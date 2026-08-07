package com.codecoachai.resume.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class JobSearchExperimentDetailVO extends JobSearchExperimentListVO {

    private List<JobSearchExperimentRelationVO> relations = new ArrayList<>();
    private List<JobSearchExperimentReviewVO> reviews = new ArrayList<>();
    private JobSearchExperimentReviewVO latestReview;
    private JobSearchExperimentStrategyVO strategy;
}
