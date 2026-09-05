package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ResumeAtsTemplateVO {
    private Long id;
    private String templateCode;
    private Integer templateVersion;
    private String templateName;
    private String layoutType;
    private JsonNode definition;
    private String definitionHash;
    private String status;
}
