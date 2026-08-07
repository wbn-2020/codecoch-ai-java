package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.dto.JobDescriptionParseDTO;
import com.codecoachai.resume.domain.dto.TargetJobQueryDTO;
import com.codecoachai.resume.domain.dto.TargetJobSaveDTO;
import com.codecoachai.resume.domain.vo.JobDescriptionAnalysisVO;
import com.codecoachai.resume.domain.vo.TargetJobVO;
import com.codecoachai.resume.service.TargetJobService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/job-targets")
public class TargetJobController {

    private final TargetJobService targetJobService;

    @GetMapping
    public Result<List<TargetJobVO>> list(@ModelAttribute TargetJobQueryDTO query) {
        return Result.success(targetJobService.listTargetJobs(query));
    }

    @PostMapping
    public Result<TargetJobVO> create(@Valid @RequestBody TargetJobSaveDTO dto) {
        return Result.success(targetJobService.createTargetJob(dto));
    }

    @GetMapping("/current")
    public Result<TargetJobVO> current() {
        return Result.success(targetJobService.getCurrent());
    }

    @GetMapping("/{id}")
    public Result<TargetJobVO> detail(@PathVariable Long id) {
        return Result.success(targetJobService.getTargetJob(id));
    }

    @PutMapping("/{id}")
    public Result<TargetJobVO> update(@PathVariable Long id, @Valid @RequestBody TargetJobSaveDTO dto) {
        return Result.success(targetJobService.updateTargetJob(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        targetJobService.deleteTargetJob(id);
        return Result.success();
    }

    @PostMapping("/{id}/set-current")
    public Result<TargetJobVO> setCurrent(@PathVariable Long id) {
        return Result.success(targetJobService.setCurrent(id));
    }

    @PostMapping("/{id}/parse")
    public Result<JobDescriptionAnalysisVO> parse(@PathVariable Long id,
                                                  @RequestBody(required = false) JobDescriptionParseDTO dto) {
        return Result.success(targetJobService.parseJobDescription(id, dto));
    }

    @PostMapping("/{id}/parse-task")
    public Result<JobDescriptionAnalysisVO> parseTask(@PathVariable Long id,
                                                      @RequestBody(required = false) JobDescriptionParseDTO dto) {
        return Result.success(targetJobService.submitJobDescriptionParse(id, dto));
    }

    @GetMapping("/{id}/analysis")
    public Result<JobDescriptionAnalysisVO> analysis(@PathVariable Long id) {
        return Result.success(targetJobService.getAnalysis(id));
    }
}
