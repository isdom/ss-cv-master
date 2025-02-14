package com.yulore.cvmaster.controller;

import com.yulore.cvmaster.service.CVTaskService;
import com.yulore.cvmaster.vo.*;
import com.yulore.util.ExceptionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@Slf4j
@RequestMapping("/cv")
public class ApiController {
    @RequestMapping(value = "/commit_zero_shot_tasks", method = RequestMethod.POST)
    @ResponseBody
    public ApiResponse<Void> commitZeroShotTasks(@RequestBody final CommitZeroShotTasksRequest request) {
        log.info("commit_zero_shot_tasks: task count:{}", request.tasks.length);

        ApiResponse<Void> resp = null;
        try {
            taskService.commitZeroShotTasks(request);
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

    @RequestMapping(value = "/task/status", method = RequestMethod.POST)
    @ResponseBody
    public ApiResponse<TaskStatusResult> queryTaskStatus(@RequestBody final QueryTaskStatusRequest request) {
        ApiResponse<TaskStatusResult> resp = null;
        try {
            final TaskStatusResult result = taskService.queryTaskStatus(request.task_ids);
            resp = ApiResponse.<TaskStatusResult>builder().code("0000").data(result).build();
        } catch (final Exception ex) {
            log.warn("/task/status failed: {}", ExceptionUtil.exception2detail(ex));
            resp = ApiResponse.<TaskStatusResult>builder().code("2000").message(ExceptionUtil.exception2detail(ex)).build();
        } finally {
            log.info("/task/status: complete with resp: {}", resp);
        }
        return resp;
    }

    @Autowired
    private CVTaskService taskService;
}
