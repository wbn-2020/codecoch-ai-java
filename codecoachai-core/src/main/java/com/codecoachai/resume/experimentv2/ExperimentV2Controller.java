package com.codecoachai.resume.experimentv2;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/job-experiments-v2")
public class ExperimentV2Controller {

    private final ExperimentV2Service experimentV2Service;

    @OperationLog(module = "job-experiment-v2", action = "CREATE_HYPOTHESIS",
            description = "Create an auditable job search hypothesis", logResponse = false)
    @PostMapping("/hypotheses")
    public Result<HypothesisView> createHypothesis(@Valid @RequestBody HypothesisCreate request) {
        SecurityAssert.requireLoginUserId();
        return Result.success(experimentV2Service.createHypothesis(request));
    }

    @GetMapping("/hypotheses/{hypothesisId}")
    public Result<HypothesisView> getHypothesis(@PathVariable Long hypothesisId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(experimentV2Service.getHypothesis(hypothesisId));
    }

    @GetMapping("/hypotheses")
    public Result<List<HypothesisView>> listHypotheses(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long legacyExperimentId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) Integer limit) {
        SecurityAssert.requireLoginUserId();
        return Result.success(experimentV2Service.listHypotheses(
                status, keyword, legacyExperimentId, limit));
    }

    @OperationLog(module = "job-experiment-v2", action = "UPDATE_HYPOTHESIS",
            description = "Update hypothesis metadata, design, or lifecycle status", logResponse = false)
    @PutMapping("/hypotheses/{hypothesisId}")
    public Result<HypothesisView> updateHypothesis(@PathVariable Long hypothesisId,
                                                    @Valid @RequestBody HypothesisUpdate request) {
        SecurityAssert.requireLoginUserId();
        return Result.success(experimentV2Service.updateHypothesis(hypothesisId, request));
    }

    @OperationLog(module = "job-experiment-v2", action = "UPDATE_HYPOTHESIS",
            description = "Patch hypothesis metadata, design, or lifecycle status", logResponse = false)
    @PatchMapping("/hypotheses/{hypothesisId}")
    public Result<HypothesisView> patchHypothesis(@PathVariable Long hypothesisId,
                                                   @Valid @RequestBody HypothesisUpdate request) {
        SecurityAssert.requireLoginUserId();
        return Result.success(experimentV2Service.updateHypothesis(hypothesisId, request));
    }

    @OperationLog(module = "job-experiment-v2", action = "ADD_VARIANT",
            description = "Add experiment variant", logResponse = false)
    @PostMapping("/hypotheses/{hypothesisId}/variants")
    public Result<VariantView> addVariant(@PathVariable Long hypothesisId,
                                          @Valid @RequestBody VariantCreate request) {
        SecurityAssert.requireLoginUserId();
        return Result.success(experimentV2Service.addVariant(hypothesisId, request));
    }

    @OperationLog(module = "job-experiment-v2", action = "ASSIGN_APPLICATION",
            description = "Assign application to experiment variant", logResponse = false)
    @PostMapping("/hypotheses/{hypothesisId}/assignments")
    public Result<AssignmentView> assign(@PathVariable Long hypothesisId,
                                         @Valid @RequestBody AssignmentCreate request) {
        SecurityAssert.requireLoginUserId();
        return Result.success(experimentV2Service.assign(hypothesisId, request));
    }

    @GetMapping("/hypotheses/{hypothesisId}/assignments")
    public Result<List<AssignmentView>> listAssignments(@PathVariable Long hypothesisId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(experimentV2Service.listAssignments(hypothesisId));
    }

    @OperationLog(module = "job-experiment-v2", action = "CREATE_COHORT",
            description = "Create stratified experiment cohort", logResponse = false)
    @PostMapping("/hypotheses/{hypothesisId}/cohorts")
    public Result<CohortView> createCohort(@PathVariable Long hypothesisId,
                                           @Valid @RequestBody CohortCreate request) {
        SecurityAssert.requireLoginUserId();
        return Result.success(experimentV2Service.createCohort(hypothesisId, request));
    }

    @GetMapping("/hypotheses/{hypothesisId}/cohorts")
    public Result<List<CohortView>> listCohorts(@PathVariable Long hypothesisId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(experimentV2Service.listCohorts(hypothesisId));
    }

    @OperationLog(module = "job-experiment-v2", action = "CALCULATE_ATTRIBUTION",
            description = "Calculate corrected stratified attribution", logResponse = false)
    @PostMapping("/cohorts/{cohortId}/attribution")
    public Result<AttributionView> attribute(@PathVariable Long cohortId,
                                             @RequestParam(required = false) LocalDateTime asOf) {
        SecurityAssert.requireLoginUserId();
        return Result.success(experimentV2Service.attribute(cohortId, asOf));
    }

    @GetMapping("/cohorts/{cohortId}/attributions/latest")
    public Result<AttributionView> getLatestAttribution(@PathVariable Long cohortId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(experimentV2Service.getLatestAttribution(cohortId));
    }

    @GetMapping("/cohorts/{cohortId}/attributions")
    public Result<List<AttributionView>> listAttributions(
            @PathVariable Long cohortId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        SecurityAssert.requireLoginUserId();
        return Result.success(experimentV2Service.listAttributions(cohortId, limit));
    }
}
