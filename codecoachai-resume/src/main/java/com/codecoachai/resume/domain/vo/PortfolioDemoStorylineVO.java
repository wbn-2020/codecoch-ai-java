package com.codecoachai.resume.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class PortfolioDemoStorylineVO {

    private PortfolioDemoStatusVO status;
    private List<Step> steps = new ArrayList<>();
    private List<Step> opsSteps = new ArrayList<>();

    @Data
    public static class Step {
        private String key;
        private String title;
        private String route;
        private String entityType;
        private Long entityId;
        private String evidenceSummary;
        private String status;
        private Boolean demoData;
    }
}
