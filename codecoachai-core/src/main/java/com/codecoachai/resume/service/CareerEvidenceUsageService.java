package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.dto.CareerEvidenceUsageCreateDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageQueryDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageResultCommandDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageResultQueryDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageResultWriteDTO;
import com.codecoachai.resume.domain.vo.CareerEvidenceUsageResultVO;
import com.codecoachai.resume.domain.vo.CareerEvidenceUsageVO;
import com.codecoachai.resume.domain.vo.EvidenceAssetEnvelopeVO;
import com.codecoachai.resume.domain.vo.EvidenceAssetOverviewEnvelopeVO;
import com.codecoachai.resume.domain.vo.InnerCareerEvidenceUsageFactsVO;
import java.time.LocalDateTime;

public interface CareerEvidenceUsageService {

    CareerEvidenceUsageVO createUsage(Long applicationId, CareerEvidenceUsageCreateDTO request);

    EvidenceAssetEnvelopeVO<CareerEvidenceUsageVO> listApplicationUsages(
            Long applicationId, CareerEvidenceUsageQueryDTO query);

    EvidenceAssetEnvelopeVO<CareerEvidenceUsageVO> listUsages(CareerEvidenceUsageQueryDTO query);

    CareerEvidenceUsageVO usage(Long usageId);

    CareerEvidenceUsageResultVO createResult(
            Long usageId, CareerEvidenceUsageResultWriteDTO request);

    EvidenceAssetEnvelopeVO<CareerEvidenceUsageResultVO> listUsageResults(Long usageId);

    EvidenceAssetEnvelopeVO<CareerEvidenceUsageResultVO> listResults(
            CareerEvidenceUsageResultQueryDTO query);

    CareerEvidenceUsageResultVO confirmResult(
            Long resultId, CareerEvidenceUsageResultCommandDTO request);

    CareerEvidenceUsageResultVO correctResult(
            Long resultId, CareerEvidenceUsageResultCommandDTO request);

    CareerEvidenceUsageResultVO voidResult(
            Long resultId, CareerEvidenceUsageResultCommandDTO request);

    EvidenceAssetOverviewEnvelopeVO overview(Long campaignId, Long applicationId);

    InnerCareerEvidenceUsageFactsVO innerFacts(
            Long userId, Long campaignId, Long applicationId, Long usageId,
            LocalDateTime dataCutoffAt);
}
