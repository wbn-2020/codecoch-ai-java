package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateFromGapDTO;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateFromMatchReportDTO;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateDTO;
import com.codecoachai.interview.domain.dto.StudyPlanQueryDTO;
import com.codecoachai.interview.domain.dto.StudyTaskStatusUpdateDTO;
import com.codecoachai.interview.domain.vo.StudyPlanDailyViewVO;
import com.codecoachai.interview.domain.vo.StudyPlanDetailVO;
import com.codecoachai.interview.domain.vo.StudyPlanGenerateVO;
import com.codecoachai.interview.domain.vo.StudyPlanListVO;
import com.codecoachai.interview.domain.vo.StudyPlanSkillRelationVO;
import com.codecoachai.interview.domain.vo.StudyPlanSourceTypeVO;
import com.codecoachai.interview.domain.vo.StudyTaskVO;
import com.codecoachai.interview.service.StudyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping
@Tag(name = "Study Plan", description = "User study plan APIs")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    @PostMapping("/study-plans/generate")
    public Result<StudyPlanGenerateVO> generate(@Valid @RequestBody StudyPlanGenerateDTO dto) {
        return Result.success(studyPlanService.generate(dto));
    }

    @PostMapping("/study-plans/generate-from-gap")
    public Result<StudyPlanGenerateVO> generateFromGap(@Valid @RequestBody StudyPlanGenerateFromGapDTO dto) {
        return Result.success(studyPlanService.generateFromGap(dto));
    }

    @PostMapping("/study-plans/generate-from-match-report")
    public Result<StudyPlanGenerateVO> generateFromMatchReport(
            @Valid @RequestBody StudyPlanGenerateFromMatchReportDTO dto) {
        return Result.success(studyPlanService.generateFromMatchReport(dto));
    }

    @GetMapping("/study-plans/source-types")
    public Result<List<StudyPlanSourceTypeVO>> sourceTypes() {
        return Result.success(studyPlanService.sourceTypes());
    }

    @GetMapping("/study-plans")
    public Result<PageResult<StudyPlanListVO>> list(@ModelAttribute StudyPlanQueryDTO dto) {
        return Result.success(studyPlanService.list(dto));
    }

    @GetMapping("/study-plans/{id}")
    public Result<StudyPlanDetailVO> detail(@PathVariable Long id) {
        return Result.success(studyPlanService.detail(id));
    }

    @GetMapping("/study-plans/{id}/tasks")
    public Result<List<StudyTaskVO>> tasks(@PathVariable Long id) {
        return Result.success(studyPlanService.tasks(id));
    }

    @GetMapping("/study-plans/{id}/skill-relations")
    public Result<List<StudyPlanSkillRelationVO>> skillRelations(@PathVariable Long id) {
        return Result.success(studyPlanService.skillRelations(id));
    }

    @GetMapping("/study-plans/{planId}/daily-view")
    @Operation(summary = "Get daily study task view",
            description = "Returns task statistics and task list for one day in a user-owned study plan. "
                    + "date is optional and uses yyyy-MM-dd; when omitted, the server uses today.")
    public Result<StudyPlanDailyViewVO> dailyView(
            @Parameter(description = "Study plan id") @PathVariable Long planId,
            @Parameter(description = "Optional date in yyyy-MM-dd format. Defaults to today.")
            @RequestParam(required = false) String date) {
        return Result.success(studyPlanService.dailyView(planId, date));
    }

    @PostMapping("/study-plans/{id}/regenerate")
    public Result<StudyPlanGenerateVO> regenerate(@PathVariable Long id) {
        return Result.success(studyPlanService.regenerate(id));
    }

    @PostMapping("/study-tasks/{taskId}/status")
    public Result<StudyTaskVO> updateTaskStatus(@PathVariable Long taskId,
                                                @Valid @RequestBody StudyTaskStatusUpdateDTO dto) {
        return Result.success(studyPlanService.updateTaskStatus(taskId, dto));
    }

    @PostMapping("/study-tasks/{taskId}/complete")
    public Result<StudyTaskVO> completeTask(@PathVariable Long taskId) {
        return Result.success(studyPlanService.completeTask(taskId));
    }

    @PostMapping("/study-tasks/{taskId}/skip")
    public Result<StudyTaskVO> skipTask(@PathVariable Long taskId) {
        return Result.success(studyPlanService.skipTask(taskId));
    }
}
