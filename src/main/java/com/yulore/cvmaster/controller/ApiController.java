package com.yulore.cvmaster.controller;

import com.yulore.cvmaster.service.CVTaskService;
import com.yulore.cvmaster.vo.*;
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
    public CommitZeroShotTasksResponse commitZeroShotTasks(@RequestBody final CommitZeroShotTasksRequest request) {
        log.info("commit_zero_shot_tasks: task count:{}", request.tasks.length);

        CommitZeroShotTasksResponse resp = null;
        try {
            resp = taskService.commitZeroShotTasks(request);
            return resp;
        } finally {
            log.info("commit_zero_shot_tasks: complete with: {}", resp);
        }
    }

    @RequestMapping(value = "/worker/status", method = RequestMethod.GET)
    @ResponseBody
    public QueryWorkerStatusResponse queryWorkerStatus() {
        return taskService.queryWorkerStatus();
    }

    @RequestMapping(value = "/task/status", method = RequestMethod.POST)
    @ResponseBody
    public QueryTaskStatusResponse queryTaskStatus(@RequestBody final QueryTaskStatusRequest request) {
        return QueryTaskStatusResponse.builder().statuses(taskService.queryTaskStatus(request.task_ids)).build();
    }

    @Autowired
    private CVTaskService taskService;
}
