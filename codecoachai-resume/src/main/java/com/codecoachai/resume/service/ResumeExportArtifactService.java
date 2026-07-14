package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.dto.ResumeArtifactPackageCreateDTO;
import com.codecoachai.resume.domain.dto.ResumeExportCreateDTO;
import com.codecoachai.resume.domain.vo.ResumeArtifactVO;
import com.codecoachai.resume.domain.vo.ResumeAtsTemplateVO;
import com.codecoachai.resume.domain.vo.ResumeExportVO;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public interface ResumeExportArtifactService {
    List<ResumeAtsTemplateVO> listTemplates();

    ResumeExportVO createExport(ResumeExportCreateDTO dto);

    ResumeArtifactVO createPackageArtifact(ResumeArtifactPackageCreateDTO dto);

    ResumeArtifactVO artifact(Long artifactId);

    List<ResumeArtifactVO> listArtifacts(Long resumeVersionId);

    ResponseEntity<StreamingResponseBody> download(Long artifactId);
}
