package com.codecoachai.user.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserOverviewVO {

    private Integer resumeCount;
    private Integer interviewCount;
    private Integer completedInterviewCount;
    private Integer questionAnsweredCount;
    private Integer wrongQuestionCount;
    private Integer favoriteQuestionCount;
}
