package com.codecoachai.resume.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ResumeVersionDiffVO {
    private Long resumeId;
    private Long versionId;
    private Long sourceVersionId;
    private Long targetVersionId;
    private String sourceLabel;
    private String targetLabel;
    private List<FieldDiff> fields = new ArrayList<>();

    @Data
    public static class FieldDiff {
        private String field;
        private Object currentValue;
        private Object versionValue;
        private Object sourceValue;
        private Object targetValue;
        private Boolean changed;
    }
}
