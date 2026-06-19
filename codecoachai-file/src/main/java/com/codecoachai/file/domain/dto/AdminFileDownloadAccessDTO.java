package com.codecoachai.file.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class AdminFileDownloadAccessDTO {

    @Size(max = 300, message = "Access reason cannot exceed 300 characters.")
    private String accessReason;

    @AssertTrue(message = "Please confirm this sensitive file access before downloading.")
    private boolean confirmSensitiveAccess;

    private Boolean confirm;

    private Boolean dryRun;

    @Size(max = 300, message = "Reason cannot exceed 300 characters.")
    private String reason;

    @Pattern(regexp = "[A-Za-z0-9:_-]{8,128}",
            message = "idempotencyKey must be 8-128 letters, digits, colon, underscore, or dash.")
    private String idempotencyKey;

    public String effectiveReason() {
        return StringUtils.hasText(reason) ? reason : accessReason;
    }
}
