package com.yulore.api;

import com.yulore.cvmaster.vo.ApiResponse;
import feign.Request;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@FeignClient(
        value = "${script.srv}",
        configuration = ScriptApi.ScriptApiConfig.class
)
@ConditionalOnProperty(prefix = "script", name = "srv")
public interface ScriptApi {
    @Builder
    @Data
    @ToString
    public class SessionSynthRequest {
        private String sessionId;
        private int state;  //1成功，0失败
        private String bucket;
        private Map<String, String> ttsMd5Map; //K为tts文字的md5,V为合成路径
    }

    @RequestMapping(value = "${script.api.report_synth}", method = RequestMethod.POST)
    ApiResponse<Void> report_synth(@RequestBody SessionSynthRequest request);

    // 配置类定义
    class ScriptApiConfig {
        @Bean
        public Request.Options options() {
            // connect(200ms), read(500ms), followRedirects(true)
            return new Request.Options(200, TimeUnit.MILLISECONDS,  500, TimeUnit.MILLISECONDS,true);
        }
    }}
