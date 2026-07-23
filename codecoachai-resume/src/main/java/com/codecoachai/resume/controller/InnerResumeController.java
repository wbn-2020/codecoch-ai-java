package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.vo.InnerResumeDetailVO;
import com.codecoachai.resume.domain.vo.InnerResumeOptimizeRecordVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeRecordAgentEvidenceVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeSubmitVO;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import com.codecoachai.resume.domain.vo.ResumeSearchReindexVO;
import com.codecoachai.resume.service.ResumeService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/resumes")
public class InnerResumeController {

    private final ResumeService resumeService;

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

    @GetMapping("/users/{userId}/optimize-records/{recordId}/agent-evidence")
    public Result<ResumeOptimizeRecordAgentEvidenceVO> getOptimizeRecordEvidence(@PathVariable Long userId,
                                                                                 @PathVariable Long recordId) {
        return Result.success(resumeService.getOptimizeRecordEvidence(userId, recordId));
    }

    @PostMapping("/optimize-records/{recordId}/execute")
    public Result<ResumeOptimizeSubmitVO> executeOptimizeRecord(@PathVariable Long recordId) {
        return Result.success(resumeService.executeOptimizeRecord(recordId));
    }

    @GetMapping("/{id}/search-doc")
    public Result<Map<String, Object>> getSearchDoc(@PathVariable Long id) {
        return Result.success(resumeService.getSearchDocument(id));
    }

    @PostMapping("/search-docs/reindex")
    public Result<ResumeSearchReindexVO> reindexSearchDocs(@RequestParam(required = false) Long afterId,
                                                           @RequestParam(required = false) Integer batchSize) {
        return Result.success(resumeService.reindexSearchDocuments(afterId, batchSize));
    }
}
