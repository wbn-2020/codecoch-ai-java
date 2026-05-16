package com.codecoachai.ai.domain.vo;

import java.util.Map;
import lombok.Data;

@Data
public class PromptVersionTestVO {

    private Long versionId;
    private Long templateId;
    private String scene;
    private String versionCode;
    private String renderedPrompt;
    private Map<String, String> inputVariables;
    private String aiResponse;
    private Long aiCallLogId;
    private Boolean mockMode;
}
