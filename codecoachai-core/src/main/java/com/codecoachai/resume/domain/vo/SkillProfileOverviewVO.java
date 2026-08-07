package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Data;

@Data
public class SkillProfileOverviewVO {

    private Boolean empty;
    private Long profileId;
    private Long targetJobId;
    private String profileName;
    private Integer overallLevel;
    private Integer overallScore;
    private String status;
    private String summary;
    private List<RadarDataItemVO> radarData;
    private List<SkillGapItemVO> topGaps;
    private JsonNode nextPrioritySkills;
    private JsonNode nextActions;
    private Integer gapCount;

    @Data
    public static class RadarDataItemVO {

        private String skillName;
        private String category;
        private Integer targetLevel;
        private Integer currentLevel;
        private Integer gapLevel;
        private String severity;
    }
}
