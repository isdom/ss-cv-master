package com.yulore.cvmaster.service;

import com.yulore.cvmaster.vo.*;

public interface CVTaskService {
    CommitZeroShotTasksResponse commitZeroShotTasks(final CommitZeroShotTasksRequest request);
    QueryWorkerStatusResponse queryWorkerStatus();
    TaskStatus[] queryTaskStatus(final String[] taskId);
}
