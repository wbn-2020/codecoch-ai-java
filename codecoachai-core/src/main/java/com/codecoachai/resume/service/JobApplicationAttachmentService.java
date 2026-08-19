package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.vo.JobApplicationAttachmentVO;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface JobApplicationAttachmentService {

    JobApplicationAttachmentVO upload(
            Long packageId, MultipartFile file, String attachmentType, String displayName);

    List<JobApplicationAttachmentVO> list(Long packageId);

    JobApplicationAttachmentVO downloadMetadata(Long packageId, Long attachmentId);

    ResponseEntity<Resource> download(Long packageId, Long attachmentId);

    JobApplicationAttachmentVO replace(
            Long packageId, Long attachmentId, MultipartFile file,
            String attachmentType, String displayName);

    void delete(Long packageId, Long attachmentId);

    JobApplicationAttachmentVO uploadForApplication(
            Long applicationId, MultipartFile file, String attachmentType, String displayName);

    List<JobApplicationAttachmentVO> listForApplication(Long applicationId);

    ResponseEntity<Resource> downloadForApplication(Long applicationId, Long attachmentId);

    JobApplicationAttachmentVO replaceForApplication(
            Long applicationId, Long attachmentId, MultipartFile file,
            String attachmentType, String displayName);

    void deleteForApplication(Long applicationId, Long attachmentId);
}
