package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class ResumeSuggestionBatchAcceptDTO {

    @NotEmpty
    @Size(max = 50)
    private List<Long> suggestionIds;

    @NotBlank
    @Size(max = 96)
    private String idempotencyKey;

    @Size(max = 1000)
    private String note;
}
