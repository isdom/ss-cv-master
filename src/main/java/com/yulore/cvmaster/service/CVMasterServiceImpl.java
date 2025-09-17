package com.yulore.cvmaster.service;

import com.yulore.api.CVMasterService;
import com.yulore.api.CosyVoiceService;
import com.yulore.api.ZeroShotTask;
import com.yulore.cvmaster.vo.*;
import com.yulore.util.ExceptionUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.api.RemoteInvocationOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class CVMasterServiceImpl implements CVMasterService, CVTaskService {
    private static final long AGENT_UPDATE_TIMEOUT_IN_MS = 1000 * 30; // 30s

    @Autowired
    public CVMasterServiceImpl(@Value("${service.cosyvoice}") final String serviceName,
                               final RedissonClient redisson) {
        cosyVoiceService = redisson.getRemoteService(serviceName)
                .get(CosyVoiceService.class, RemoteInvocationOptions.defaults()
                        .expectAckWithin(1, TimeUnit.SECONDS)
                        .noResult());

        checkAndExecuteTasks();
    }

    @Override
    public void updateCVAgentStatus(final String agentId,
                                    final int totalWorks,
                                    final int freeWorks,
                                    final long timestamp) {
        final var now = System.currentTimeMillis();
        if (now - timestamp > AGENT_UPDATE_TIMEOUT_IN_MS) {
            // out of date update, ignore
            return;
        }
        log.info("updateCVAgentStatus: agent[{}] - freeWorks: {}", agentId, freeWorks);
        agentMemos.put(agentId, new AgentMemo(agentId, totalWorks, freeWorks, now));
    }

    @Override
    public void feedbackZeroShotStatus(final String agentId, final String task_id, final int status) {
        log.info("feedbackZeroShotStatus: agent[{}] - task_id: {}: status:{}", agentId, task_id, status);
        final var memo = zeroShotMemos.get(task_id);
        if (memo == null) {
            // memo is null
            log.warn("task: {} feedback_by_agentId({}) has_no_memo, , ignore feedback_status[{}]", task_id, agentId, status);
            return;
        }
        if (memo.agentId != null && !agentId.equals(memo.agentId)) {
            // agentId mismatch
            log.warn("task: {} feedback_by_agentId({}) mismatch commit_by_agentId({}), ignore feedback_status[{}]",
                    task_id, agentId, memo.agentId, status);
            return;
        }
        switch (status) {
            case -1: {
                log.info("task: {} feedback_by_agentId({}) failed, schedule_to_retry", task_id, agentId);
                // set task's status => 0, to re-try
                memo.status = 0;
                memo.agentId = null;
                memo.lastFeedbackInMs = -1;
            }
            break;
            case 0: {
                zeroShotMemos.remove(task_id);
                completedTasks.put(task_id, memo.task);
                final var costInMs = System.currentTimeMillis() - memo.beginInMs;
                final var totalCostInMs = totalTaskCostInMs.addAndGet(costInMs);
                final var totalTextLen = totalTaskTextLen.addAndGet(memo.task.tts_text.length());
                log.info("task: {} complete, cost: {} s, avg_task_complete_speed: {} ch/s",
                        task_id, costInMs / 1000.0f, totalTextLen / (totalCostInMs / 1000.0f));
                if (memo.completableFuture != null) {
                    memo.completableFuture.complete(memo.task);
                }
            }
            break;
            case 1: {
                // task progress
                if (memo.agentId == null) {
                    memo.agentId = agentId;
                }
                memo.lastFeedbackInMs = System.currentTimeMillis();
                log.info("task: {} progress_for {} s", task_id, (memo.lastFeedbackInMs - memo.beginInMs) / 1000.0f);
            }
            break;
        }
    }

    @Override
    public void commitZeroShotTask(final ZeroShotTask task, final CompletableFuture<ZeroShotTask> cf) {
        if (null != zeroShotMemos.putIfAbsent(task.task_id,
                    ZeroShotMemo.builder()
                            .task(task)
                            .status(0)
                            .completableFuture(cf)
                            .agentId(null)
                            .lastFeedbackInMs(-1)
                            .build()) ) {
                log.warn("commitZeroShotTasks: task_id:{} has_committed_already, ignore", task.task_id);
                if (cf != null) {
                    cf.completeExceptionally(new RuntimeException("task_id:{} has_committed_already"));
                }
        }
    }

    @Override
    public WorkerStatus queryWorkerStatus() {
        return WorkerStatus.builder().total_workers(totalWorks()).free_workers(totalFreeWorks()).build();
    }

    @Override
    public TaskStatus[] queryTaskStatus(final String[] ids) {
        final List<TaskStatus> statues = new ArrayList<>();
        for (String taskId : ids) {
            final ZeroShotMemo memo = zeroShotMemos.get(taskId);
            if (null == memo) {
                // not found
                final ZeroShotTask task = completedTasks.get(taskId);
                if (null != task) {
                    statues.add(TaskStatus.builder()
                            .task_id(taskId)
                            .status("done")
                            .build());
                } else {
                    statues.add(TaskStatus.builder()
                            .task_id(taskId)
                            .status("not_found")
                            .build());
                }
            } else {
                statues.add(TaskStatus.builder().task_id(taskId)
                        .status(memo.status == 0 ? "pending" : "progress")
                        .build());
            }
        }
        return statues.toArray(new TaskStatus[0]);
    }

    @Override
    public TaskStatus[] queryAllTaskStatus() {
        final List<TaskStatus> statues = new ArrayList<>();
        for (ZeroShotMemo memo : zeroShotMemos.values()) {
            statues.add(TaskStatus.builder().task_id(memo.task.task_id)
                    .status(memo.status == 0 ? "pending" : "progress")
                    .build());
        }
        for (ZeroShotTask task : completedTasks.values()) {
            statues.add(TaskStatus.builder().task_id(task.task_id)
                    .status("done")
                    .build());
        }
        return statues.toArray(new TaskStatus[0]);
    }

    @Override
    public AgentMemo[] queryAllAgentStatus() {
        return agentMemos.values().toArray(new AgentMemo[0]);
    }

    @Override
    public TaskSummary queryTaskSummary() {
        return TaskSummary.builder()
                .pending((int) countOfStatus(0))
                .progress((int)countOfStatus(1))
                .done(completedTasks.size())
                .build();
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();

        log.info("CVMasterServiceImpl: shutdown");
    }

    private void checkAndExecuteTasks() {
        updateAgents();
        try {
            final var pendingTasks = pendingTasks();
            if (pendingTasks > 0) {
                if (totalFreeWorks() > 0) {
                    for (final ZeroShotMemo memo : zeroShotMemos.values()) {
                        if (0 == memo.status) {
                            try {
                            /*
                            final RFuture<String> future = cosyVoiceService.inferenceZeroShotAndSave(
                                    memo.task.tts_text,
                                    memo.task.prompt_text,
                                    memo.task.prompt_wav,
                                    memo.task.bucket,
                                    memo.task.save_to
                                    );
                            future.whenComplete((resp, ex) -> {
                                if (resp != null) {
                                    zeroShotMemos.remove(memo.task.task_id);
                                    completedTasks.put(memo.task.task_id, memo.task);
                                    log.info("task: {} complete_with: {}, cost: {} s",
                                            memo.task.task_id, resp, (System.currentTimeMillis() - now) / 1000.0f);
                                    if (memo.completableFuture != null) {
                                        memo.completableFuture.complete(memo.task);
                                    }
                                    // memo.status = 2;
                                    // memo.resp = resp;
                                }
                                if (ex != null) {
                                    log.info("task: {} failed_with: {}, schedule_to_retry",
                                            memo.task.task_id, ExceptionUtil.exception2detail(ex));
                                    // set status => 0, to re-try
                                    memo.status = 0;
                                }
                            });
                            */
                                log.info("try commitZeroShotTask: {}", memo.task);
                                final var start = System.currentTimeMillis();
                                cosyVoiceService.commitZeroShotTask(memo.task);
                                // memo.agentId =
                                memo.status = 1; // progress
                                memo.beginInMs = System.currentTimeMillis();
                                memo.lastFeedbackInMs = memo.beginInMs;
                                log.info("commitZeroShotTask_success: {}, cost: {} s", memo.task, (memo.beginInMs - start) / 1000.0f);
                            } catch (Exception ex) {
                                log.warn("commitZeroShotTask_failed: {} with {}", memo.task, ExceptionUtil.exception2detail(ex));
                            }
                            break;
                        }
                    }
                } else {
                    //log.debug("no more free workers for pending tasks: {}", pendingTasks);
                }
            }
            final var now = System.currentTimeMillis();
            for (final ZeroShotMemo memo : zeroShotMemos.values()) {
                if (1 == memo.status) {
                    // progress
                    if (now - memo.lastFeedbackInMs > 60 * 1000L) {
                        // not feedback more than 60 seconds
                        log.info("task: {} commit_by_agentId({}) NOT feedback {} s, schedule_to_retry",
                                memo.task.task_id, memo.agentId, (now - memo.lastFeedbackInMs) / 1000.0f);
                        // set task's status => 0, to re-try
                        memo.status = 0;
                        memo.agentId = null;
                        memo.lastFeedbackInMs = -1;
                    }
                }
            }
        } finally {
            scheduler.schedule(this::checkAndExecuteTasks, _task_check_interval, TimeUnit.MILLISECONDS);
        }
    }

    private void updateAgents() {
        final long now = System.currentTimeMillis();
        if (now - last_agent_check_timestamp > _agent_check_interval) {
            last_agent_check_timestamp = now;
            for (AgentMemo memo : agentMemos.values()) {
                if (now - memo.updateTimestamp() >= AGENT_UPDATE_TIMEOUT_IN_MS) {
                    if (agentMemos.remove(memo.id()) != null) {
                        log.warn("updateAgents: remove_update_timeout agent: {}", memo);
                    }
                }
            }
        }
    }

    private int totalWorks() {
        int totalWorks = 0;
        for (AgentMemo memo : agentMemos.values()) {
            totalWorks += memo.totalWorks();
        }
        return totalWorks;
    }

    private int totalFreeWorks() {
        int freeWorks = 0;
        for (AgentMemo memo : agentMemos.values()) {
            freeWorks += memo.freeWorks();
        }
        return freeWorks;
    }

    private int pendingTasks() {
        int pendingTasks = 0;
        for (ZeroShotMemo memo : zeroShotMemos.values()) {
            pendingTasks += memo.status == 0 ? 1 : 0;
        }
        return pendingTasks;
    }

    private final CosyVoiceService cosyVoiceService;

    @Builder
    @Data
    @ToString
    static public class ZeroShotMemo {
        private ZeroShotTask task;
        private CompletableFuture<ZeroShotTask> completableFuture;
        private long beginInMs;
        //  0: to_start
        //  1: progress
        private int status;
        private String agentId;
        private long lastFeedbackInMs;
        private String resp;
    }

    @Value("${task.check_interval:100}") // default: 100ms
    private long _task_check_interval;

    @Value("${agent.check_interval:10000}") // default: 1000ms
    private long _agent_check_interval;

    private long last_agent_check_timestamp = 0;

    private final AtomicLong totalTaskCostInMs = new AtomicLong(0);
    private final AtomicLong totalTaskTextLen = new AtomicLong(0);
    private final ConcurrentMap<String, AgentMemo> agentMemos = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ZeroShotMemo> zeroShotMemos = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ZeroShotTask> completedTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1, new DefaultThreadFactory("cvTaskExecutor"));

    private long countOfStatus(final int status) {
        return zeroShotMemos.values().stream()
                .filter(memo -> memo.status == status)
                .count();
    }
}
