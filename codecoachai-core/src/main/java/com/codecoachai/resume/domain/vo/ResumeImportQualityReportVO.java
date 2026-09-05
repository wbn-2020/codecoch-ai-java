package com.codecoachai.resume.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ResumeImportQualityReportVO {

    private String schemaVersion;
    private String policyVersion;
    private String validationStatus;
    private boolean confirmable;
    private int duplicateProjectsRemoved;
    private List<String> blockers = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> missingContacts = new ArrayList<>();
    private List<ResumeImportWritePreviewVO> writePreview = new ArrayList<>();
}
