package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("target_job")
public class TargetJob extends BaseEntity {

    private Long userId;
    private String jobTitle;
    private String companyName;
    private String jobLevel;
    private String jdText;
    private String jdSource;
    private Integer currentFlag;
    private Integer status;
    private String parseStatus;
    private String parseErrorMessage;
}
