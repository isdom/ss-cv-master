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
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.api.RemoteInvocationOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Service
public class CVMasterServiceImpl implements CVMasterService, CVTaskService {
    private static final long AGENT_UPDATE_TIMEOUT_IN_MS = 1000 * 30; // 30s

    @Autowired
    public CVMasterServiceImpl(@Value("${service.cosyvoice}") final String serviceName,
                               final RedissonClient redisson) {
        cosyVoiceService = redisson.getRemoteService(serviceName)
                .get(/*CosyVoiceServiceAsync.class*/CosyVoiceService.class, RemoteInvocationOptions.defaults()
                        .noAck()
                        .expectResultWithin(10 * 1000L));

        checkAndExecuteTasks();
    }

    @Override
    public void updateCVAgentStatus(final String agentId, final int freeWorks) {
        log.info("updateCVAgentStatus: agent[{}] - freeWorks: {}", agentId, freeWorks);
        agentMemos.put(agentId, new AgentMemo(agentId, freeWorks, System.currentTimeMillis()));
    }

    @Override
    public void feedbackZeroShotStatus(final String agentId, final String task_id, final int status) {
        log.info("feedbackZeroShotStatus: agent[{}] - task_id: {}: status:{}", agentId, task_id, status);
        if (status  == -1) {
            final var memo = zeroShotMemos.get(task_id);
            if (memo != null) {
                log.info("task: {} failed, schedule_to_retry", task_id);
                // set status => 0, to re-try
                memo.status = 0;
            }
        } else if (status == 0) {
            final var memo = zeroShotMemos.remove(task_id);
            completedTasks.put(task_id, memo.task);
            log.info("task: {} complete, cost: {} s", task_id, (System.currentTimeMillis() - memo.beginInMs) / 1000.0f);
            if (memo.completableFuture != null) {
                memo.completableFuture.complete(memo.task);
            }
        } else if (status == 1) {
            log.info("task: {} pending", task_id);
        }
    }

    @Override
    public void commitZeroShotTask(final ZeroShotTask task, final CompletableFuture<ZeroShotTask> cf) {
        if (null != zeroShotMemos.putIfAbsent(task.task_id,
                    ZeroShotMemo.builder()
                            .task(task)
                            .status(0)
                            .completableFuture(cf)
                            .build()) ) {
                log.warn("commitZeroShotTasks: task_id:{} has_committed_already, ignore", task.task_id);
                if (cf != null) {
                    cf.completeExceptionally(new RuntimeException("task_id:{} has_committed_already"));
                }
        }
    }

    @Override
    public WorkerStatus queryWorkerStatus() {
        return WorkerStatus.builder().total_workers(agentMemos.size()).free_workers(totalFreeWorks()).build();
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
                        .status("pending")
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
                    .status("pending")
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
        return TaskSummary.builder().pending(zeroShotMemos.size()).done(completedTasks.size()).build();
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();

        log.info("CVMasterServiceImpl: shutdown");
    }

    private void checkAndExecuteTasks() {
        updateAgents();
        try {
            final int pendingTasks = pendingTasks();
            if (pendingTasks > 0) {
                if (totalFreeWorks() > 0) {
                    for (final ZeroShotMemo memo : zeroShotMemos.values()) {
                        if (0 == memo.status) {
                            memo.status = 1; // executing
                            log.info("execute_zeroshot_task: {}", memo.task);
                            memo.beginInMs = System.currentTimeMillis();
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
                            final var agentId = cosyVoiceService.commitZeroShotTask(memo.task);
                            log.info("async_execute_zeroshot_task: {} by agent:{} ok", memo.task, agentId);
                            break;
                        }
                    }
                } else {
                    //log.debug("no more free workers for pending tasks: {}", pendingTasks);
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

    private final /*CosyVoiceServiceAsync*/ CosyVoiceService cosyVoiceService;

    @Builder
    @Data
    @ToString
    static public class ZeroShotMemo {
        private ZeroShotTask task;
        private CompletableFuture<ZeroShotTask> completableFuture;
        private long beginInMs;
        // 0: todo  1: executing 2: complete 3: failed
        private int status;
        private String resp;
    }

    @Value("${task.check_interval:100}") // default: 100ms
    private long _task_check_interval;

    @Value("${agent.check_interval:10000}") // default: 1000ms
    private long _agent_check_interval;

    private long last_agent_check_timestamp = 0;

    private final ConcurrentMap<String, AgentMemo> agentMemos = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ZeroShotMemo> zeroShotMemos = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ZeroShotTask> completedTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1, new DefaultThreadFactory("cvTaskExecutor"));
}
