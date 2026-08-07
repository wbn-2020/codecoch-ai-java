package com.codecoachai.resume.domain.vo;

import lombok.Data;

@Data
public class CareerInsightItemVO {
    private String type;
    private String title;
    private String description;
    private String severity;
    private String evidence;
    private String actionLabel;
    private String actionPath;
}
