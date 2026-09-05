package com.codecoachai.file.domain.vo;

import com.codecoachai.resume.domain.vo.ResumeUploadVO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ResumeUploadDecisionVO extends ResumeUploadVO {

    private String contentSha256;
    private Boolean duplicate;
    private Boolean decisionRequired;
    private String requestedDecision;
    private String recommendedDecision;
    private String appliedDecision;
    private List<String> allowedDecisions;
    private String operationStatus;
    private Boolean cancellable;
    private Boolean retryable;
}
