package com.codecoachai.interview.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InterviewRemediationOptionVO {

    private String optionKey;
    private String reasonType;
    private String title;
    private String description;
    private String evidence;
    private List<Long> sourceRequirementIds;
    private String practicePurpose;
    private Boolean strongRemediation;
}
