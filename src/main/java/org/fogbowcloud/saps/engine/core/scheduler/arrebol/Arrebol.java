package org.fogbowcloud.saps.engine.core.scheduler.arrebol;

import java.util.List;
import org.fogbowcloud.saps.engine.core.dto.JobResponseDTO;
import org.fogbowcloud.saps.engine.core.job.SapsJob;
import org.fogbowcloud.saps.engine.core.scheduler.arrebol.exceptions.GetJobException;
import org.fogbowcloud.saps.engine.core.scheduler.arrebol.exceptions.SubmitJobException;

public interface Arrebol {
	
	public String addJob(SapsJob job) throws Exception, SubmitJobException;

	public void removeJob(JobSubmitted job);
	
	public void addJobInList(JobSubmitted newJob);

	public List<JobSubmitted> returnAllJobsSubmitted();

	public JobResponseDTO checkStatusJob(String jobId) throws GetJobException;

	public String checkStatusJobString(String jobId) throws GetJobException;
	
	public int getCountSlotsInQueue() throws Exception, SubmitJobException;
}
