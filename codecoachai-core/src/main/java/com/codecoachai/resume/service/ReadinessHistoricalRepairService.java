package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.dto.ReadinessRepairRequestDTO;
import com.codecoachai.resume.domain.vo.ReadinessRepairResultVO;

public interface ReadinessHistoricalRepairService {

    ReadinessRepairResultVO repair(ReadinessRepairRequestDTO request);
}
