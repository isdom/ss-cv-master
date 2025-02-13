package com.yulore.cvmaster.controller;

import com.yulore.cvmaster.service.CVTaskService;
import com.yulore.cvmaster.vo.CommitZeroShotTasksRequest;
import com.yulore.cvmaster.vo.CommitZeroShotTasksResponse;
import com.yulore.cvmaster.vo.QueryTaskStatusResponse;
import com.yulore.cvmaster.vo.QueryWorkerStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@Slf4j
@RequestMapping("/cv")
public class ApiController {
//    @Autowired
//    public ApiController(@Value("${service.cosyvoice}") final String service_cosyvoice,
//                         final RedissonClient redisson) {
//        cosyVoiceService = redisson.getRemoteService(service_cosyvoice)
//                .get(CosyVoiceService.class, RemoteInvocationOptions.defaults()/*.noAck()*/
//                        .expectResultWithin(300 * 1000L));
//    }
//
//    @Data
//    @ToString
//    static public class ZeroShotRequest {
//        private String tts_text;
//        private String prompt_text;
//        private String prompt_wav;
//        private String bucket;
//        private String save_to;
//    }
//
//    @RequestMapping(value = "/zero_shot", method = RequestMethod.POST)
//    @ResponseBody
//    public String zero_shot(@RequestBody final ZeroShotRequest request) {
//        log.info("zero_shot: ttsText:{} / promptText:{} / promptWav:{}", request.tts_text, request.prompt_text, request.prompt_wav);
//
//        String result = null;
//        try {
//            result = cosyVoiceService.inferenceZeroShotAndSave(request.tts_text, request.prompt_text, request.prompt_wav, request.bucket, request.save_to);
//            return result;
//        } finally {
//            log.info("zero_shot: complete with: {}", result);
//        }
//    }

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

    @RequestMapping(value = "/task/status", method = RequestMethod.GET)
    @ResponseBody
    public QueryTaskStatusResponse queryTaskStatus(@RequestParam(value = "task_id") String taskId) {
        return taskService.queryTaskStatus(taskId);
    }

    // private final CosyVoiceService cosyVoiceService;

    @Autowired
    private CVTaskService taskService;
}
