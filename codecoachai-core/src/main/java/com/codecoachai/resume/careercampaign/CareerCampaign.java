package com.codecoachai.resume.careercampaign;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_campaign")
public class CareerCampaign extends BaseEntity {
    private Long userId;
    private String name;
    private String status;
    private String goal;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime archivedAt;
    private Integer lockVersion;
}
