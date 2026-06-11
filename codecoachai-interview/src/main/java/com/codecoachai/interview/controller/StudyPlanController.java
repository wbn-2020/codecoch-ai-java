package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.web.log.OperationLog;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
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
    @OperationLog(module = "study", action = "QUERY_DAILY_VIEW", description = "查询每日学习任务", logArgs = true, logResponse = false)
    @Operation(summary = "Get daily study task view",
            description = "Returns task statistics and task list for one day in a user-owned study plan. "
                    + "date is optional and uses yyyy-MM-dd; when omitted, the server uses today.")
    public Result<StudyPlanDailyViewVO> dailyView(
            @Parameter(description = "Study plan id") @PathVariable Long planId,
            @Parameter(description = "Optional date in yyyy-MM-dd format. Defaults to today.")
            @RequestParam(required = false) String date) {
        return Result.success(studyPlanService.dailyView(planId, date));
    }

    @GetMapping("/daily-tasks")
    @OperationLog(module = "study", action = "QUERY_DAILY_TASKS", description = "查询每日任务入口", logArgs = true, logResponse = false)
    public Result<StudyPlanDailyViewVO> dailyTasks(@RequestParam(required = false) Long planId,
                                                   @RequestParam(required = false) String date) {
        if (planId != null) {
            return Result.success(studyPlanService.dailyView(planId, date));
        }
        StudyPlanQueryDTO query = new StudyPlanQueryDTO();
        query.setPageNo(1L);
        query.setPageSize(1L);
        PageResult<StudyPlanListVO> page = studyPlanService.list(query);
        if (page.getRecords() == null || page.getRecords().isEmpty()) {
            return Result.success(emptyDailyView(date));
        }
        return Result.success(studyPlanService.dailyView(page.getRecords().get(0).getId(), date));
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

    private StudyPlanDailyViewVO emptyDailyView(String date) {
        StudyPlanDailyViewVO vo = new StudyPlanDailyViewVO();
        vo.setDate(parseDailyViewDate(date));
        vo.setDayIndex(0);
        vo.setTotalTaskCount(0);
        vo.setPendingTaskCount(0);
        vo.setCompletedTaskCount(0);
        vo.setSkippedTaskCount(0);
        vo.setCompletionRate(0);
        vo.setTasks(Collections.emptyList());
        return vo;
    }

    private LocalDate parseDailyViewDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日期格式不正确，请使用 yyyy-MM-dd");
        }
    }
}
