package com.codecoachai.interview.feign.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class InnerSkillProfileVO {

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
    private String rawResultJson;
    private Long aiCallLogId;
    private String errorMessage;
    private String targetJobTitle;
    private String targetCompanyName;
    private String targetJobLevel;
    private String targetJdSource;
    private List<InnerSkillGapItemVO> gapItems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
