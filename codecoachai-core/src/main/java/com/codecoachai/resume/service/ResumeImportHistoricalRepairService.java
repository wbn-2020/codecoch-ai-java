package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.dto.ResumeImportRepairRequestDTO;
import com.codecoachai.resume.domain.dto.ResumeImportRepairRollbackDTO;
import com.codecoachai.resume.domain.vo.ResumeImportRepairResultVO;

public interface ResumeImportHistoricalRepairService {

    ResumeImportRepairResultVO repair(ResumeImportRepairRequestDTO request, Long actorUserId);

    ResumeImportRepairResultVO rollback(
            String repairBatchId, ResumeImportRepairRollbackDTO request, Long actorUserId);
}
