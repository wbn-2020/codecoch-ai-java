package com.codecoachai.resume.careerimport.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_import_row")
public class CareerImportRow extends BaseEntity {
    private Long userId;
    private Long batchId;
    private Integer rowNumber;
    private String disposition;
    private String rawDataJson;
    private String errorCode;
    private String errorMessage;
    private String duplicateCandidatesJson;
    private Long applicationId;
    private Long calendarEventId;
}
