package com.codecoachai.interview.tts;

import com.codecoachai.interview.voice.task.ProviderExecutionContext;

public interface TtsProvider {

    String providerCode();

    TtsSynthesisResult synthesize(TtsSynthesisRequest request, ProviderExecutionContext context);
}
