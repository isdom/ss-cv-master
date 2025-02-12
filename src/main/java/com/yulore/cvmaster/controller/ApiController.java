package com.yulore.cvmaster.controller;

import com.yulore.api.CosyVoiceService;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRemoteService;
import org.redisson.api.RedissonClient;
import org.redisson.api.RemoteInvocationOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Controller
@Slf4j
@RequestMapping("/cv")
public class ApiController {
    @Autowired
    public ApiController(final RedissonClient redisson) {
        final RRemoteService rs1 = redisson.getRemoteService(_service_cosyvoice);
        cosyVoiceService = rs1.get(CosyVoiceService.class,
                RemoteInvocationOptions.defaults().noAck().expectResultWithin(300 * 1000L));
    }

    @Data
    @ToString
    static public class ZeroShotRequest {
        private String tts_text;
        private String prompt_text;
        private String prompt_wav;
        private String bucket;
        private String saveTo;
    }

    @RequestMapping(value = "/zero_shot", method = RequestMethod.POST)
    public String zero_shot(@RequestBody final ZeroShotRequest request) {
        log.info("zero_shot: ttsText:{} / promptText:{} / promptWav:{}", request.tts_text, request.prompt_text, request.prompt_wav);

        String result = null;
        try {
            result = cosyVoiceService.inferenceZeroShotAndSave(request.tts_text, request.prompt_text, request.prompt_wav, request.bucket, request.saveTo);
            return result;
        } finally {
            log.info("zero_shot: complete with: {}", result);
        }
    }

    @Value("${service.cosyvoice}")
    private String _service_cosyvoice;

    private final CosyVoiceService cosyVoiceService;
}
