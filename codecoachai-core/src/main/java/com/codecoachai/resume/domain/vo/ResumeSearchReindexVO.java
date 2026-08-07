package com.codecoachai.resume.domain.vo;

import lombok.Data;

@Data
public class ResumeSearchReindexVO {

    private Long afterId;
    private Long nextAfterId;
    private Integer batchSize;
    private Integer queued;
    private Boolean hasMore;
}
