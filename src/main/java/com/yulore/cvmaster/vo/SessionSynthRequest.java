package com.yulore.cvmaster.vo;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.Map;

@Builder
@Data
@ToString
public class SessionSynthRequest {
    private String sessionId;
    private int state;  //1成功，0失败
    private String bucket;
    private Map<String, String> ttsMd5Map; //K为tts文字的md5,V为合成路径
}
