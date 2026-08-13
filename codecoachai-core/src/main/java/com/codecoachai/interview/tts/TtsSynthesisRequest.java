package com.codecoachai.interview.tts;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TtsSynthesisRequest {

    String requestId;
    String text;
    String voice;
    String locale;
    String audioFormat;
}
