package com.codecoachai.ai.agent.domain.vo.weekly;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class WeeklyReportFactVO {

    private String factId;
    private String factType;
    private String label;
    private Object value;
    private String unit;
    private String scope;
    private String timeWindow;
    private List<String> sourceRefs = new ArrayList<>();
    private String calculationVersion;
}
