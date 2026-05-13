package com.codecoachai.ai.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class GenerateReportDTO {

    private Long interviewId;
    private String mode;
    private List<String> messages;
}
