package com.codecoachai.interview.streamingasr;

public interface StreamingAsrSessionService {

    StreamingAsrSessionVO open(StreamingAsrSessionCreateDTO dto);

    StreamingAsrSessionVO accept(String sessionId, StreamingAsrChunkDTO dto);

    StreamingAsrSessionVO complete(String sessionId);

    StreamingAsrSessionVO get(String sessionId);

    StreamingAsrSessionVO cancel(String sessionId);
}
