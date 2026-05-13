package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResumeSaveDTO {

    @NotBlank(message = "title is required")
    private String title;

    private String realName;
    private String email;
    private String phone;
    private String summary;
}
