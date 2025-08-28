package com.yulore.cvmaster.service;

import com.yulore.cvmaster.vo.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface CVTaskService {
    void commitZeroShotTask(final ZeroShotTask task, final CompletableFuture<ZeroShotTask> cf);
    WorkerStatus queryWorkerStatus();
    TaskStatus[] queryTaskStatus(final String[] taskId);
    TaskStatus[] queryAllTaskStatus();
    AgentMemo[] queryAllAgentStatus();
    TaskSummary queryTaskSummary();
}
