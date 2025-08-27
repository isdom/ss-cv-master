package com.yulore.cvmaster.vo;

import lombok.Data;
import lombok.ToString;

import java.util.Map;

@ToString
@Data
public class PrepareSessionRequest {
    private String sessionId;
    private Map<String, String> ttsTextList;  //占位符
    private String promptText; //参考音
    private String promptWav; //参考音
    private String promptBucket;
}
