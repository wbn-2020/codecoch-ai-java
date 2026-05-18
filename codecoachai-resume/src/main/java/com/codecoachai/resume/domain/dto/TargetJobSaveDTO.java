package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TargetJobSaveDTO {

    @NotBlank(message = "jobTitle is required")
    @Size(max = 128, message = "jobTitle length must be less than 128")
    private String jobTitle;

    @Size(max = 128, message = "companyName length must be less than 128")
    private String companyName;

    @Size(max = 64, message = "jobLevel length must be less than 64")
    private String jobLevel;

    @Size(max = 20000, message = "jdText length must be less than 20000")
    private String jdText;

    @Size(max = 64, message = "jdSource length must be less than 64")
    private String jdSource;
}
