package com.codecoachai.resume.careerimport.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_import_batch")
public class CareerImportBatch extends BaseEntity {
    private Long userId;
    private String format;
    private String filename;
    private String timezone;
    private String duplicatePolicy;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer errorCount;
    private Integer duplicateCount;
}
