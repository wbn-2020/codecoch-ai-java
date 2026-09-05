package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.file.domain.vo.ResumeParseOperationVO;
import com.codecoachai.file.domain.vo.ResumeUploadDecisionVO;
import com.codecoachai.resume.domain.dto.ApplyResumeOptimizeResultDTO;
import com.codecoachai.resume.domain.dto.ResumeOptimizeRequestDTO;
import com.codecoachai.resume.domain.dto.ResumeProjectSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeSaveDTO;
import com.codecoachai.resume.domain.vo.ApplyResumeOptimizeResultVO;
import com.codecoachai.resume.domain.vo.ResumeAnalysisResultVO;
import com.codecoachai.resume.domain.vo.ResumeConfirmAnalysisVO;
import com.codecoachai.resume.domain.vo.ResumeDetailVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.codecoachai.resume.domain.vo.ResumeListVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeRecordVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeSubmitVO;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import com.codecoachai.resume.service.ResumeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    @GetMapping
    public Result<PageResult<ResumeListVO>> listResumes(@RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer size,
                                                        @RequestParam(required = false) String keyword) {
        return Result.success(resumeService.listResumes(page, size, keyword));
    }

    @OperationLog(module = "resume", action = "CREATE_RESUME", description = "Create resume", logResponse = false)
    @PostMapping
    public Result<ResumeDetailVO> createResume(@Valid @RequestBody ResumeSaveDTO dto) {
        return Result.success(resumeService.createResume(dto));
    }

    @OperationLog(module = "resume", action = "UPLOAD_RESUME", description = "Upload resume file", logArgs = false, logResponse = false)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResumeUploadDecisionVO> uploadResume(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String duplicateDecision) {
        return Result.success(resumeService.uploadResume(file, duplicateDecision));
    }

    /**
     * A2 stage: path variable id is resume_analysis_record.id, not the confirmed resume.id.
     */
    @GetMapping("/{id}/parse-status")
    public Result<ResumeParseOperationVO> getParseStatus(@PathVariable Long id) {
        return Result.success(resumeService.getParseStatus(id));
    }

    /**
     * A2 stage: path variable id is resume_analysis_record.id, not the confirmed resume.id.
     */
    @OperationLog(module = "resume", action = "REPARSE_RESUME", description = "Reparse resume", logResponse = false)
    @PostMapping("/{id}/reparse")
    public Result<ResumeParseOperationVO> reparse(@PathVariable Long id) {
        return Result.success(resumeService.reparse(id));
    }

    @OperationLog(module = "resume", action = "CANCEL_RESUME_PARSE", description = "Cancel resume parsing")
    @PostMapping("/{id}/cancel-parse")
    public Result<ResumeParseOperationVO> cancelParse(@PathVariable Long id) {
        return Result.success(resumeService.cancelParse(id));
    }

    /**
     * A4 stage: path variable id is resume_analysis_record.id, not the confirmed resume.id.
     */
    @GetMapping("/{id}/analysis-result")
    public Result<ResumeAnalysisResultVO> getAnalysisResult(@PathVariable Long id) {
        return Result.success(resumeService.getAnalysisResult(id));
    }

    /**
     * A4 stage: path variable id is resume_analysis_record.id, not the confirmed resume.id.
     */
    @OperationLog(module = "resume", action = "CONFIRM_RESUME_ANALYSIS", description = "Confirm resume analysis", logResponse = false)
    @PostMapping("/{id}/confirm-analysis")
    public Result<ResumeConfirmAnalysisVO> confirmAnalysis(@PathVariable Long id) {
        return Result.success(resumeService.confirmAnalysis(id));
    }

    /**
     * A5 stage: path variable id is the confirmed resume.id.
     */
    @OperationLog(module = "resume", action = "OPTIMIZE_RESUME", description = "Submit resume optimization", logResponse = false)
    @PostMapping("/{id}/optimize")
    public Result<ResumeOptimizeSubmitVO> optimizeResume(@PathVariable Long id,
                                                         @RequestBody(required = false) ResumeOptimizeRequestDTO dto) {
        return Result.success(resumeService.optimizeResume(id, dto));
    }

    /**
     * A5 stage: path variable id is the confirmed resume.id.
     */
    @GetMapping("/{id}/optimize-records")
    public Result<List<ResumeOptimizeRecordVO>> listOptimizeRecords(@PathVariable Long id) {
        return Result.success(resumeService.listOptimizeRecords(id));
    }

    /**
     * A5 stage: recordId is resume_optimize_record.id.
     */
    @GetMapping("/optimize-records/{recordId}")
    public Result<ResumeOptimizeDetailVO> getOptimizeRecordDetail(@PathVariable Long recordId) {
        return Result.success(resumeService.getOptimizeRecordDetail(recordId));
    }

    /**
     * P0-3-B: apply an AI optimization record by creating a new draft copy.
     */
    @OperationLog(module = "resume", action = "APPLY_RESUME_OPTIMIZE", description = "Apply resume optimization result", logResponse = false)
    @PostMapping("/optimize-records/{recordId}/apply")
    public Result<ApplyResumeOptimizeResultVO> applyOptimizeResult(
            @PathVariable Long recordId,
            @RequestBody(required = false) ApplyResumeOptimizeResultDTO dto) {
        return Result.success(resumeService.applyOptimizeResult(recordId, dto));
    }

    @GetMapping("/{id}")
    public Result<ResumeDetailVO> getResume(@PathVariable Long id) {
        return Result.success(resumeService.getResume(id));
    }

    @GetMapping("/{id}/document")
    public Result<JsonNode> getResumeDocument(@PathVariable Long id) {
        return Result.success(resumeService.getResumeDocument(id));
    }

    @OperationLog(module = "resume", action = "UPDATE_RESUME", description = "Update resume", logResponse = false)
    @PutMapping("/{id}")
    public Result<ResumeDetailVO> updateResume(@PathVariable Long id, @Valid @RequestBody ResumeSaveDTO dto) {
        return Result.success(resumeService.updateResume(id, dto));
    }

    @OperationLog(module = "resume", action = "DELETE_RESUME", description = "Delete resume", logResponse = false)
    @DeleteMapping("/{id}")
    public Result<Void> deleteResume(@PathVariable Long id) {
        resumeService.deleteResume(id);
        return Result.success();
    }

    @OperationLog(module = "resume", action = "SET_DEFAULT_RESUME", description = "Set default resume")
    @PutMapping("/{id}/default")
    public Result<ResumeDetailVO> setDefault(@PathVariable Long id) {
        return Result.success(resumeService.setDefault(id));
    }

    @OperationLog(module = "resume", action = "CLEAR_DEFAULT_RESUME", description = "Clear default resume")
    @DeleteMapping("/{id}/default")
    public Result<ResumeDetailVO> clearDefault(@PathVariable Long id) {
        return Result.success(resumeService.clearDefault(id));
    }

    @OperationLog(module = "resume", action = "CREATE_RESUME_PROJECT", description = "Create resume project", logResponse = false)
    @PostMapping("/{resumeId}/projects")
    public Result<ResumeProjectVO> createProject(@PathVariable Long resumeId,
                                                 @Valid @RequestBody ResumeProjectSaveDTO dto) {
        return Result.success(resumeService.createProject(resumeId, dto));
    }

    @OperationLog(module = "resume", action = "UPDATE_RESUME_PROJECT", description = "Update resume project", logResponse = false)
    @PutMapping("/{resumeId}/projects/{projectId}")
    public Result<ResumeProjectVO> updateProject(@PathVariable Long resumeId, @PathVariable Long projectId,
                                                 @Valid @RequestBody ResumeProjectSaveDTO dto) {
        return Result.success(resumeService.updateProject(resumeId, projectId, dto));
    }

    @OperationLog(module = "resume", action = "UPDATE_RESUME_PROJECT", description = "Update resume project", logResponse = false)
    @PutMapping("/projects/{projectId}")
    public Result<ResumeProjectVO> updateProjectByDocumentPath(@PathVariable Long projectId,
                                                               @Valid @RequestBody ResumeProjectSaveDTO dto) {
        return Result.success(resumeService.updateProject(projectId, dto));
    }

    @OperationLog(module = "resume", action = "DELETE_RESUME_PROJECT", description = "Delete resume project", logResponse = false)
    @DeleteMapping("/{resumeId}/projects/{projectId}")
    public Result<Void> deleteProject(@PathVariable Long resumeId, @PathVariable Long projectId) {
        resumeService.deleteProject(resumeId, projectId);
        return Result.success();
    }

    @OperationLog(module = "resume", action = "DELETE_RESUME_PROJECT", description = "Delete resume project", logResponse = false)
    @DeleteMapping("/projects/{projectId}")
    public Result<Void> deleteProjectByDocumentPath(@PathVariable Long projectId) {
        resumeService.deleteProject(projectId);
        return Result.success();
    }
}
