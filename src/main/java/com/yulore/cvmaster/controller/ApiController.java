package com.yulore.cvmaster.controller;

import com.yulore.cvmaster.service.CVMasterServiceImpl;
import com.yulore.cvmaster.service.CVTaskService;
import com.yulore.cvmaster.vo.*;
import com.yulore.util.ExceptionUtil;
import com.yulore.api.ScriptApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@Slf4j
@RequestMapping("/cv")
public class ApiController {
    @Value("${repo.bucket}")
    private String _repo_bucket;

    @Value("${repo.prefix}")
    private String _repo_prefix;

    @Autowired
    private ScriptApi _scriptApi;

    @RequestMapping(value = "/prepare_session", method = RequestMethod.POST)
    @ResponseBody
    public ApiResponse<Void> prepareSession(@RequestBody final PrepareSessionRequest request) {
        log.info("prepare_session: {}", request);

        ApiResponse<Void> resp = null;
        try {
            CompletionStage<Map<String, String>> allTaskResult = CompletableFuture.completedStage(new ConcurrentHashMap<>());
            if (request.getTtsTextList() != null && !request.getTtsTextList().isEmpty()) {
                for (Map.Entry<String, String> entry : request.getTtsTextList().entrySet()) {
                    final var task_id = UUID.randomUUID().toString().replaceAll("-", "");
                    final var key = entry.getKey();
                    final var text = entry.getValue();
                    final var saveTo = _repo_prefix + Integer.toString(request.getBotId()) + "/" + request.getSessionId() + "/" + task_id + ".wav";
                    final var task = ZeroShotTask.builder()
                            .task_id(task_id)
                            .prompt_text(request.getPromptText())
                            .prompt_wav(request.getPromptWav())
                            .tts_text(text)
                            .bucket(_repo_bucket)
                            .save_to(saveTo)
                            .build();
                    allTaskResult = allTaskResult.thenCombine(taskService.commitZeroShotTask(task),
                            (m, task_) -> {
                                m.put(key, task_.save_to);
                                return m;
                            });
                }
            }

            allTaskResult.whenComplete((result, ex) -> {
                if (result != null) {
                    final var synthRequest = ScriptApi.SessionSynthRequest.builder()
                            .sessionId(request.getSessionId())
                            .state(1)
                            .bucket(_repo_bucket)
                            .ttsMd5Map(result)
                            .build();
                    _scriptApi.report_synth(synthRequest);
                } else if (ex != null) {
                    log.warn("prepareSession: commitZeroShotTask failed: {}", ExceptionUtil.exception2detail(ex));
                }
            });

            resp = ApiResponse.<Void>builder().code("0000").build();
        } catch (final Exception ex) {
            log.warn("prepare_session failed: {}", ExceptionUtil.exception2detail(ex));
            resp = ApiResponse.<Void>builder().code("2000").message(ExceptionUtil.exception2detail(ex)).build();
        } finally {
            log.info("prepare_session: complete with resp: {}", resp);
        }
        return resp;
    }

    @RequestMapping(value = "/commit_zero_shot_tasks", method = RequestMethod.POST)
    @ResponseBody
    public ApiResponse<Void> commitZeroShotTasks(@RequestBody final CommitZeroShotTasksRequest request) {
        log.info("commit_zero_shot_tasks: task count:{}", request.tasks.length);

        ApiResponse<Void> resp = null;
        try {
            for (ZeroShotTask task : request.tasks) {
                taskService.commitZeroShotTask(task);
//                if ( null != zeroShotMemos.putIfAbsent(task.task_id,
//                        CVMasterServiceImpl.ZeroShotMemo.builder().task(task).status(0).build()) ) {
//                    log.warn("commitZeroShotTasks: task_id:{} has_committed_already, ignore", task.task_id);
//                }
            }
            //taskService.commitZeroShotTasks(request);
            resp = ApiResponse.<Void>builder().code("0000").build();
        } catch (final Exception ex) {
            log.warn("commit_zero_shot_tasks failed: {}", ExceptionUtil.exception2detail(ex));
            resp = ApiResponse.<Void>builder().code("2000").message(ExceptionUtil.exception2detail(ex)).build();
        } finally {
            log.info("commit_zero_shot_tasks: complete with resp: {}", resp);
        }
        return resp;
    }

    @RequestMapping(value = "/worker/status", method = RequestMethod.GET)
    @ResponseBody
    public ApiResponse<WorkerStatus> queryWorkerStatus() {
        ApiResponse<WorkerStatus> resp = null;
        try {
            final WorkerStatus status = taskService.queryWorkerStatus();
            resp = ApiResponse.<WorkerStatus>builder().code("0000").data(status).build();
        } catch (final Exception ex) {
            log.warn("/worker/status failed: {}", ExceptionUtil.exception2detail(ex));
            resp = ApiResponse.<WorkerStatus>builder().code("2000").message(ExceptionUtil.exception2detail(ex)).build();
        } finally {
            log.info("/worker/status: complete with resp: {}", resp);
        }
        return resp;
    }

    @RequestMapping(value = "/agent/all", method = RequestMethod.GET)
    @ResponseBody
    public ApiResponse<AgentMemo[]> queryAllAgentStatus() {
        ApiResponse<AgentMemo[]> resp = null;
        try {
            final AgentMemo[] memos = taskService.queryAllAgentStatus();
            resp = ApiResponse.<AgentMemo[]>builder().code("0000").data(memos).build();
        } catch (final Exception ex) {
            log.warn("/agent/all failed: {}", ExceptionUtil.exception2detail(ex));
            resp = ApiResponse.<AgentMemo[]>builder().code("2000").message(ExceptionUtil.exception2detail(ex)).build();
        } finally {
            log.info("/agent/all: complete with resp: {}", resp);
        }
        return resp;
    }

    @RequestMapping(value = "/task/status", method = RequestMethod.POST)
    @ResponseBody
    public ApiResponse<TaskStatus[]> queryTaskStatus(@RequestBody final QueryTaskStatusRequest request) {
        ApiResponse<TaskStatus[]> resp = null;
        try {
            final TaskStatus[] result = taskService.queryTaskStatus(request.task_ids);
            resp = ApiResponse.<TaskStatus[]>builder().code("0000").data(result).build();
        } catch (final Exception ex) {
            log.warn("/task/status failed: {}", ExceptionUtil.exception2detail(ex));
            resp = ApiResponse.<TaskStatus[]>builder().code("2000").message(ExceptionUtil.exception2detail(ex)).build();
        } finally {
            log.info("/task/status: complete with resp: {}", resp);
        }
        return resp;
    }

    @RequestMapping(value = "/task/all", method = RequestMethod.GET)
    @ResponseBody
    public ApiResponse<TaskStatus[]> queryAllTaskStatus() {
        ApiResponse<TaskStatus[]> resp = null;
        try {
            final TaskStatus[] result = taskService.queryAllTaskStatus();
            resp = ApiResponse.<TaskStatus[]>builder().code("0000").data(result).build();
        } catch (final Exception ex) {
            log.warn("/task/all failed: {}", ExceptionUtil.exception2detail(ex));
            resp = ApiResponse.<TaskStatus[]>builder().code("2000").message(ExceptionUtil.exception2detail(ex)).build();
        } finally {
            log.info("/task/all: complete with resp: {}", resp);
        }
        return resp;
    }

    @RequestMapping(value = "/task/summary", method = RequestMethod.GET)
    @ResponseBody
    public ApiResponse<TaskSummary> queryTaskSummary() {
        ApiResponse<TaskSummary> resp = null;
        try {
            final TaskSummary result = taskService.queryTaskSummary();
            resp = ApiResponse.<TaskSummary>builder().code("0000").data(result).build();
        } catch (final Exception ex) {
            log.warn("/task/summary failed: {}", ExceptionUtil.exception2detail(ex));
            resp = ApiResponse.<TaskSummary>builder().code("2000").message(ExceptionUtil.exception2detail(ex)).build();
        } finally {
            log.info("/task/summary: complete with resp: {}", resp);
        }
        return resp;
    }

    @Autowired
    private CVTaskService taskService;
}
