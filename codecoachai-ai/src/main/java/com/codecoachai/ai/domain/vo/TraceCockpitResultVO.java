package com.codecoachai.ai.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TraceCockpitResultVO {

    private String source = "BACKEND_AGGREGATED";
    private String dataSource = "BACKEND_AGGREGATED";
    private Boolean partialResult = false;
    private TraceOverviewVO overview;
    private List<TraceModuleStatusVO> moduleStatuses = new ArrayList<>();
    private TraceTimelineVO timeline;
    private List<TraceNodeVO> nodes = new ArrayList<>();
    private List<TraceEdgeVO> edges = new ArrayList<>();
    private List<TraceRiskVO> risks = new ArrayList<>();
    private List<TraceGovernanceSuggestionVO> suggestions = new ArrayList<>();
}
