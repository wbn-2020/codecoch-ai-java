package com.codecoachai.resume.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class JobRequirementMatrixVO {

    private Long targetJobId;
    private Long jdAnalysisId;
    private Integer requirementCount;
    private Integer strongCount;
    private Integer weakCount;
    private Integer missingCount;
    private List<String> warnings = new ArrayList<>();
    private List<RequirementItem> requirements = new ArrayList<>();

    @Data
    public static class RequirementItem {

        private Long requirementId;
        private String requirementKey;
        private String requirementType;
        private String requirementName;
        private String priority;
        private BigDecimal weight;
        private String requirementConfidence;
        private Boolean requirementFallback;
        private String coverageLevel;
        private List<EvidenceItem> evidences = new ArrayList<>();
        private List<NextAction> nextActions = new ArrayList<>();
    }

    @Data
    public static class EvidenceItem {

        private Long id;
        private String evidenceType;
        private Long evidenceId;
        private Long evidenceSubId;
        private String title;
        private String excerpt;
        private String resultSource;
        private Integer score;
        private LocalDateTime occurredAt;
        private Long projectEvidenceId;
        private Long projectSkillEvidenceId;
        private String projectTitle;
        private String skillName;
        private String matchType;
        private String coverageLevel;
        private String confidenceLevel;
        private String sourceType;
        private Boolean confirmed;
        private Boolean fallback;
        private String evidenceText;
        private String matchReason;
    }

    @Data
    public static class NextAction {

        private String actionCode;
        private String title;
        private String path;
    }
}
