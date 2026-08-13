package com.codecoachai.interview.streamingasr;

public interface StreamingAsrProviderSession {

    StreamingAsrSnapshot accept(StreamingAsrAudioChunk chunk);

    StreamingAsrSnapshot complete();

    StreamingAsrSnapshot cancel();

    StreamingAsrSnapshot snapshot();
}
