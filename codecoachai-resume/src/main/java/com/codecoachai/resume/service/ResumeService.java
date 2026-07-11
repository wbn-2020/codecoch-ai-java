package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.dto.ApplyResumeOptimizeResultDTO;
import com.codecoachai.resume.domain.dto.ResumeOptimizeRequestDTO;
import com.codecoachai.resume.domain.dto.ResumeProjectSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeSaveDTO;
import com.codecoachai.resume.domain.vo.ApplyResumeOptimizeResultVO;
import com.codecoachai.resume.domain.vo.InnerResumeDetailVO;
import com.codecoachai.resume.domain.vo.InnerResumeOptimizeRecordVO;
import com.codecoachai.resume.domain.vo.ResumeAnalysisResultVO;
import com.codecoachai.resume.domain.vo.ResumeConfirmAnalysisVO;
import com.codecoachai.resume.domain.vo.ResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeListVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeRecordVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeRecordAgentEvidenceVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeSubmitVO;
import com.codecoachai.resume.domain.vo.ResumeParseStatusVO;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import com.codecoachai.resume.domain.vo.ResumeSearchReindexVO;
import com.codecoachai.resume.domain.vo.ResumeUploadVO;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface ResumeService {

    List<ResumeListVO> listResumes();

    List<ResumeListVO> listResumes(Integer page, Integer size, String keyword);

    ResumeDetailVO createResume(ResumeSaveDTO dto);

    ResumeUploadVO uploadResume(MultipartFile file);

    ResumeParseStatusVO getParseStatus(Long analysisRecordId);

    ResumeParseStatusVO reparse(Long analysisRecordId);

    ResumeAnalysisResultVO getAnalysisResult(Long analysisRecordId);

    ResumeConfirmAnalysisVO confirmAnalysis(Long analysisRecordId);

    ResumeOptimizeSubmitVO optimizeResume(Long resumeId, ResumeOptimizeRequestDTO dto);

    ResumeOptimizeSubmitVO executeOptimizeRecord(Long recordId);

    List<ResumeOptimizeRecordVO> listOptimizeRecords(Long resumeId);

    ResumeOptimizeDetailVO getOptimizeRecordDetail(Long recordId);

    ApplyResumeOptimizeResultVO applyOptimizeResult(Long recordId, ApplyResumeOptimizeResultDTO dto);

    ResumeDetailVO getResume(Long id);

    ResumeDetailVO updateResume(Long id, ResumeSaveDTO dto);

    void deleteResume(Long id);

    ResumeDetailVO setDefault(Long id);

    ResumeProjectVO createProject(Long resumeId, ResumeProjectSaveDTO dto);

    ResumeProjectVO updateProject(Long resumeId, Long projectId, ResumeProjectSaveDTO dto);

    ResumeProjectVO updateProject(Long projectId, ResumeProjectSaveDTO dto);

    void deleteProject(Long resumeId, Long projectId);

    void deleteProject(Long projectId);

    InnerResumeDetailVO getInnerResume(Long id);

    InnerResumeDetailVO getDefaultInnerResume();

    InnerResumeOptimizeRecordVO getInnerOptimizeRecord(Long recordId);

    ResumeOptimizeRecordAgentEvidenceVO getOptimizeRecordEvidence(Long userId, Long recordId);

    Map<String, Object> getSearchDocument(Long id);

    ResumeSearchReindexVO reindexSearchDocuments(Long afterId, Integer batchSize);
}
