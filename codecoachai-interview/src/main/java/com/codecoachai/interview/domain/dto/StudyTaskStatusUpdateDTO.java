package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StudyTaskStatusUpdateDTO {

    @NotBlank(message = "taskStatus is required")
    private String taskStatus;
}
