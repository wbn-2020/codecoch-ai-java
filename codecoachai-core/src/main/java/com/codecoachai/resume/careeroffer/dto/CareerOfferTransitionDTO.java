package com.codecoachai.resume.careeroffer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CareerOfferTransitionDTO {
    @NotBlank
    private String targetStatus;
    private Integer expectedLockVersion;
    private Integer applicationLockVersion;
    @NotNull
    private Boolean userConfirmed;
}
