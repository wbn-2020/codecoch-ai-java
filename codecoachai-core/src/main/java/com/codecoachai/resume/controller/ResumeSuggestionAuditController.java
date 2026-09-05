package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.resume.domain.dto.ResumeClaimAuditCreateDTO;
import com.codecoachai.resume.domain.dto.ResumeSuggestionCreateDTO;
import com.codecoachai.resume.domain.dto.ResumeSuggestionBatchAcceptDTO;
import com.codecoachai.resume.domain.dto.ResumeSuggestionDecisionDTO;
import com.codecoachai.resume.domain.vo.ResumeClaimAuditVO;
import com.codecoachai.resume.domain.vo.ResumeSuggestionVO;
import com.codecoachai.resume.service.ResumeClaimAuditService;
import com.codecoachai.resume.service.ResumeSuggestionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class ResumeSuggestionAuditController {

    private final ResumeSuggestionService suggestionService;
    private final ResumeClaimAuditService claimAuditService;

    @OperationLog(module = "resume", action = "CREATE_RESUME_SUGGESTION", description = "Create anchored resume suggestion", logResponse = false)
    @PostMapping("/resume-suggestions")
    public Result<ResumeSuggestionVO> createSuggestion(@Valid @RequestBody ResumeSuggestionCreateDTO dto) {
        return Result.success(suggestionService.create(dto));
    }

    @GetMapping("/resume-suggestions")
    public Result<List<ResumeSuggestionVO>> listSuggestions(@RequestParam(required = false) Long resumeId,
                                                            @RequestParam(required = false) String status) {
        return Result.success(suggestionService.list(resumeId, status));
    }

    @GetMapping("/resume-suggestions/{id}")
    public Result<ResumeSuggestionVO> suggestion(@PathVariable Long id) {
        return Result.success(suggestionService.detail(id));
    }

    @OperationLog(module = "resume", action = "DECIDE_RESUME_SUGGESTION", description = "Accept, reject, or undo resume suggestion", logResponse = false)
    @PostMapping("/resume-suggestions/{id}/decisions")
    public Result<ResumeSuggestionVO> decideSuggestion(@PathVariable Long id,
                                                       @Valid @RequestBody ResumeSuggestionDecisionDTO dto) {
        return Result.success(suggestionService.decide(id, dto));
    }

    @OperationLog(module = "resume", action = "BATCH_ACCEPT_RESUME_SUGGESTIONS",
            description = "Accept low-risk anchored resume suggestions in one version", logResponse = false)
    @PostMapping("/resume-suggestions/batch-accept")
    public Result<List<ResumeSuggestionVO>> batchAccept(
            @Valid @RequestBody ResumeSuggestionBatchAcceptDTO dto) {
        return Result.success(suggestionService.acceptLowRiskBatch(dto));
    }

    @OperationLog(module = "resume", action = "AUDIT_RESUME_CLAIMS", description = "Audit resume factual and quantified claims", logResponse = false)
    @PostMapping("/resume-claim-audits")
    public Result<ResumeClaimAuditVO> audit(@Valid @RequestBody ResumeClaimAuditCreateDTO dto) {
        return Result.success(claimAuditService.audit(dto.getResumeVersionId()));
    }

    @GetMapping("/resume-claim-audits")
    public Result<List<ResumeClaimAuditVO>> listAudits(@RequestParam(required = false) Long resumeId) {
        return Result.success(claimAuditService.list(resumeId));
    }

    @GetMapping("/resume-claim-audits/{id}")
    public Result<ResumeClaimAuditVO> auditDetail(@PathVariable Long id) {
        return Result.success(claimAuditService.detail(id));
    }
}
