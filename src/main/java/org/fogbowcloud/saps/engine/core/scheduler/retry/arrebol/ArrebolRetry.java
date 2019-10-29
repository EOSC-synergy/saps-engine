package org.fogbowcloud.saps.engine.core.scheduler.retry.arrebol;

import org.fogbowcloud.saps.engine.core.scheduler.arrebol.exceptions.SubmitJobException;

public interface ArrebolRetry<T>{

	public T run() throws Exception, SubmitJobException;

}
