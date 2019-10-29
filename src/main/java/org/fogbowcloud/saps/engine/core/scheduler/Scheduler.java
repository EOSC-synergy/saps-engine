package org.fogbowcloud.saps.engine.core.scheduler;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.fogbowcloud.saps.engine.core.database.ImageDataStore;
import org.fogbowcloud.saps.engine.core.database.JDBCImageDataStore;
import org.fogbowcloud.saps.engine.core.dto.*;
import org.fogbowcloud.saps.engine.core.exception.SapsException;
import org.fogbowcloud.saps.engine.core.job.SapsJob;
import org.fogbowcloud.saps.engine.core.model.ImageTask;
import org.fogbowcloud.saps.engine.core.model.ImageTaskState;
import org.fogbowcloud.saps.engine.core.model.SapsTask;
import org.fogbowcloud.saps.engine.core.scheduler.arrebol.Arrebol;
import org.fogbowcloud.saps.engine.core.scheduler.arrebol.DefaultArrebol;
import org.fogbowcloud.saps.engine.core.scheduler.arrebol.JobSubmitted;
import org.fogbowcloud.saps.engine.core.scheduler.arrebol.exceptions.GetJobException;
import org.fogbowcloud.saps.engine.core.scheduler.arrebol.exceptions.SubmitJobException;
import org.fogbowcloud.saps.engine.core.scheduler.retry.arrebol.ArrebolRetry;
import org.fogbowcloud.saps.engine.core.scheduler.retry.arrebol.LenQueueRetry;
import org.fogbowcloud.saps.engine.core.scheduler.retry.arrebol.SubmitJobRetry;
import org.fogbowcloud.saps.engine.core.scheduler.retry.catalog.CatalogRetry;
import org.fogbowcloud.saps.engine.core.scheduler.retry.catalog.GetTasksRetry;
import org.fogbowcloud.saps.engine.core.scheduler.retry.catalog.UpdateTaskRetry;
import org.fogbowcloud.saps.engine.core.scheduler.selector.DefaultRoundRobin;
import org.fogbowcloud.saps.engine.core.scheduler.selector.Selector;
import org.fogbowcloud.saps.engine.core.task.Specification;
import org.fogbowcloud.saps.engine.core.task.TaskImpl;
import org.fogbowcloud.saps.engine.util.ExecutionScriptTag;
import org.fogbowcloud.saps.engine.util.ExecutionScriptTagUtil;
import org.fogbowcloud.saps.engine.util.SapsPropertiesConstants;

public class Scheduler {

	// Constants
	public static final Logger LOGGER = Logger.getLogger(Scheduler.class);

	private static final int ARREBOL_SLEEP_SECONDS = 5;
	private static final int CATALOG_SLEEP_SECONDS = 5;

	// Saps Controller Variables
	private final Properties properties;
	private final ImageDataStore imageStore;
	private final ScheduledExecutorService sapsExecutor;
	private final Arrebol arrebol;
	private final Selector selector;

	// REMOVE IT
	public Scheduler() {
		this.imageStore = null;
		this.properties = null;
		this.sapsExecutor = null;
		this.arrebol = null;
		this.selector = null;
	}
	// REMOVE IT

	public Scheduler(Properties properties) throws SapsException, SQLException {
		try {
			LOGGER.debug("Imagestore " + SapsPropertiesConstants.IMAGE_DATASTORE_IP + ":"
					+ SapsPropertiesConstants.IMAGE_DATASTORE_IP);
			this.imageStore = new JDBCImageDataStore(properties);

			if (!checkProperties(properties))
				throw new SapsException("Error on validate the file. Missing properties for start Saps Controller.");
		} catch (Exception e) {
			throw new SapsException("Error while initializing Sebal Controller.", e);
		}

		this.properties = properties;
		this.sapsExecutor = Executors.newScheduledThreadPool(1);
		this.arrebol = new DefaultArrebol(properties);
		this.selector = new DefaultRoundRobin();
	}

	public Scheduler(Properties properties, ImageDataStore imageStore, ScheduledExecutorService sapsExecutor,
			Arrebol arrebol, Selector selector) throws SapsException, SQLException {
		if (!checkProperties(properties))
			throw new SapsException("Error on validate the file. Missing properties for start Saps Controller.");

		this.properties = properties;
		this.imageStore = imageStore;
		this.sapsExecutor = Executors.newScheduledThreadPool(1);
		this.arrebol = new DefaultArrebol(properties);
		this.selector = selector;
	}

	/**
	 * This function checks if the essential properties have been set.
	 * 
	 * @param properties saps properties to be check
	 * @return boolean representation, true (case all properties been set) or false
	 *         (otherwise)
	 */
	protected static boolean checkProperties(Properties properties) {
		if (!properties.containsKey(SapsPropertiesConstants.IMAGE_DATASTORE_IP)) {
			LOGGER.error("Required property " + SapsPropertiesConstants.IMAGE_DATASTORE_IP + " was not set");
			return false;
		}
		if (!properties.containsKey(SapsPropertiesConstants.IMAGE_DATASTORE_PORT)) {
			LOGGER.error("Required property " + SapsPropertiesConstants.IMAGE_DATASTORE_PORT + " was not set");
			return false;
		}
		if (!properties.containsKey(SapsPropertiesConstants.IMAGE_WORKER)) {
			LOGGER.error("Required property " + SapsPropertiesConstants.IMAGE_WORKER + " was not set");
			return false;
		}
		if (!properties.containsKey(SapsPropertiesConstants.SAPS_EXECUTION_PERIOD_SUBMISSOR)) {
			LOGGER.error(
					"Required property " + SapsPropertiesConstants.SAPS_EXECUTION_PERIOD_SUBMISSOR + " was not set");
			return false;
		}
		if (!properties.containsKey(SapsPropertiesConstants.SAPS_EXECUTION_PERIOD_CHECKER)) {
			LOGGER.error("Required property " + SapsPropertiesConstants.SAPS_EXECUTION_PERIOD_CHECKER + " was not set");
			return false;
		}
		if (!properties.containsKey(SapsPropertiesConstants.ARREBOL_BASE_URL)) {
			LOGGER.error("Required property " + SapsPropertiesConstants.ARREBOL_BASE_URL + " was not set");
			return false;
		}

		LOGGER.debug("All properties are set");
		return true;
	}

	/**
	 * Get properties
	 * 
	 * @return properties
	 */
	public Properties getProperties() {
		return properties;
	}

	/**
	 * Start Scheduler component
	 * 
	 * @throws Exception
	 */
	public void start() throws Exception {
		recovery();

		try {
			sapsExecutor.scheduleWithFixedDelay(new Runnable() {
				@Override
				public void run() {
					schedule(getCountSlotsInArrebol());
				}
			}, 0, Integer.parseInt(properties.getProperty(SapsPropertiesConstants.SAPS_EXECUTION_PERIOD_SUBMISSOR)),
					TimeUnit.SECONDS);

			sapsExecutor.scheduleWithFixedDelay(new Runnable() {
				@Override
				public void run() {
					checker();
				}
			}, 0, Integer.parseInt(properties.getProperty(SapsPropertiesConstants.SAPS_EXECUTION_PERIOD_CHECKER)),
					TimeUnit.SECONDS);

		} catch (Exception e) {
			LOGGER.error("Error while starting Scheduler component", e);
		}
	}

	// TODO implementation and documentation this method
	private void recovery() {

	}

	/**
	 * This function schedules up to count tasks.
	 * 
	 * @param count slots number in Arrebol queue
	 * @return number of scheduled tasks
	 */
	protected int schedule(int count) {
		int remaining;

		remaining = schedule(count, ImageTaskState.READY);
		remaining = schedule(remaining, ImageTaskState.DOWNLOADED);
		remaining = schedule(remaining, ImageTaskState.CREATED);

		return remaining;
	}

	/**
	 * This function schedules up to count tasks in specific state.
	 * 
	 * @param count slots number in Arrebol queue
	 * @param state tasks state for schedule
	 * @return number of scheduled tasks in specific state
	 */
	private int schedule(int count, final ImageTaskState state) {
		// shortcut. it was solved in the previous schedule
		if (count <= 0)
			return count;

		List<ImageTask> tasks = getTasksInCatalog(state);

		Map<String, List<ImageTask>> tasksByUsers = mapUsers2Tasks(tasks);

		LOGGER.info("Tasks by users: " + tasksByUsers);

		List<ImageTask> selectedTasks = selector.select(count, tasksByUsers);

		LOGGER.info("Selected tasks using " + selector.version() + ": " + selectedTasks);

		ImageTaskState nextState = getNextState(state);

		for (ImageTask task : selectedTasks) {
			updateStateInCatalog(task, nextState);
			String arrebolJobId = submitTaskToArrebol(task, nextState);
			writeArrebolJobIdInCatalog(task, arrebolJobId);
		}

		return count - selectedTasks.size();
	}

	/**
	 * This function updates task with Arrebol job ID in catalog component.
	 *
	 * @param task         task to be updated
	 * @param arrebolJobId Arrebol job ID of task submitted
	 * @return boolean representation reporting success (true) or failure (false) in
	 *         update state task in catalog
	 */
	private void writeArrebolJobIdInCatalog(ImageTask task, String arrebolJobId) {
		task.setArrebolJobId(arrebolJobId);
		retry(new UpdateTaskRetry(imageStore, task), CATALOG_SLEEP_SECONDS,
				"updates task[" + task.getTaskId() + "] with Arrebol job ID");
	}

	/**
	 * This function updates task state in catalog component.
	 *
	 * @param task  task to be updated
	 * @param state new task state
	 * @return boolean representation reporting success (true) or failure (false) in
	 *         update state task in catalog
	 */
	private boolean updateStateInCatalog(ImageTask task, ImageTaskState state) {
		task.setState(state);
		return retry(new UpdateTaskRetry(imageStore, task), CATALOG_SLEEP_SECONDS,
				"updates task[" + task.getTaskId() + "] state for " + state.getValue());
	}

	/**
	 * This function gets Arrebol capacity for add new jobs
	 * 
	 * @return Arrebol capacity
	 */
	private int getCountSlotsInArrebol() {
		return retry(new LenQueueRetry(arrebol), ARREBOL_SLEEP_SECONDS, "gets Arrebol capacity len for add news jobs");
	}

	/**
	 * This function gets tasks in specific state in Catalog
	 * 
	 * @param state specific state for get tasks
	 * @return tasks in specific state
	 */
	private List<ImageTask> getTasksInCatalog(ImageTaskState state) {
		return retry(new GetTasksRetry(imageStore, state, ImageDataStore.UNLIMITED), CATALOG_SLEEP_SECONDS,
				"gets tasks with " + state.getValue() + " state");
	}

	/**
	 * This function tries countless times to successfully execute the passed
	 * function.
	 * 
	 * @param <T>            Return type
	 * @param function       Function passed for execute
	 * @param sleepInSeconds Time sleep in seconds (case fail)
	 * @param message        Information message about function passed
	 * @return Function return
	 */
	@SuppressWarnings("unchecked")
	private <T> T retry(ArrebolRetry<?> function, int sleepInSeconds, String message) {
		LOGGER.info("Trying " + message + " using " + sleepInSeconds + " seconds with time sleep");

		while (true) {
			try {
				return (T) function.run();
			} catch (Exception | SubmitJobException e) {
				LOGGER.error(message);
				e.printStackTrace();
			}

			try {
				Thread.sleep(Long.valueOf(sleepInSeconds));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * This function tries countless times to successfully execute the passed
	 * function.
	 * 
	 * @param <T>            Return type
	 * @param function       Function passed for execute
	 * @param sleepInSeconds Time sleep in seconds (case fail)
	 * @param message        Information message about function passed
	 * @return Function return
	 */
	@SuppressWarnings("unchecked")
	private <T> T retry(CatalogRetry<?> function, int sleepInSeconds, String message) {
		LOGGER.info("Trying " + message + " using " + sleepInSeconds + " seconds with time sleep");

		while (true) {
			try {
				return (T) function.run();
			} catch (SQLException e) {
				LOGGER.error("Failed while " + message);
				e.printStackTrace();
			}

			try {
				LOGGER.info("Sleeping for " + sleepInSeconds + " seconds");
				Thread.sleep(Long.valueOf(sleepInSeconds));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * This function get next state based in current state.
	 * 
	 * @param currentState current state
	 * @return next state
	 */
	private ImageTaskState getNextState(ImageTaskState currentState) {
		if (currentState == ImageTaskState.READY)
			return ImageTaskState.RUNNING;
		else if (currentState == ImageTaskState.DOWNLOADED)
			return ImageTaskState.PREPROCESSING;
		else
			return ImageTaskState.DOWNLOADING;
	}

	/***
	 * This function associate each task with a specific user by building an map.
	 * After that, it sorts each task list by task priority.
	 * 
	 * @param tasks list with tasks
	 * @return user map by tasks
	 */
	protected Map<String, List<ImageTask>> mapUsers2Tasks(List<ImageTask> tasks) {
		Map<String, List<ImageTask>> mapUsersToTasks = new TreeMap<String, List<ImageTask>>();

		for (ImageTask task : tasks) {
			String user = task.getUser();
			if (!mapUsersToTasks.containsKey(user))
				mapUsersToTasks.put(user, new ArrayList<ImageTask>());

			mapUsersToTasks.get(user).add(task);
		}

		for (String user : mapUsersToTasks.keySet()) {
			mapUsersToTasks.get(user).sort(new Comparator<ImageTask>() {
				@Override
				public int compare(ImageTask task01, ImageTask task02) {
					return task02.getPriority() - task01.getPriority();
				}
			});
		}

		return mapUsersToTasks;
	}

	/**
	 * This function try submit task to Arrebol.
	 * 
	 * @param task task to be submitted
	 * @return Arrebol job id
	 * @throws Exception
	 */
	private String submitTaskToArrebol(ImageTask task, ImageTaskState state) {

		LOGGER.info("Trying submit task id " + task.getTaskId() + " in state " + task.getState().getValue()
				+ " to arrebol");

		String federationMember = task.getFederationMember();
		String repository = getRepository(state);
		ExecutionScriptTag imageDockerInfo = null;

		try {
			imageDockerInfo = ExecutionScriptTagUtil.getExecutionScritpTag(task.getAlgorithmExecutionTag(), repository);
		} catch (SapsException e) {
			LOGGER.error("Error while trying get tag and repository Docker.", e);
			return null;
		}

		Specification workerSpec = new Specification(imageDockerInfo.formatImageDocker(),
				new HashMap<String, String>());

		TaskImpl taskToJob = new TaskImpl(task.getTaskId(), workerSpec, UUID.randomUUID().toString());

		LOGGER.info("Creating saps task");
		SapsTask.createTask(taskToJob, task);

		SapsJob imageJob = new SapsJob(UUID.randomUUID().toString(), federationMember, task.getTaskId());
		imageJob.addTask(taskToJob);

		String jobId = retry(new SubmitJobRetry(arrebol, imageJob), ARREBOL_SLEEP_SECONDS, "add new job");
		LOGGER.debug("Result submited job: " + jobId);

		arrebol.addJobInList(new JobSubmitted(jobId, task));
		LOGGER.info("Adding job in list");

		return jobId;
	}

	/**
	 * This function get key (repository representation in execution_script_tags
	 * file) based in state parameter.
	 * 
	 * @param state task state to be submitted
	 * @return key (repository representation)
	 */
	private String getRepository(ImageTaskState state) {
		if (state == ImageTaskState.RUNNING)
			return ExecutionScriptTagUtil.WORKER;
		else if (state == ImageTaskState.PREPROCESSING)
			return ExecutionScriptTagUtil.PRE_PROCESSING;
		else
			return ExecutionScriptTagUtil.INPUT_DOWNLOADER;
	}

	/**
	 * This function checks if each submitted job was finished. If exists finished
	 * jobs, for each job is updates state in Catalog and removes a job by list of
	 * submitted jobs to Arrebol.
	 * 
	 */
	private void checker() {

		ImageTask imageTask = null;
		List<JobSubmitted> submittedJobs = arrebol.returnAllJobsSubmitted();
		List<JobSubmitted> finishedJobs = new LinkedList<JobSubmitted>();

		LOGGER.debug("Checking jobs " + submittedJobs.size() + " submitted for arrebol service");

		try {
			for (JobSubmitted job : submittedJobs) {
				String jobId = job.getJobId();

				JobResponseDTO jobResponse = arrebol.checkStatusJob(jobId);

				imageTask = job.getImageTask();

				LOGGER.debug("Checking job " + jobId + " ...");

				boolean checkFinish = checkJobWasFinish(jobResponse);

				if (checkFinish) {

					boolean checkOK = checkJobFinishedWithSucess(jobResponse);

					if (checkOK) {
						LOGGER.debug("Job " + jobId + " finished");
						imageTask.setState(ImageTaskState.FINISHED);
						imageStore.updateTaskState(imageTask.getTaskId(), ImageTaskState.FINISHED);
					} else {
						LOGGER.debug("Job " + jobId + " failed");
						imageTask.setState(ImageTaskState.FAILED);
						imageStore.updateTaskState(imageTask.getTaskId(), ImageTaskState.FAILED);
					}

					finishedJobs.add(job);

					try {
						imageTask.setUpdateTime(imageStore.getTask(imageTask.getTaskId()).getUpdateTime());
					} catch (SQLException e) {
						LOGGER.error("Error while update time in task.", e);
					}

					try {
						imageStore.addStateStamp(imageTask.getTaskId(), imageTask.getState(),
								imageTask.getUpdateTime());
					} catch (SQLException e) {
						LOGGER.error("Error while adding state " + imageTask.getState() + " timestamp "
								+ imageTask.getUpdateTime() + " in Catalogue", e);
					}
				}
			}

			for (JobSubmitted jobFinished : finishedJobs) {
				LOGGER.info("Removing job " + jobFinished.getJobId() + " from the list");
				arrebol.removeJob(jobFinished);
			}
		} catch (GetJobException e) {
			LOGGER.error("Error while trying check status jobs submitted.", e);
		} catch (SQLException e) {
			LOGGER.error("Error while trying update image state.", e);
		}

	}

	/**
	 * This function checks job state was finished.
	 * 
	 * @param jobResponse job to be check
	 * @return boolean representation, true (is was finished) or false (otherwise)
	 */
	private boolean checkJobWasFinish(JobResponseDTO jobResponse) {

		for (TaskResponseDTO task : jobResponse.getTasks()) {
			LOGGER.debug("State task: " + task.getState());

			// TODO Verify values possible for job states
			if (task.getState().compareTo("FINISHED") != 0)
				return false;
		}

		return true;
	}

	/**
	 * This function checks a finished job was completed with success or failure.
	 * 
	 * @param jobResponse job to be check
	 * @return boolean representation, true (success completed) or false (otherwise)
	 */
	private boolean checkJobFinishedWithSucess(JobResponseDTO jobResponse) {

		for (TaskResponseDTO task : jobResponse.getTasks()) {
			TaskSpecResponseDTO taskSpec = task.getTaskSpec();

			for (CommandResponseDTO command : taskSpec.getCommands()) {

				String commandDesc = command.getCommand();
				String commandState = command.getState();
				Integer commandExitCode = command.getExitCode();

				LOGGER.info("Command: " + commandDesc);
				LOGGER.info("State:" + commandState);
				LOGGER.info("Exit code: " + commandExitCode);

				if (commandExitCode != 0)
					return false;
			}
		}

		return true;
	}
}
