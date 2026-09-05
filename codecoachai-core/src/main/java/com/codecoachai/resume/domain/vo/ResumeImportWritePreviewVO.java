package com.codecoachai.resume.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeImportWritePreviewVO {

    private String fieldKey;
    private String label;
    private String value;
    private String status;
}
