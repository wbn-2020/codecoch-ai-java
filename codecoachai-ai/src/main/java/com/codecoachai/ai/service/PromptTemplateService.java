package com.codecoachai.ai.service;

import com.codecoachai.ai.domain.dto.PromptTemplateSaveDTO;
import com.codecoachai.ai.domain.dto.UpdatePromptStatusDTO;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVO;
import com.codecoachai.common.core.domain.PageResult;

public interface PromptTemplateService {

    PageResult<PromptTemplateVO> pagePrompts(Long pageNo, Long pageSize);

    PromptTemplateVO createPrompt(PromptTemplateSaveDTO dto);

    PromptTemplateVO updatePrompt(Long id, PromptTemplateSaveDTO dto);

    void deletePrompt(Long id);

    void updateStatus(Long id, UpdatePromptStatusDTO dto);

    PageResult<AiCallLogVO> pageLogs(Long pageNo, Long pageSize);

    AiCallLogVO getLog(Long id);
}
