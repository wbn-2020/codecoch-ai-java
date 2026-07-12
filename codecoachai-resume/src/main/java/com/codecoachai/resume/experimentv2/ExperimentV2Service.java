package com.codecoachai.resume.experimentv2;

import com.codecoachai.resume.experimentv2.ExperimentV2Models.AssignmentCreate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.AssignmentView;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.AttributionView;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.CohortCreate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.CohortView;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.HypothesisCreate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.HypothesisUpdate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.HypothesisView;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.VariantCreate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.VariantView;
import java.time.LocalDateTime;
import java.util.List;

public interface ExperimentV2Service {

    HypothesisView createHypothesis(HypothesisCreate request);

    HypothesisView getHypothesis(Long hypothesisId);

    List<HypothesisView> listHypotheses(String status, String keyword,
                                        Long legacyExperimentId, Integer limit);

    HypothesisView updateHypothesis(Long hypothesisId, HypothesisUpdate request);

    VariantView addVariant(Long hypothesisId, VariantCreate request);

    AssignmentView assign(Long hypothesisId, AssignmentCreate request);

    List<AssignmentView> listAssignments(Long hypothesisId);

    CohortView createCohort(Long hypothesisId, CohortCreate request);

    List<CohortView> listCohorts(Long hypothesisId);

    AttributionView attribute(Long cohortId, LocalDateTime asOf);

    AttributionView getLatestAttribution(Long cohortId);

    List<AttributionView> listAttributions(Long cohortId, Integer limit);
}
