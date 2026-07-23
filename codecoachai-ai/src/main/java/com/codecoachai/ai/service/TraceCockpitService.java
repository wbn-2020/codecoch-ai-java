package com.codecoachai.ai.service;

import com.codecoachai.ai.domain.dto.TraceCockpitQueryDTO;
import com.codecoachai.ai.domain.vo.TraceCockpitResultVO;

public interface TraceCockpitService {

    TraceCockpitResultVO getTraceCockpit(TraceCockpitQueryDTO query);
}
