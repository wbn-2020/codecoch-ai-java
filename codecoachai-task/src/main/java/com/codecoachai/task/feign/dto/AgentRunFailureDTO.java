package com.codecoachai.task.feign.dto;

import lombok.Data;

@Data
public class AgentRunFailureDTO {

    private Long userId;

    private String errorCode;

    private String errorMessage;
}
