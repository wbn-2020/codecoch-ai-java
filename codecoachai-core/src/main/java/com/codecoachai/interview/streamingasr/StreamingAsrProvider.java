package com.codecoachai.interview.streamingasr;

import com.codecoachai.interview.voice.task.ProviderExecutionContext;

public interface StreamingAsrProvider {

    String providerCode();

    StreamingAsrProviderSession open(StreamingAsrOpenRequest request, ProviderExecutionContext context);
}
