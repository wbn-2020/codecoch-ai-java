package com.codecoachai.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.ai.convert.AiConvert;
import com.codecoachai.ai.domain.dto.PromptTemplateSaveDTO;
import com.codecoachai.ai.domain.dto.UpdatePromptStatusDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVO;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.mapper.PromptTemplateMapper;
import com.codecoachai.ai.service.PromptTemplateService;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;
    private final AiCallLogMapper aiCallLogMapper;

    @Override
    public PageResult<PromptTemplateVO> pagePrompts(Long pageNo, Long pageSize, String keyword, String scene,
                                                    Integer status) {
        Page<PromptTemplate> page = promptTemplateMapper.selectPage(Page.of(defaultPage(pageNo), defaultSize(pageSize)),
                new LambdaQueryWrapper<PromptTemplate>()
                        .and(StringUtils.hasText(keyword), condition -> condition
                                .like(PromptTemplate::getName, keyword)
                                .or()
                                .like(PromptTemplate::getContent, keyword))
                        .eq(StringUtils.hasText(scene), PromptTemplate::getScene, scene)
                        .eq(status != null, PromptTemplate::getStatus, status)
                        .orderByDesc(PromptTemplate::getUpdatedAt));
        return PageResult.of(page.getRecords().stream().map(AiConvert::toPromptVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PromptTemplateVO createPrompt(PromptTemplateSaveDTO dto) {
        PromptTemplate template = new PromptTemplate();
        apply(template, dto);
        promptTemplateMapper.insert(template);
        return AiConvert.toPromptVO(template);
    }

    @Override
    public PromptTemplateVO updatePrompt(Long id, PromptTemplateSaveDTO dto) {
        PromptTemplate template = getTemplate(id);
        apply(template, dto);
        promptTemplateMapper.updateById(template);
        return AiConvert.toPromptVO(template);
    }

    @Override
    public void deletePrompt(Long id) {
        promptTemplateMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, UpdatePromptStatusDTO dto) {
        PromptTemplate template = getTemplate(id);
        template.setStatus(dto.getStatus());
        promptTemplateMapper.updateById(template);
    }

    @Override
    public PageResult<AiCallLogVO> pageLogs(Long pageNo, Long pageSize) {
        Page<AiCallLog> page = aiCallLogMapper.selectPage(Page.of(defaultPage(pageNo), defaultSize(pageSize)),
                new LambdaQueryWrapper<AiCallLog>().orderByDesc(AiCallLog::getCreatedAt));
        return PageResult.of(page.getRecords().stream().map(AiConvert::toLogVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public AiCallLogVO getLog(Long id) {
        AiCallLog log = aiCallLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "AI call log not found");
        }
        return AiConvert.toLogVO(log);
    }

    private void apply(PromptTemplate template, PromptTemplateSaveDTO dto) {
        template.setScene(dto.getScene());
        template.setName(dto.getName());
        template.setContent(dto.getContent());
        template.setStatus(dto.getStatus() == null ? CommonConstants.YES : dto.getStatus());
    }

    private PromptTemplate getTemplate(Long id) {
        PromptTemplate template = promptTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Prompt template not found");
        }
        return template;
    }

    private long defaultPage(Long pageNo) {
        return pageNo == null ? 1L : pageNo;
    }

    private long defaultSize(Long pageSize) {
        return pageSize == null ? 10L : pageSize;
    }
}
