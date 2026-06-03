package com.codecoachai.ai.service;

import com.codecoachai.ai.domain.dto.AiCallLogQueryDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateSaveDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateQueryDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateVersionCreateDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateVersionQueryDTO;
import com.codecoachai.ai.domain.dto.PromptVersionTestDTO;
import com.codecoachai.ai.domain.dto.PromptVersionActionDTO;
import com.codecoachai.ai.domain.dto.UpdatePromptStatusDTO;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateDetailVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVersionVO;
import com.codecoachai.ai.domain.vo.PromptVersionTestVO;
import com.codecoachai.common.core.domain.PageResult;

public interface PromptTemplateService {

    PageResult<PromptTemplateVO> pagePrompts(Long pageNo, Long pageSize, String keyword, String scene, Integer status);

    PageResult<PromptTemplateVO> pagePrompts(PromptTemplateQueryDTO query);

    PromptTemplateVO createPrompt(PromptTemplateSaveDTO dto);

    PromptTemplateVO getPrompt(Long id);

    PromptTemplateDetailVO getPromptDetail(Long id);

    PromptTemplateVO updatePrompt(Long id, PromptTemplateSaveDTO dto);

    void deletePrompt(Long id);

    void updateStatus(Long id, UpdatePromptStatusDTO dto);

    PageResult<PromptTemplateVersionVO> pageVersions(Long templateId, PromptTemplateVersionQueryDTO query);

    PromptTemplateVersionVO createVersion(Long templateId, PromptTemplateVersionCreateDTO dto);

    PromptTemplateVersionVO activateVersion(Long versionId, PromptVersionActionDTO dto);

    PromptTemplateVersionVO rollbackVersion(Long versionId, PromptVersionActionDTO dto);

    void disableVersion(Long versionId, PromptVersionActionDTO dto);

    PromptVersionTestVO testVersion(Long versionId, PromptVersionTestDTO dto);

    PageResult<AiCallLogVO> pageLogs(Long pageNo, Long pageSize);

    PageResult<AiCallLogVO> pageLogs(AiCallLogQueryDTO query);

    PageResult<AiCallLogVO> pageTemplateLogs(Long templateId, AiCallLogQueryDTO query);

    PageResult<AiCallLogVO> pageVersionLogs(Long versionId, AiCallLogQueryDTO query);

    AiCallLogVO getLog(Long id);

    AiCallLogVO getLogRaw(Long id);
}
