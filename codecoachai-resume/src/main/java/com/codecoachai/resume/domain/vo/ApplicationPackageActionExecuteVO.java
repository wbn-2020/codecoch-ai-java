package com.codecoachai.resume.domain.vo;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

@Data
public class ApplicationPackageActionExecuteVO {

    private Long packageId;
    private String actionCode;
    private String actionType;
    private String status;
    private String message;
    private String actionUrl;
    private String relatedBizType;
    private Long relatedBizId;
    private Map<String, Object> payload = new LinkedHashMap<>();
    private JobApplicationPackageVO packageDetail;
}
