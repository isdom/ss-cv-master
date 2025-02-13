package com.yulore.cvmaster.service;

import com.yulore.api.CosyVoiceService;
import org.redisson.api.RFuture;
import org.redisson.api.annotation.RRemoteAsync;

@RRemoteAsync(CosyVoiceService.class)
public interface CosyVoiceServiceAsync {
    RFuture<String> inferenceZeroShotAndSave(final String ttsText,
                                              final String promptText,
                                              final String promptWav,
                                              final String bucket,
                                              final String saveTo);
}
