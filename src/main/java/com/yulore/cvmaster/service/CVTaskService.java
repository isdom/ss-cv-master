package com.yulore.cvmaster.service;

import com.yulore.cvmaster.vo.*;

public interface CVTaskService {
    void commitZeroShotTasks(final CommitZeroShotTasksRequest request);
    WorkerStatus queryWorkerStatus();
    TaskStatus[] queryTaskStatus(final String[] taskId);
    TaskStatus[] queryAllTaskStatus();
    AgentMemo[] queryAllAgentStatus();
}
