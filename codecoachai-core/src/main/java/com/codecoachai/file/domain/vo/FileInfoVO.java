package com.codecoachai.file.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "File metadata with optional resume analysis status")
public class FileInfoVO {

    @Schema(description = "File id")
    private Long id;

    @Schema(description = "Owner user id")
    private Long userId;

    @Schema(description = "File business type, for example RESUME")
    private String bizType;

    @Schema(description = "Alias of bizType for frontend business display compatibility")
    private String businessType;

    @Schema(description = "Related business object id, currently resume id when a resume analysis is confirmed")
    private Long businessId;

    @Schema(description = "Original filename")
    private String originalFilename;

    @Schema(description = "Stored filename")
    private String storedFilename;

    @Schema(description = "File extension")
    private String fileExt;

    @Schema(description = "MIME type")
    private String mimeType;

    @Schema(description = "File size in bytes")
    private Long fileSize;

    @Schema(description = "Storage provider")
    private String storageProvider;

    @Schema(description = "File status")
    private String status;

    @Schema(description = "Resume id linked by the latest resume analysis record, null when not confirmed or not a resume file")
    private Long resumeId;

    @Schema(description = "Latest resume_analysis_record id linked by this file, null when no analysis record exists")
    private Long resumeAnalysisRecordId;

    @Schema(description = "Latest resume parse status, for example PENDING, PARSING, WAIT_CONFIRM, SUCCESS, FAILED")
    private String parseStatus;

    @Schema(description = "Latest resume parse error message, null when no error exists")
    private String parseErrorMessage;

    @Schema(description = "Whether latest resume analysis record has been confirmed")
    private Boolean analysisConfirmed;

    @Schema(description = "Best-effort parse completion time from latest analysis record updatedAt, null before terminal parse status")
    private LocalDateTime parsedAt;

    @Schema(description = "Best-effort confirmation time from latest analysis record updatedAt, null before SUCCESS")
    private LocalDateTime confirmedAt;

    @Schema(description = "File metadata creation time")
    private LocalDateTime createdAt;

    @Schema(description = "File metadata update time")
    private LocalDateTime updatedAt;
}
