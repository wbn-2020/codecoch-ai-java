package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.resume.domain.dto.ResumeArtifactPackageCreateDTO;
import com.codecoachai.resume.domain.dto.ResumeExportCreateDTO;
import com.codecoachai.resume.domain.vo.ResumeArtifactVO;
import com.codecoachai.resume.domain.vo.ResumeAtsTemplateVO;
import com.codecoachai.resume.domain.vo.ResumeExportVO;
import com.codecoachai.resume.service.ResumeExportArtifactService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequiredArgsConstructor
public class ResumeExportArtifactController {

    private final ResumeExportArtifactService exportArtifactService;

    @GetMapping("/resume-ats-templates")
    public Result<List<ResumeAtsTemplateVO>> templates() {
        return Result.success(exportArtifactService.listTemplates());
    }

    @OperationLog(module = "resume", action = "EXPORT_RESUME", description = "Generate formal ATS resume export", logResponse = false)
    @PostMapping("/resume-exports")
    public Result<ResumeExportVO> export(@Valid @RequestBody ResumeExportCreateDTO dto) {
        return Result.success(exportArtifactService.createExport(dto));
    }

    @OperationLog(module = "resume", action = "CREATE_APPLICATION_ARTIFACT", description = "Generate application package ZIP artifact", logResponse = false)
    @PostMapping("/resume-artifacts/application-packages")
    public Result<ResumeArtifactVO> createPackage(@Valid @RequestBody ResumeArtifactPackageCreateDTO dto) {
        return Result.success(exportArtifactService.createPackageArtifact(dto));
    }

    @GetMapping("/resume-artifacts")
    public Result<List<ResumeArtifactVO>> artifacts(@RequestParam(required = false) Long resumeVersionId) {
        return Result.success(exportArtifactService.listArtifacts(resumeVersionId));
    }

    @GetMapping("/resume-artifacts/{id}")
    public Result<ResumeArtifactVO> artifact(@PathVariable Long id) {
        return Result.success(exportArtifactService.artifact(id));
    }

    @GetMapping("/resume-artifacts/{id}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long id) {
        return exportArtifactService.download(id);
    }
}
