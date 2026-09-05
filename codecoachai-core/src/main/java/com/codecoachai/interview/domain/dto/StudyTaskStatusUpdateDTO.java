package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StudyTaskStatusUpdateDTO {

    @NotBlank(message = "请选择学习任务状态")
    private String taskStatus;
}
