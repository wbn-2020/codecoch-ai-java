package com.codecoachai.interview.streamingasr;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class StreamingAsrSessionVO {

    private String sessionId;
    private String provider;
    private String status;
    private String partialTranscript;
    private String finalTranscript;
    private String timestampMode;
    private List<StreamingAsrWord> words;
    private Long acceptedChunks;
    private Long acceptedBytes;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime deadlineAt;
}
