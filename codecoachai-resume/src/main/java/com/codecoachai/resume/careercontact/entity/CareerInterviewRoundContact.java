package com.codecoachai.resume.careercontact.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_interview_round_contact")
public class CareerInterviewRoundContact extends BaseEntity {
    private Long userId;
    private Long interviewRoundId;
    private Long contactId;
    private String relationshipType;
}
