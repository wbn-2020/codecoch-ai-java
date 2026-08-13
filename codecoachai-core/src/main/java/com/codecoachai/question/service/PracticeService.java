package com.codecoachai.question.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.question.domain.dto.PracticeRecordQueryDTO;
import com.codecoachai.question.domain.dto.PracticeSubmitDTO;
import com.codecoachai.question.domain.vo.PracticeRecordVO;

public interface PracticeService {

    PracticeRecordVO submit(Long questionId, PracticeSubmitDTO dto);

    PageResult<PracticeRecordVO> list(PracticeRecordQueryDTO query);

    PracticeRecordVO detail(Long recordId);
}
