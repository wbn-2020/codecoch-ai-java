package com.codecoachai.resume.careeroffer.vo;

import com.codecoachai.resume.careeroffer.entity.CareerOfferVersion;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerOfferVO {
    private Long id;
    private Long applicationId;
    private Long currentVersionId;
    private String status;
    private Integer lockVersion;
    private LocalDateTime decisionDeadline;
    private CareerOfferVersion currentVersion;
    private LocalDateTime finalizedAt;
}
