package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("portfolio_demo_dataset")
public class PortfolioDemoDataset extends BaseEntity {

    private Long userId;
    private Long demoUserId;
    private String datasetKey;
    private String datasetName;
    private String status;
    private String version;
    private LocalDateTime loadedAt;
    private LocalDateTime resetAt;
    private String storyJson;
    private Integer demoFlag;
}
