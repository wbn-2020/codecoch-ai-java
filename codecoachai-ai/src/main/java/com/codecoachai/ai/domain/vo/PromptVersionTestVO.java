package com.codecoachai.ai.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class PromptVersionTestVO {

    private Long versionId;
    private Long templateId;
    private String scene;
    private String versionCode;
    private Integer renderedPromptLength;
    private String renderedPromptHash;
    private Integer inputVariableCount;
    private List<String> inputVariableKeys;
    private String inputVariablesHash;
    private Integer aiResponseLength;
    private String aiResponseHash;
    private Long aiCallLogId;
    private Boolean mockMode;
    private Boolean rawFieldsAvailable;
    private Boolean rawFieldsIncluded;
    private String rawAccessPermission;
}
