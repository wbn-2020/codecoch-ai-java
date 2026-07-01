package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class WeaknessInsightItemVO {

    private String name;
    private String category;
    private Long count;
    private String evidence;
    private String recommendedActionType;
    private String actionPath;
}
