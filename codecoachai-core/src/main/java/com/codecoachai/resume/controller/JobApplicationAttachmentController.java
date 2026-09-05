package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.vo.JobApplicationAttachmentVO;
import com.codecoachai.resume.service.JobApplicationAttachmentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class JobApplicationAttachmentController {

    private final JobApplicationAttachmentService attachmentService;

    @PostMapping(
            value = "/application-packages/{packageId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<JobApplicationAttachmentVO> upload(
            @PathVariable Long packageId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String attachmentType,
            @RequestParam(required = false) String displayName) {
        SecurityAssert.requireLoginUserId();
        return Result.success(attachmentService.upload(
                packageId, file, attachmentType, displayName));
    }

    @GetMapping("/application-packages/{packageId}/attachments")
    public Result<List<JobApplicationAttachmentVO>> list(@PathVariable Long packageId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(attachmentService.list(packageId));
    }

    @GetMapping("/application-packages/{packageId}/attachments/{attachmentId}/download-metadata")
    public Result<JobApplicationAttachmentVO> downloadMetadata(
            @PathVariable Long packageId,
            @PathVariable Long attachmentId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(attachmentService.downloadMetadata(packageId, attachmentId));
    }

    @GetMapping("/application-packages/{packageId}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long packageId,
            @PathVariable Long attachmentId) {
        SecurityAssert.requireLoginUserId();
        return attachmentService.download(packageId, attachmentId);
    }

    @PutMapping(
            value = "/application-packages/{packageId}/attachments/{attachmentId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<JobApplicationAttachmentVO> replace(
            @PathVariable Long packageId,
            @PathVariable Long attachmentId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String attachmentType,
            @RequestParam(required = false) String displayName) {
        SecurityAssert.requireLoginUserId();
        return Result.success(attachmentService.replace(
                packageId, attachmentId, file, attachmentType, displayName));
    }

    @DeleteMapping("/application-packages/{packageId}/attachments/{attachmentId}")
    public Result<Void> delete(
            @PathVariable Long packageId,
            @PathVariable Long attachmentId) {
        SecurityAssert.requireLoginUserId();
        attachmentService.delete(packageId, attachmentId);
        return Result.success();
    }

    @PostMapping(
            value = "/applications/{applicationId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<JobApplicationAttachmentVO> uploadForApplication(
            @PathVariable Long applicationId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String attachmentType,
            @RequestParam(required = false) String displayName) {
        SecurityAssert.requireLoginUserId();
        return Result.success(attachmentService.uploadForApplication(
                applicationId, file, attachmentType, displayName));
    }

    @GetMapping("/applications/{applicationId}/attachments")
    public Result<List<JobApplicationAttachmentVO>> listForApplication(
            @PathVariable Long applicationId) {
        SecurityAssert.requireLoginUserId();
        return Result.success(attachmentService.listForApplication(applicationId));
    }

    @GetMapping("/applications/{applicationId}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadForApplication(
            @PathVariable Long applicationId,
            @PathVariable Long attachmentId) {
        SecurityAssert.requireLoginUserId();
        return attachmentService.downloadForApplication(applicationId, attachmentId);
    }

    @PutMapping(
            value = "/applications/{applicationId}/attachments/{attachmentId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<JobApplicationAttachmentVO> replaceForApplication(
            @PathVariable Long applicationId,
            @PathVariable Long attachmentId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String attachmentType,
            @RequestParam(required = false) String displayName) {
        SecurityAssert.requireLoginUserId();
        return Result.success(attachmentService.replaceForApplication(
                applicationId, attachmentId, file, attachmentType, displayName));
    }

    @DeleteMapping("/applications/{applicationId}/attachments/{attachmentId}")
    public Result<Void> deleteForApplication(
            @PathVariable Long applicationId,
            @PathVariable Long attachmentId) {
        SecurityAssert.requireLoginUserId();
        attachmentService.deleteForApplication(applicationId, attachmentId);
        return Result.success();
    }
}
