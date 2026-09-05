package com.codecoachai.file.domain.vo;

import com.codecoachai.resume.domain.vo.ResumeParseStatusVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ResumeParseOperationVO extends ResumeParseStatusVO {

    private String operationStatus;
    private Boolean cancellable;
    private Boolean retryable;
}
