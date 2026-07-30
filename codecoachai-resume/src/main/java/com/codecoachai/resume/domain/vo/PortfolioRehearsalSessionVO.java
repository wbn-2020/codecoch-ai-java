package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class PortfolioRehearsalSessionVO {

    private String activeRouteKey;
    private Integer activeNodeIndex;
    private Integer elapsedSeconds;
    private List<String> completedNodeIds = new ArrayList<>();
    private LocalDateTime updatedAt;
}
