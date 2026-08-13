package com.codecoachai.interview.streamingasr;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StreamingAsrChunkDTO {

    @Min(0)
    @NotNull
    private Long sequence;
    @NotBlank
    private String audioBase64;
    private Boolean endOfStream = Boolean.FALSE;
}
