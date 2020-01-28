package org.fogbowcloud.saps.engine.utils.retry.catalog;

import java.sql.SQLException;

import org.fogbowcloud.saps.engine.core.database.Catalog;
import org.fogbowcloud.saps.engine.core.model.SapsImage;

public class GetTaskById implements CatalogRetry<SapsImage> {

	private Catalog imageStore;
	private String taskId;

	public GetTaskById(Catalog imageStore, String taskId) {
		this.imageStore = imageStore;
		this.taskId = taskId;
	}

	@Override
	public SapsImage run() throws SQLException {
		return imageStore.getTask(taskId);
	}

}
