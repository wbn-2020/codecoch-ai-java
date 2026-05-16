package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateDTO;
import com.codecoachai.interview.domain.dto.StudyPlanQueryDTO;
import com.codecoachai.interview.domain.dto.StudyTaskStatusUpdateDTO;
import com.codecoachai.interview.domain.vo.StudyPlanDetailVO;
import com.codecoachai.interview.domain.vo.StudyPlanGenerateVO;
import com.codecoachai.interview.domain.vo.StudyPlanListVO;
import com.codecoachai.interview.domain.vo.StudyTaskVO;
import com.codecoachai.interview.service.StudyPlanService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    @PostMapping("/study-plans/generate")
    public Result<StudyPlanGenerateVO> generate(@Valid @RequestBody StudyPlanGenerateDTO dto) {
        return Result.success(studyPlanService.generate(dto));
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

    @PostMapping("/study-plans/{id}/regenerate")
    public Result<StudyPlanGenerateVO> regenerate(@PathVariable Long id) {
        return Result.success(studyPlanService.regenerate(id));
    }

    @PostMapping("/study-tasks/{taskId}/status")
    public Result<StudyTaskVO> updateTaskStatus(@PathVariable Long taskId,
                                                @Valid @RequestBody StudyTaskStatusUpdateDTO dto) {
        return Result.success(studyPlanService.updateTaskStatus(taskId, dto));
    }
}
