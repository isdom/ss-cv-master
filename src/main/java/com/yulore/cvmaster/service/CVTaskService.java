package com.yulore.cvmaster.service;

import com.yulore.cvmaster.vo.CommitZeroShotTasksRequest;
import com.yulore.cvmaster.vo.CommitZeroShotTasksResponse;
import com.yulore.cvmaster.vo.QueryTaskStatusResponse;
import com.yulore.cvmaster.vo.QueryWorkerStatusResponse;

public interface CVTaskService {
    CommitZeroShotTasksResponse commitZeroShotTasks(final CommitZeroShotTasksRequest request);
    QueryWorkerStatusResponse queryWorkerStatus();
    QueryTaskStatusResponse queryTaskStatus(final String taskId);
}
