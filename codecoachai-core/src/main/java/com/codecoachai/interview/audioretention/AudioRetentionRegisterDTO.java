package com.codecoachai.interview.audioretention;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AudioRetentionRegisterDTO {

    @NotNull
    private Long voiceSubmissionId;
    @NotNull
    private Long fileId;
    @NotBlank
    @Size(max = 32)
    private String policyCode;
    @NotNull
    private LocalDateTime retainUntil;
}
