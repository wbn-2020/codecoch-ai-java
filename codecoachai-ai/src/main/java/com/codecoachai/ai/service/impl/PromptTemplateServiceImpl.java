package com.codecoachai.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.ai.convert.AiConvert;
import com.codecoachai.ai.domain.dto.AiCallLogQueryDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateSaveDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateQueryDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateVersionCreateDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateVersionQueryDTO;
import com.codecoachai.ai.domain.dto.PromptVersionActionDTO;
import com.codecoachai.ai.domain.dto.UpdatePromptStatusDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.entity.PromptTemplateVersion;
import com.codecoachai.ai.domain.enums.PromptVersionStatus;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateDetailVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVersionVO;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.mapper.PromptTemplateMapper;
import com.codecoachai.ai.mapper.PromptTemplateVersionMapper;
import com.codecoachai.ai.service.PromptTemplateService;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;
    private final PromptTemplateVersionMapper promptTemplateVersionMapper;
    private final AiCallLogMapper aiCallLogMapper;

    @Override
    public PageResult<PromptTemplateVO> pagePrompts(Long pageNo, Long pageSize, String keyword, String scene,
                                                    Integer status) {
        PromptTemplateQueryDTO query = new PromptTemplateQueryDTO();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setKeyword(keyword);
        query.setScene(scene);
        query.setStatus(status);
        return pagePrompts(query);
    }

    @Override
    public PageResult<PromptTemplateVO> pagePrompts(PromptTemplateQueryDTO query) {
        PromptTemplateQueryDTO actualQuery = query == null ? new PromptTemplateQueryDTO() : query;
        Page<PromptTemplate> page = promptTemplateMapper.selectPage(
                Page.of(defaultPage(actualQuery.getPageNo()), defaultSize(actualQuery.getPageSize())),
                new LambdaQueryWrapper<PromptTemplate>()
                        .and(StringUtils.hasText(actualQuery.getKeyword()), condition -> condition
                                .like(PromptTemplate::getName, actualQuery.getKeyword())
                                .or()
                                .like(PromptTemplate::getTemplateName, actualQuery.getKeyword())
                                .or()
                                .like(PromptTemplate::getContent, actualQuery.getKeyword()))
                        .eq(StringUtils.hasText(actualQuery.getScene()), PromptTemplate::getScene, actualQuery.getScene())
                        .eq(actualQuery.getEnabled() != null, PromptTemplate::getEnabled, actualQuery.getEnabled())
                        .eq(actualQuery.getStatus() != null, PromptTemplate::getStatus, actualQuery.getStatus())
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
    public PromptTemplateVO getPrompt(Long id) {
        return AiConvert.toPromptVO(getTemplate(id));
    }

    @Override
    public PromptTemplateDetailVO getPromptDetail(Long id) {
        PromptTemplate template = getTemplate(id);
        PromptTemplateDetailVO vo = new PromptTemplateDetailVO();
        PromptTemplateVO base = AiConvert.toPromptVO(template);
        vo.setId(base.getId());
        vo.setScene(base.getScene());
        vo.setName(base.getName());
        vo.setTemplateName(base.getTemplateName());
        vo.setDescription(base.getDescription());
        vo.setContent(base.getContent());
        vo.setTemplateContent(base.getTemplateContent());
        vo.setVariables(base.getVariables());
        vo.setVersion(base.getVersion());
        vo.setActiveVersionId(base.getActiveVersionId());
        vo.setEnabled(base.getEnabled());
        vo.setStatus(base.getStatus());
        if (template.getActiveVersionId() != null) {
            PromptTemplateVersion version = promptTemplateVersionMapper.selectById(template.getActiveVersionId());
            if (version != null) {
                vo.setActiveVersion(AiConvert.toVersionVO(version));
            }
        }
        return vo;
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
        template.setEnabled(dto.getStatus());
        promptTemplateMapper.updateById(template);
    }

    @Override
    public PageResult<PromptTemplateVersionVO> pageVersions(Long templateId, PromptTemplateVersionQueryDTO query) {
        getTemplate(templateId);
        PromptTemplateVersionQueryDTO actualQuery = query == null ? new PromptTemplateVersionQueryDTO() : query;
        Page<PromptTemplateVersion> page = promptTemplateVersionMapper.selectPage(
                Page.of(defaultPage(actualQuery.getPageNo()), defaultSize(actualQuery.getPageSize())),
                new LambdaQueryWrapper<PromptTemplateVersion>()
                        .eq(PromptTemplateVersion::getTemplateId, templateId)
                        .eq(StringUtils.hasText(actualQuery.getStatus()), PromptTemplateVersion::getStatus,
                                actualQuery.getStatus())
                        .eq(actualQuery.getIsActive() != null, PromptTemplateVersion::getIsActive,
                                actualQuery.getIsActive())
                        .orderByDesc(PromptTemplateVersion::getCreatedAt));
        return PageResult.of(page.getRecords().stream().map(AiConvert::toVersionVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PromptTemplateVersionVO createVersion(Long templateId, PromptTemplateVersionCreateDTO dto) {
        PromptTemplate template = getTemplate(templateId);
        assertVersionCodeUnique(templateId, dto.getVersionCode());
        PromptTemplateVersion version = new PromptTemplateVersion();
        version.setTemplateId(template.getId());
        version.setScene(template.getScene());
        version.setVersionCode(dto.getVersionCode());
        version.setVersionName(dto.getVersionName());
        version.setContent(dto.getContent());
        version.setVariablesJson(dto.getVariablesJson());
        version.setModelParamsJson(dto.getModelParamsJson());
        version.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : PromptVersionStatus.DRAFT.name());
        version.setIsActive(CommonConstants.NO);
        version.setCreatedBy(LoginUserContext.getUserId());
        version.setChangeLog(dto.getChangeLog());
        promptTemplateVersionMapper.insert(version);
        return AiConvert.toVersionVO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateVersionVO activateVersion(Long versionId, PromptVersionActionDTO dto) {
        PromptTemplateVersion version = getVersion(versionId);
        PromptTemplate template = getTemplate(version.getTemplateId());
        promptTemplateVersionMapper.update(null, new LambdaUpdateWrapper<PromptTemplateVersion>()
                .eq(PromptTemplateVersion::getTemplateId, template.getId())
                .eq(PromptTemplateVersion::getIsActive, CommonConstants.YES)
                .set(PromptTemplateVersion::getIsActive, CommonConstants.NO)
                .set(PromptTemplateVersion::getStatus, PromptVersionStatus.DISABLED.name()));
        version.setStatus(PromptVersionStatus.ACTIVE.name());
        version.setIsActive(CommonConstants.YES);
        version.setActivatedBy(LoginUserContext.getUserId());
        version.setActivatedAt(LocalDateTime.now());
        version.setChangeLog(dto == null ? version.getChangeLog() : firstText(dto.getChangeLog(), version.getChangeLog()));
        promptTemplateVersionMapper.updateById(version);

        template.setActiveVersionId(version.getId());
        template.setContent(version.getContent());
        template.setTemplateContent(version.getContent());
        template.setVariables(version.getVariablesJson());
        template.setVersion(version.getVersionCode());
        template.setEnabled(CommonConstants.YES);
        template.setStatus(CommonConstants.YES);
        promptTemplateMapper.updateById(template);
        return AiConvert.toVersionVO(version);
    }

    @Override
    public void disableVersion(Long versionId, PromptVersionActionDTO dto) {
        PromptTemplateVersion version = getVersion(versionId);
        if (CommonConstants.YES.equals(version.getIsActive())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Active prompt version cannot be disabled directly");
        }
        version.setStatus(PromptVersionStatus.DISABLED.name());
        version.setChangeLog(dto == null ? version.getChangeLog() : firstText(dto.getChangeLog(), version.getChangeLog()));
        promptTemplateVersionMapper.updateById(version);
    }

    @Override
    public PageResult<AiCallLogVO> pageLogs(Long pageNo, Long pageSize) {
        AiCallLogQueryDTO query = new AiCallLogQueryDTO();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return pageLogs(query);
    }

    @Override
    public PageResult<AiCallLogVO> pageLogs(AiCallLogQueryDTO query) {
        AiCallLogQueryDTO actualQuery = query == null ? new AiCallLogQueryDTO() : query;
        Page<AiCallLog> page = aiCallLogMapper.selectPage(
                Page.of(defaultPage(actualQuery.getPageNo()), defaultSize(actualQuery.getPageSize())),
                new LambdaQueryWrapper<AiCallLog>()
                        .eq(actualQuery.getUserId() != null, AiCallLog::getUserId, actualQuery.getUserId())
                        .eq(StringUtils.hasText(actualQuery.getScene()), AiCallLog::getScene, actualQuery.getScene())
                        .eq(StringUtils.hasText(actualQuery.getModelName()), AiCallLog::getModelName,
                                actualQuery.getModelName())
                        .eq(actualQuery.getPromptTemplateId() != null, AiCallLog::getPromptTemplateId,
                                actualQuery.getPromptTemplateId())
                        .eq(actualQuery.getPromptTemplateVersionId() != null, AiCallLog::getPromptTemplateVersionId,
                                actualQuery.getPromptTemplateVersionId())
                        .eq(StringUtils.hasText(actualQuery.getPromptVersion()), AiCallLog::getPromptVersion,
                                actualQuery.getPromptVersion())
                        .eq(StringUtils.hasText(actualQuery.getTraceId()), AiCallLog::getTraceId,
                                actualQuery.getTraceId())
                        .eq(StringUtils.hasText(actualQuery.getRequestId()), AiCallLog::getRequestId,
                                actualQuery.getRequestId())
                        .eq(actualQuery.getSuccess() != null, AiCallLog::getSuccess, actualQuery.getSuccess())
                        .eq(actualQuery.getStatus() != null, AiCallLog::getStatus, actualQuery.getStatus())
                        .eq(StringUtils.hasText(actualQuery.getBusinessId()), AiCallLog::getBusinessId,
                                actualQuery.getBusinessId())
                        .ge(actualQuery.getStartTime() != null, AiCallLog::getCreatedAt, actualQuery.getStartTime())
                        .le(actualQuery.getEndTime() != null, AiCallLog::getCreatedAt, actualQuery.getEndTime())
                        .orderByDesc(AiCallLog::getCreatedAt));
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
        if (!StringUtils.hasText(dto.getName()) && !StringUtils.hasText(dto.getTemplateName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "templateName is required");
        }
        if (!StringUtils.hasText(dto.getContent()) && !StringUtils.hasText(dto.getTemplateContent())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "templateContent is required");
        }
        template.setScene(dto.getScene());
        template.setName(StringUtils.hasText(dto.getName()) ? dto.getName() : dto.getTemplateName());
        template.setTemplateName(StringUtils.hasText(dto.getTemplateName()) ? dto.getTemplateName() : dto.getName());
        template.setDescription(dto.getDescription());
        template.setContent(StringUtils.hasText(dto.getContent()) ? dto.getContent() : dto.getTemplateContent());
        template.setTemplateContent(StringUtils.hasText(dto.getTemplateContent()) ? dto.getTemplateContent() : dto.getContent());
        template.setVariables(dto.getVariables());
        template.setVersion(dto.getVersion());
        template.setActiveVersionId(dto.getActiveVersionId());
        template.setEnabled(dto.getEnabled() == null ? CommonConstants.YES : dto.getEnabled());
        template.setStatus(dto.getStatus() == null ? CommonConstants.YES : dto.getStatus());
    }

    private void assertVersionCodeUnique(Long templateId, String versionCode) {
        Long count = promptTemplateVersionMapper.selectCount(new LambdaQueryWrapper<PromptTemplateVersion>()
                .eq(PromptTemplateVersion::getTemplateId, templateId)
                .eq(PromptTemplateVersion::getVersionCode, versionCode));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Prompt version code already exists");
        }
    }

    private PromptTemplateVersion getVersion(Long id) {
        PromptTemplateVersion version = promptTemplateVersionMapper.selectById(id);
        if (version == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Prompt template version not found");
        }
        return version;
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

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
