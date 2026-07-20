package com.codecoachai.resume.careeroffer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CareerOfferCreateDTO {
    @NotNull
    private Long applicationId;
    private String status;
    private Long versionId;
}
