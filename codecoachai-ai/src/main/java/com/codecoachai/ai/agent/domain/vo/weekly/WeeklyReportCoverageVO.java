package com.codecoachai.ai.agent.domain.vo.weekly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class WeeklyReportCoverageVO {

    private Map<String, Integer> includedCounts = new LinkedHashMap<>();
    private Map<String, Integer> excludedCounts = new LinkedHashMap<>();
    private Map<String, Integer> unavailableCounts = new LinkedHashMap<>();
    private List<WeeklySourceCoverageItemVO> sources = new ArrayList<>();
    private Boolean truncated = false;
    private List<String> warnings = new ArrayList<>();
    private String consistencyLevel = "COMPLETE";
}
