package com.codecoachai.resume.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EvidenceAssetOverviewEnvelopeVO
        extends EvidenceAssetEnvelopeVO<EvidenceAssetOverviewVO.ReadinessItem> {

    private EvidenceAssetOverviewVO overview = new EvidenceAssetOverviewVO();
}
