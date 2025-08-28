package com.yulore.cvmaster.vo;

import lombok.Data;
import lombok.ToString;

import java.util.Map;

@ToString
@Data
public class PrepareSessionRequest {
    private String sessionId;
    private int botId;
    private Map<String, String> ttsTextList;  //待合成文本
    private String promptText; //参考音文本
    private String promptWav; //参考音: {bucket=bucket_name1}yyyy.wav
}
