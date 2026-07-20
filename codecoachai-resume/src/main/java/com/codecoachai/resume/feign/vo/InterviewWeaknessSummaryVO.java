package com.codecoachai.resume.feign.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class InterviewWeaknessSummaryVO {

    private Integer rangeDays;
    private Long interviewCount;
    private Long reportCount;
    private List<WeaknessItemVO> topWeaknesses = new ArrayList<>();

    @Data
    public static class WeaknessItemVO {
        private String name;
        private String category;
        private Long count;
        private String evidence;
        private String recommendedActionType;
        private String actionPath;
    }
}
