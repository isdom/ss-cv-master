package com.yulore.cvmaster.service;

import com.yulore.cvmaster.vo.*;

public interface CVTaskService {
    void commitZeroShotTasks(final CommitZeroShotTasksRequest request);
    WorkerStatus queryWorkerStatus();
    TaskStatusResult queryTaskStatus(final String[] taskId);
}
