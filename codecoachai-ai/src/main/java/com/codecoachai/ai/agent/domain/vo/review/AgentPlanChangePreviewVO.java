package com.codecoachai.ai.agent.domain.vo.review;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentPlanChangePreviewVO {

    private Long changeSetId;
    private Long reviewId;
    private Integer reviewVersion;
    private Long targetJobId;
    private LocalDate targetDate;
    private String status;
    private Integer previewVersion;
    private String previewHash;
    private LocalDateTime expiresAt;
    private Boolean confirmable;
    private String resultSource;
    private Boolean fallback;
    private AgentPlanChangeSummaryVO summary = new AgentPlanChangeSummaryVO();
    private List<AgentPlanChangeItemVO> items = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> blockers = new ArrayList<>();
    private LocalDateTime confirmedAt;
    private LocalDateTime appliedAt;
    private String failureCode;
    private String failureMessage;
}
