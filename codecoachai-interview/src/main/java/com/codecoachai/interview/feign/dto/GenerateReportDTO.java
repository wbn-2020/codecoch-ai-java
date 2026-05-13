package com.codecoachai.interview.feign.dto;

import java.util.List;
import lombok.Data;

@Data
public class GenerateReportDTO {

    private Long interviewId;
    private String mode;
    private List<String> messages;
}
