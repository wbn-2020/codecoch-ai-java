package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("portfolio_rehearsal_session")
public class PortfolioRehearsalSession extends BaseEntity {

    private Long userId;
    private String activeRouteKey;
    private Integer activeNodeIndex;
    private Integer elapsedSeconds;

    /**
     * JSON array of completed node ids across all routes, e.g. ["quick-target-job","deep-loop"].
     * Node identity is defined by the frontend rehearsal route constants; the backend only
     * persists the opaque id strings and never interprets them.
     */
    private String completedNodeIds;
}
