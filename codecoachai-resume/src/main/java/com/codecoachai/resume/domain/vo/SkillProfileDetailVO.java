package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class SkillProfileDetailVO {

    private Long profileId;
    private Long userId;
    private Long targetJobId;
    private Long matchReportId;
    private String profileName;
    private Integer overallLevel;
    private Integer overallScore;
    private String summary;
    private String sourceType;
    private Long sourceBizId;
    private String status;
    private String errorMessage;
    private JsonNode rawResult;
    private Long aiCallLogId;
    private List<SkillGapItemVO> gapItems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
