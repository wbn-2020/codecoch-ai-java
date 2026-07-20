package com.codecoachai.ai.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GenerateAgentReviewVO {

    private String summary;
    private List<String> facts = new ArrayList<>();
    private List<String> limits = new ArrayList<>();
    private List<String> driftReasons = new ArrayList<>();
    private List<String> adjustments = new ArrayList<>();
    private List<String> nextActions = new ArrayList<>();
    private Long aiCallLogId;
    private String rawResponse;
}
