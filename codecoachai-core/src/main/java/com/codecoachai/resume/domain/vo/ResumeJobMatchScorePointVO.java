package com.codecoachai.resume.domain.vo;

import lombok.Data;

/**
 * 匹配报告历史分数点：同岗位历次成功报告的关键分数，用于详情页“历史变化”展示。
 * 只取成功且分数齐全的报告，按时间升序。
 */
@Data
public class ResumeJobMatchScorePointVO {

    private Long reportId;

    private Integer overallScore;

    private Integer techStackScore;

    private Integer projectExperienceScore;

    private Integer businessFitScore;

    private Integer communicationScore;

    private String createdAt;
}
