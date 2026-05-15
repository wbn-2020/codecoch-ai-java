package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.dto.ResumeProjectSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeSaveDTO;
import com.codecoachai.resume.domain.vo.InnerResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeListVO;
import com.codecoachai.resume.domain.vo.ResumeParseStatusVO;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import com.codecoachai.resume.domain.vo.ResumeUploadVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ResumeService {

    List<ResumeListVO> listResumes();

    ResumeDetailVO createResume(ResumeSaveDTO dto);

    ResumeUploadVO uploadResume(MultipartFile file);

    ResumeParseStatusVO getParseStatus(Long analysisRecordId);

    ResumeParseStatusVO reparse(Long analysisRecordId);

    ResumeDetailVO getResume(Long id);

    ResumeDetailVO updateResume(Long id, ResumeSaveDTO dto);

    void deleteResume(Long id);

    ResumeDetailVO setDefault(Long id);

    ResumeProjectVO createProject(Long resumeId, ResumeProjectSaveDTO dto);

    ResumeProjectVO updateProject(Long resumeId, Long projectId, ResumeProjectSaveDTO dto);

    void deleteProject(Long resumeId, Long projectId);

    InnerResumeDetailVO getInnerResume(Long id);

    InnerResumeDetailVO getDefaultInnerResume();
}
