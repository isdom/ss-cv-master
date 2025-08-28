package com.yulore.cvmaster.service;

import com.yulore.cvmaster.vo.*;

import java.util.concurrent.CompletionStage;

public interface CVTaskService {
    CompletionStage<ZeroShotTask> commitZeroShotTask(final ZeroShotTask task);
    WorkerStatus queryWorkerStatus();
    TaskStatus[] queryTaskStatus(final String[] taskId);
    TaskStatus[] queryAllTaskStatus();
    AgentMemo[] queryAllAgentStatus();
    TaskSummary queryTaskSummary();
}
