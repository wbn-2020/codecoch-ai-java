package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class JobApplicationEventReviewGenerateDTO {

    @Size(max = 10)
    private List<@Size(max = 300) String> observedFacts;

    @Size(max = 2000)
    private String externalFeedback;

    @Size(max = 2000)
    private String selfReflection;

    private Boolean force;

    @Size(max = 64)
    private String requestId;
}
