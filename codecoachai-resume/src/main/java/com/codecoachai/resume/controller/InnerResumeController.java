package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.vo.InnerResumeDetailVO;
import com.codecoachai.resume.domain.vo.InnerResumeOptimizeRecordVO;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.service.ResumeService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/resumes")
public class InnerResumeController {

    private final ResumeService resumeService;
    private final ResumeMapper resumeMapper;

    @GetMapping("/{id}")
    public Result<InnerResumeDetailVO> getResume(@PathVariable Long id) {
        return Result.success(resumeService.getInnerResume(id));
    }

    @GetMapping("/{id}/projects")
    public Result<List<ResumeProjectVO>> getProjects(@PathVariable Long id) {
        return Result.success(resumeService.getInnerResume(id).getProjects());
    }

    @GetMapping("/default")
    public Result<InnerResumeDetailVO> getDefaultResume() {
        return Result.success(resumeService.getDefaultInnerResume());
    }

    @GetMapping("/optimize-records/{recordId}")
    public Result<InnerResumeOptimizeRecordVO> getOptimizeRecord(@PathVariable Long recordId) {
        return Result.success(resumeService.getInnerOptimizeRecord(recordId));
    }

    @GetMapping("/{id}/search-doc")
    public Result<Map<String, Object>> getSearchDoc(@PathVariable Long id) {
        Resume r = resumeMapper.selectById(id);
        if (r == null) {
            return Result.success(null);
        }
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", r.getId());
        doc.put("userId", r.getUserId());
        doc.put("title", r.getTitle());
        doc.put("realName", r.getRealName());
        doc.put("targetPosition", r.getTargetPosition());
        doc.put("skillStack", r.getSkillStack());
        doc.put("workExperience", r.getWorkExperience());
        doc.put("educationExperience", r.getEducationExperience());
        doc.put("summary", r.getSummary());
        doc.put("status", r.getStatus());
        return Result.success(doc);
    }
}
