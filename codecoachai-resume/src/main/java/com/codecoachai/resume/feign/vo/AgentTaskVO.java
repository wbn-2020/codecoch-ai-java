package com.codecoachai.resume.feign.vo;

import lombok.Data;

@Data
public class AgentTaskVO {

    private Long id;

    private String title;

    private String status;

    private String reviewSummary;

    private String reviewSource;

    private String reviewSourceLabel;
}
