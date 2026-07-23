package com.codecoachai.resume.careeroffer.vo;

import com.codecoachai.resume.careeroffer.entity.CareerOfferDecisionItem;
import com.codecoachai.resume.careeroffer.entity.CareerOfferDecisionSnapshot;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CareerOfferDecisionVO {
    private Long id;
    private Long campaignId;
    private String status;
    private Long selectedOfferId;
    private String outcome;
    private Integer lockVersion;
    private CareerOfferDecisionSnapshot snapshot;
    private List<CareerOfferDecisionItem> items = new ArrayList<>();
}
