package com.yulore.cvmaster.service;

import com.yulore.api.CVMasterService;
import com.yulore.api.CosyVoiceService;
import com.yulore.cvmaster.vo.CommitZeroShotTasksRequest;
import com.yulore.cvmaster.vo.CommitZeroShotTasksResponse;
import com.yulore.cvmaster.vo.ZeroShotTask;
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
import java.util.concurrent.*;

@Slf4j
@Service
public class CVMasterServiceImpl implements CVMasterService, CVTaskService {
    @Autowired
    public CVMasterServiceImpl(@Value("${service.cosyvoice}") final String serviceName,
                               final RedissonClient redisson) {
        cosyVoiceService = redisson.getRemoteService(serviceName)
                .get(CosyVoiceServiceAsync.class, RemoteInvocationOptions.defaults()
                        .noAck()
                        .expectResultWithin(300 * 1000L));

        checkAndExecuteTasks();
    }

    @Override
    public void updateCVAgentStatus(final String agentId, final int freeWorks) {
        log.info("updateCVAgentStatus: agent[{}] - freeWorks: {}", agentId, freeWorks);
        agentMemos.put(agentId, new AgentMemo(agentId, freeWorks, System.currentTimeMillis()));
    }

    @Override
    public CommitZeroShotTasksResponse CommitZeroShotTasks(final CommitZeroShotTasksRequest request) {
        for (ZeroShotTask task : request.tasks) {
            if ( null != zeroShotMemos.putIfAbsent(task.task_id,
                    ZeroShotMemo.builder().task(task).status(0).build()) ) {
                log.warn("CommitZeroShotTasks: task_id:{} has_committed_already, ignore", task.task_id);
            }
        }
        return CommitZeroShotTasksResponse.builder().free_workers(0).build();
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();

        log.info("CVMasterServiceImpl: shutdown");
    }

    private void checkAndExecuteTasks() {
        try {
            if (!zeroShotMemos.isEmpty()) {
                log.info("begin to execute zero shot tasks");
                if (totalFreeWorks() > 0) {
                    for (final ZeroShotMemo memo : zeroShotMemos.values()) {
                        if (0 == memo.status) {
                            log.info("execute zero shot task: {}", memo.task);
                            final String taskId = memo.task.task_id;
                            final RFuture<String> future = cosyVoiceService.inferenceZeroShotAndSave(
                                    memo.task.tts_text,
                                    memo.task.prompt_text,
                                    memo.task.prompt_wav,
                                    memo.task.bucket,
                                    memo.task.save_to
                            );
                            future.whenComplete((resp, ex) -> {
                                if (resp != null) {
                                    log.info("task: {} complete with: {}", taskId, resp);
                                    memo.status = 1;
                                    memo.resp = resp;
                                }
                                if (ex != null) {
                                    log.info("task: {} failed with: {}", taskId, ExceptionUtil.exception2detail(ex));
                                    memo.status = 3;
                                }
                            });
                            log.info("async execute zero shot task: {} ok", memo.task);
                            break;
                        }
                    }
                }
            }
        } finally {
            scheduler.schedule(this::checkAndExecuteTasks, _task_check_interval, TimeUnit.MILLISECONDS);
        }
    }

    private int totalFreeWorks() {
        int freeWorks = 0;
        for (AgentMemo memo : agentMemos.values()) {
            freeWorks += memo.freeWorks;
        }
        return freeWorks;
    }

    private final CosyVoiceServiceAsync cosyVoiceService;

    record AgentMemo(String id, int freeWorks, long updateTimestamp) {
    }

    @Builder
    @Data
    @ToString
    static public class ZeroShotMemo {
        private ZeroShotTask task;
        // 0: todo  1: executing 2: complete 3: failed
        private int status;
        private String resp;
    }

    @Value("${task.check_interval:1000}") // default: 1000ms
    private long _task_check_interval;

    private final ConcurrentMap<String, AgentMemo> agentMemos = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ZeroShotMemo> zeroShotMemos = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new DefaultThreadFactory("cvTaskExecutor"));
}
