package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.resume.domain.dto.ProjectEvidenceFromResumeProjectDTO;
import com.codecoachai.resume.domain.dto.ProjectEvidenceQueryDTO;
import com.codecoachai.resume.domain.dto.ProjectEvidenceSaveDTO;
import com.codecoachai.resume.domain.dto.ProjectJdCoverageRequestDTO;
import com.codecoachai.resume.domain.dto.ProjectSkillEvidenceSaveDTO;
import com.codecoachai.resume.domain.dto.ProjectStoryGenerateDTO;
import com.codecoachai.resume.domain.dto.ProjectStoryGenerationQueryDTO;
import com.codecoachai.resume.domain.vo.ProjectEvidenceDetailVO;
import com.codecoachai.resume.domain.vo.ProjectEvidenceListVO;
import com.codecoachai.resume.domain.vo.ProjectJdCoverageVO;
import com.codecoachai.resume.domain.vo.ProjectSkillEvidenceVO;
import com.codecoachai.resume.domain.vo.ProjectStoryGenerationVO;
import com.codecoachai.resume.service.ProjectEvidenceMaterialService;
import com.codecoachai.resume.service.ProjectEvidenceService;
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
@RequestMapping("/project-evidence")
public class ProjectEvidenceController {

    private final ProjectEvidenceService projectEvidenceService;
    private final ProjectEvidenceMaterialService projectEvidenceMaterialService;

    @GetMapping
    public Result<PageResult<ProjectEvidenceListVO>> list(@ModelAttribute ProjectEvidenceQueryDTO query) {
        return Result.success(projectEvidenceService.list(query));
    }

    @OperationLog(module = "project-evidence", action = "CREATE_PROJECT_EVIDENCE", description = "Create project evidence", logResponse = false)
    @PostMapping
    public Result<ProjectEvidenceDetailVO> create(@Valid @RequestBody ProjectEvidenceSaveDTO dto) {
        return Result.success(projectEvidenceService.create(dto));
    }

    @OperationLog(module = "project-evidence", action = "IMPORT_PROJECT_EVIDENCE", description = "Import project evidence from resume project", logResponse = false)
    @PostMapping("/from-resume-project")
    public Result<ProjectEvidenceDetailVO> importFromResumeProject(@Valid @RequestBody ProjectEvidenceFromResumeProjectDTO dto) {
        return Result.success(projectEvidenceService.importFromResumeProject(dto));
    }

    @GetMapping("/{id}")
    public Result<ProjectEvidenceDetailVO> detail(@PathVariable Long id) {
        return Result.success(projectEvidenceService.detail(id));
    }

    @OperationLog(module = "project-evidence", action = "UPDATE_PROJECT_EVIDENCE", description = "Update project evidence", logResponse = false)
    @PutMapping("/{id}")
    public Result<ProjectEvidenceDetailVO> update(@PathVariable Long id, @Valid @RequestBody ProjectEvidenceSaveDTO dto) {
        return Result.success(projectEvidenceService.update(id, dto));
    }

    @OperationLog(module = "project-evidence", action = "DELETE_PROJECT_EVIDENCE", description = "Delete project evidence", logResponse = false)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectEvidenceService.delete(id);
        return Result.success();
    }

    @OperationLog(module = "project-evidence", action = "CREATE_PROJECT_SKILL_EVIDENCE", description = "Create project skill evidence", logResponse = false)
    @PostMapping("/{id}/skill-evidences")
    public Result<ProjectSkillEvidenceVO> addSkillEvidence(@PathVariable Long id,
                                                           @Valid @RequestBody ProjectSkillEvidenceSaveDTO dto) {
        return Result.success(projectEvidenceService.addSkillEvidence(id, dto));
    }

    @OperationLog(module = "project-evidence", action = "UPDATE_PROJECT_SKILL_EVIDENCE", description = "Update project skill evidence", logResponse = false)
    @PutMapping("/{id}/skill-evidences/{evidenceId}")
    public Result<ProjectSkillEvidenceVO> updateSkillEvidence(@PathVariable Long id,
                                                              @PathVariable Long evidenceId,
                                                              @Valid @RequestBody ProjectSkillEvidenceSaveDTO dto) {
        return Result.success(projectEvidenceService.updateSkillEvidence(id, evidenceId, dto));
    }

    @OperationLog(module = "project-evidence", action = "DELETE_PROJECT_SKILL_EVIDENCE", description = "Delete project skill evidence", logResponse = false)
    @DeleteMapping("/{id}/skill-evidences/{evidenceId}")
    public Result<Void> deleteSkillEvidence(@PathVariable Long id, @PathVariable Long evidenceId) {
        projectEvidenceService.deleteSkillEvidence(id, evidenceId);
        return Result.success();
    }

    @OperationLog(module = "project-evidence", action = "GENERATE_PROJECT_STORY", description = "Generate project interview material", logResponse = false)
    @PostMapping("/{id}/generations")
    public Result<ProjectStoryGenerationVO> generateStory(@PathVariable Long id,
                                                          @Valid @RequestBody ProjectStoryGenerateDTO dto) {
        return Result.success(projectEvidenceMaterialService.generate(id, dto));
    }

    @GetMapping("/{id}/generations")
    public Result<List<ProjectStoryGenerationVO>> listGenerations(@PathVariable Long id,
                                                                  @ModelAttribute ProjectStoryGenerationQueryDTO query) {
        return Result.success(projectEvidenceMaterialService.listGenerations(id, query));
    }

    @OperationLog(module = "project-evidence", action = "ACCEPT_PROJECT_STORY", description = "Accept generated project material", logResponse = false)
    @PostMapping("/{id}/generations/{generationId}/accept")
    public Result<ProjectStoryGenerationVO> acceptGeneration(@PathVariable Long id, @PathVariable Long generationId) {
        return Result.success(projectEvidenceMaterialService.accept(id, generationId));
    }

    @OperationLog(module = "project-evidence", action = "ANALYZE_PROJECT_JD_COVERAGE", description = "Analyze project evidence JD coverage", logResponse = false)
    @PostMapping("/{id}/jd-coverage")
    public Result<ProjectJdCoverageVO> analyzeJdCoverage(@PathVariable Long id,
                                                         @RequestBody(required = false) ProjectJdCoverageRequestDTO dto) {
        return Result.success(projectEvidenceMaterialService.analyzeJdCoverage(id, dto));
    }
}
