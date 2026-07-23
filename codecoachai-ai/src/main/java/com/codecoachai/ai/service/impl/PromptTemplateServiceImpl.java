package com.codecoachai.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.convert.AiConvert;
import com.codecoachai.ai.domain.dto.AiCallLogQueryDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateActionDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateSaveDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateQueryDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateVersionCreateDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateVersionQueryDTO;
import com.codecoachai.ai.domain.dto.PromptVersionActionDTO;
import com.codecoachai.ai.domain.dto.PromptVersionTestDTO;
import com.codecoachai.ai.domain.dto.UpdatePromptStatusDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.entity.PromptTemplateVersion;
import com.codecoachai.ai.domain.enums.PromptVersionStatus;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateDetailVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVersionVO;
import com.codecoachai.ai.domain.vo.PromptVersionTestVO;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.mapper.PromptTemplateMapper;
import com.codecoachai.ai.mapper.PromptTemplateVersionMapper;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptTemplateService;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
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
    private final AiCallLogService aiCallLogService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

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
        template.setActiveVersionId(null);
        template.setEnabled(CommonConstants.NO);
        template.setStatus(CommonConstants.NO);
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
        return toPromptDetail(template, false);
    }

    @Override
    public PromptTemplateDetailVO getPromptRaw(Long id) {
        PromptTemplate template = getTemplate(id);
        return toPromptDetail(template, true);
    }

    private PromptTemplateDetailVO toPromptDetail(PromptTemplate template, boolean includeRawFields) {
        PromptTemplateDetailVO vo = new PromptTemplateDetailVO();
        PromptTemplateVO base = AiConvert.toPromptVO(template, includeRawFields);
        vo.setId(base.getId());
        vo.setScene(base.getScene());
        vo.setName(base.getName());
        vo.setTemplateName(base.getTemplateName());
        vo.setDescription(base.getDescription());
        vo.setContent(base.getContent());
        vo.setTemplateContent(base.getTemplateContent());
        vo.setContentLength(base.getContentLength());
        vo.setContentHash(base.getContentHash());
        vo.setTemplateContentLength(base.getTemplateContentLength());
        vo.setTemplateContentHash(base.getTemplateContentHash());
        vo.setVariables(base.getVariables());
        vo.setVersion(base.getVersion());
        vo.setActiveVersionId(base.getActiveVersionId());
        vo.setEnabled(base.getEnabled());
        vo.setStatus(base.getStatus());
        vo.setRawFieldsAvailable(base.getRawFieldsAvailable());
        vo.setRawFieldsIncluded(base.getRawFieldsIncluded());
        vo.setRawAccessPermission(base.getRawAccessPermission());
        if (template.getActiveVersionId() != null) {
            PromptTemplateVersion version = promptTemplateVersionMapper.selectById(template.getActiveVersionId());
            if (version != null) {
                vo.setActiveVersion(AiConvert.toVersionVO(version, includeRawFields));
            }
        }
        return vo;
    }

    @Override
    public PromptTemplateVO updatePrompt(Long id, PromptTemplateSaveDTO dto) {
        PromptTemplate template = getTemplate(id);
        assertExpectedTemplateState(template, dto.getExpectedStatus(), dto.getExpectedActiveVersionId());
        if (hasPromptContent(dto)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        applyMetadata(template, dto);
        promptTemplateMapper.updateById(template);
        return AiConvert.toPromptVO(template);
    }

    @Override
    public void deletePrompt(Long id, PromptTemplateActionDTO dto) {
        PromptTemplate template = getTemplate(id);
        PromptTemplateActionDTO action = dto == null ? new PromptTemplateActionDTO() : dto;
        assertExpectedTemplateState(template, action.getExpectedStatus(), action.getExpectedActiveVersionId());
        if (isTemplateEnabled(template)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        template.setStatus(CommonConstants.NO);
        template.setEnabled(CommonConstants.NO);
        promptTemplateMapper.updateById(template);
    }

    @Override
    public void updateStatus(Long id, UpdatePromptStatusDTO dto) {
        PromptTemplate template = getTemplate(id);
        assertExpectedTemplateState(template, dto.getExpectedStatus(), dto.getExpectedActiveVersionId());
        Integer nextStatus = normalizeTemplateStatus(dto.getStatus());
        if (CommonConstants.YES.equals(nextStatus)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        template.setStatus(nextStatus);
        template.setEnabled(nextStatus);
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
    public PromptTemplateVersionVO getVersionRaw(Long versionId) {
        return AiConvert.toVersionVO(getVersion(versionId), true);
    }

    @Override
    public PromptTemplateVersionVO createVersion(Long templateId, PromptTemplateVersionCreateDTO dto) {
        PromptTemplate template = getTemplate(templateId);
        assertVersionCodeUnique(templateId, dto.getVersionCode());
        PromptTemplateVariableValidator.validateDefinition(dto.getContent(), dto.getVariablesJson());
        PromptTemplateVersion version = new PromptTemplateVersion();
        version.setTemplateId(template.getId());
        version.setScene(template.getScene());
        version.setVersionCode(dto.getVersionCode());
        version.setVersionName(dto.getVersionName());
        version.setContent(dto.getContent());
        version.setVariablesJson(dto.getVariablesJson());
        version.setModelParamsJson(dto.getModelParamsJson());
        version.setStatus(normalizeCreateStatus(dto.getStatus()));
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
        if (PromptVersionStatus.DISABLED.name().equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        PromptTemplateVariableValidator.validateDefinition(version.getContent(), version.getVariablesJson());
        PromptTemplate template = getTemplate(version.getTemplateId());
        assertExpectedActiveVersion(template, dto);
        promptTemplateVersionMapper.update(null, new LambdaUpdateWrapper<PromptTemplateVersion>()
                .eq(PromptTemplateVersion::getTemplateId, template.getId())
                .eq(PromptTemplateVersion::getIsActive, CommonConstants.YES)
                .set(PromptTemplateVersion::getIsActive, CommonConstants.NO)
                .set(PromptTemplateVersion::getStatus, PromptVersionStatus.INACTIVE.name()));
        version.setStatus(PromptVersionStatus.ACTIVE.name());
        version.setIsActive(CommonConstants.YES);
        version.setActivatedBy(LoginUserContext.getUserId());
        version.setActivatedAt(LocalDateTime.now());
        version.setChangeLog(dto == null ? version.getChangeLog() : firstText(dto.getChangeLog(), version.getChangeLog()));
        promptTemplateVersionMapper.updateById(version);

        disableOtherTemplatesForScene(template);
        int updated = updateTemplateForActivation(template, version, expectedActiveVersion(dto));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        return AiConvert.toVersionVO(version);
    }

    @Override
    public PromptTemplateVersionVO rollbackVersion(Long versionId, PromptVersionActionDTO dto) {
        return activateVersion(versionId, dto);
    }

    @Override
    public void disableVersion(Long versionId, PromptVersionActionDTO dto) {
        PromptTemplateVersion version = getVersion(versionId);
        PromptTemplate template = getTemplate(version.getTemplateId());
        assertExpectedActiveVersion(template, dto);
        if (CommonConstants.YES.equals(version.getIsActive())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        version.setStatus(PromptVersionStatus.DISABLED.name());
        version.setChangeLog(dto == null ? version.getChangeLog() : firstText(dto.getChangeLog(), version.getChangeLog()));
        promptTemplateVersionMapper.updateById(version);
    }

    @Override
    public PromptVersionTestVO testVersion(Long versionId, PromptVersionTestDTO dto) {
        PromptTemplateVersion version = getVersion(versionId);
        PromptTemplate template = getTemplate(version.getTemplateId());
        Map<String, String> variables = dto == null || dto.getInputVariables() == null
                ? Collections.emptyMap()
                : dto.getInputVariables();
        PromptTemplateVariableValidator.validateDefinition(version.getContent(), version.getVariablesJson());
        String renderedPrompt = PromptTemplateVariableValidator.render(
                version.getContent(), version.getVariablesJson(), variables);
        boolean callAi = dto != null && Boolean.TRUE.equals(dto.getCallAi());
        PromptTestResult testResult = testAiResponse(template, version, renderedPrompt, variables, callAi);

        PromptVersionTestVO vo = new PromptVersionTestVO();
        vo.setVersionId(version.getId());
        vo.setTemplateId(template.getId());
        vo.setScene(version.getScene());
        vo.setVersionCode(version.getVersionCode());
        vo.setRenderedPromptLength(renderedPrompt == null ? 0 : renderedPrompt.length());
        vo.setRenderedPromptHash(sha256(renderedPrompt));
        vo.setInputVariableCount(variables.size());
        vo.setInputVariableKeys(variables.keySet().stream().sorted().toList());
        vo.setInputVariablesHash(sha256(toJson(variables)));
        vo.setAiResponseLength(testResult.response() == null ? 0 : testResult.response().length());
        vo.setAiResponseHash(sha256(testResult.response()));
        vo.setAiCallLogId(testResult.logId());
        vo.setMockMode(Boolean.TRUE.equals(aiProperties.getMockEnabled()));
        vo.setRawFieldsAvailable(true);
        vo.setRawFieldsIncluded(false);
        vo.setRawAccessPermission("admin:ai:log:raw:view");
        return vo;
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
    public PageResult<AiCallLogVO> pageTemplateLogs(Long templateId, AiCallLogQueryDTO query) {
        getTemplate(templateId);
        AiCallLogQueryDTO actualQuery = query == null ? new AiCallLogQueryDTO() : query;
        actualQuery.setPromptTemplateId(templateId);
        return pageLogs(actualQuery);
    }

    @Override
    public PageResult<AiCallLogVO> pageVersionLogs(Long versionId, AiCallLogQueryDTO query) {
        PromptTemplateVersion version = getVersion(versionId);
        AiCallLogQueryDTO actualQuery = query == null ? new AiCallLogQueryDTO() : query;
        actualQuery.setPromptTemplateId(version.getTemplateId());
        actualQuery.setPromptTemplateVersionId(versionId);
        return pageLogs(actualQuery);
    }

    @Override
    public AiCallLogVO getLog(Long id) {
        AiCallLog log = aiCallLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        return AiConvert.toLogVO(log);
    }

    @Override
    public AiCallLogVO getLogRaw(Long id) {
        AiCallLog log = aiCallLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        return AiConvert.toLogVO(log, true);
    }

    private PromptTestResult testAiResponse(PromptTemplate template, PromptTemplateVersion version,
                                            String renderedPrompt, Map<String, String> variables, boolean callAi) {
        if (!callAi) {
            String response = "PROMPT_RENDER_ONLY";
            return new PromptTestResult(response, null);
        }
        if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
            String response = "提示词版本测试模拟响应";
            return new PromptTestResult(response,
                    savePromptTestLog(template, version, renderedPrompt, variables, response));
        }
        try {
            AiCallContext ctx = new AiCallContext();
            ctx.setScene("PROMPT_VERSION_TEST");
            ctx.setPrompt(renderedPrompt);
            ctx.setUserId(LoginUserContext.getUserId());
            ctx.setBusinessId(String.valueOf(version.getId()));
            ctx.setPromptTemplateId(template.getId());
            ctx.setPromptTemplateVersionId(version.getId());
            ctx.setPromptVersion(version.getVersionCode());
            ctx.setInputVariablesJson(toJson(variables));
            ctx.setModelParamsJson(version.getModelParamsJson());
            ctx.setPromptHash(sha256(renderedPrompt));
            ctx.setResponseFormat("TEXT");
            ctx.setRequestBody("promptVersionTest=true");
            ctx.setCheckQuota(true);
            RouteResult result = aiCallLogService.callAndLog(ctx);
            return new PromptTestResult(result.getContent(), result.getAiCallLogId());
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Prompt version test failed. Please retry later.");
        }
    }

    private Long savePromptTestLog(PromptTemplate template, PromptTemplateVersion version, String renderedPrompt,
                                   Map<String, String> variables, String response) {
        AiCallLog log = new AiCallLog();
        log.setUserId(LoginUserContext.getUserId());
        log.setScene("PROMPT_VERSION_TEST");
        log.setModelName(Boolean.TRUE.equals(aiProperties.getMockEnabled())
                ? aiProperties.getModel() + "（模拟）"
                : aiProperties.getModel());
        log.setPromptTemplateId(template.getId());
        log.setPromptTemplateVersionId(version.getId());
        log.setPromptVersion(version.getVersionCode());
        log.setInputVariablesJson(toJson(variables));
        log.setPromptHash(sha256(renderedPrompt));
        log.setResponseFormat("TEXT");
        log.setRequestPrompt(renderedPrompt);
        log.setResponseContent(response);
        log.setBusinessId(String.valueOf(version.getId()));
        log.setRequestBody("promptVersionTest=true");
        log.setResponseBody(response);
        log.setElapsedMs(0L);
        log.setCostMillis(0L);
        log.setSuccess(CommonConstants.YES);
        log.setStatus(CommonConstants.YES);
        try {
            aiCallLogMapper.insert(log);
            return log.getId();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void apply(PromptTemplate template, PromptTemplateSaveDTO dto) {
        if (!StringUtils.hasText(dto.getName()) && !StringUtils.hasText(dto.getTemplateName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        if (!StringUtils.hasText(dto.getContent()) && !StringUtils.hasText(dto.getTemplateContent())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        String content = StringUtils.hasText(dto.getContent()) ? dto.getContent() : dto.getTemplateContent();
        PromptTemplateVariableValidator.validateDefinition(content, dto.getVariables());
        template.setScene(dto.getScene());
        template.setName(StringUtils.hasText(dto.getName()) ? dto.getName() : dto.getTemplateName());
        template.setTemplateName(StringUtils.hasText(dto.getTemplateName()) ? dto.getTemplateName() : dto.getName());
        template.setDescription(dto.getDescription());
        template.setContent(content);
        template.setTemplateContent(StringUtils.hasText(dto.getTemplateContent()) ? dto.getTemplateContent() : dto.getContent());
        template.setVariables(dto.getVariables());
        template.setVersion(dto.getVersion());
        template.setActiveVersionId(null);
        template.setEnabled(CommonConstants.NO);
        template.setStatus(CommonConstants.NO);
    }

    private void applyMetadata(PromptTemplate template, PromptTemplateSaveDTO dto) {
        if (StringUtils.hasText(dto.getName())) {
            template.setName(dto.getName());
        }
        if (StringUtils.hasText(dto.getTemplateName())) {
            template.setTemplateName(dto.getTemplateName());
        }
        if (dto.getDescription() != null) {
            template.setDescription(dto.getDescription());
        }
    }

    private boolean hasPromptContent(PromptTemplateSaveDTO dto) {
        return StringUtils.hasText(dto.getContent()) || StringUtils.hasText(dto.getTemplateContent());
    }

    private String normalizeCreateStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return PromptVersionStatus.DRAFT.name();
        }
        PromptVersionStatus versionStatus;
        try {
            versionStatus = PromptVersionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        if (PromptVersionStatus.ACTIVE.equals(versionStatus)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        return versionStatus.name();
    }

    private void assertVersionCodeUnique(Long templateId, String versionCode) {
        Long count = promptTemplateVersionMapper.selectCount(new LambdaQueryWrapper<PromptTemplateVersion>()
                .eq(PromptTemplateVersion::getTemplateId, templateId)
                .eq(PromptTemplateVersion::getVersionCode, versionCode));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
    }

    private PromptTemplateVersion getVersion(Long id) {
        PromptTemplateVersion version = promptTemplateVersionMapper.selectById(id);
        if (version == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        return version;
    }

    private PromptTemplate getTemplate(Long id) {
        PromptTemplate template = promptTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        return template;
    }

    private void assertExpectedTemplateState(PromptTemplate template, Integer expectedStatus,
                                             Long expectedActiveVersionId) {
        if (!Objects.equals(template.getStatus(), expectedStatus)
                || !Objects.equals(template.getActiveVersionId(), expectedActiveVersionId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
    }

    private void assertExpectedActiveVersion(PromptTemplate template, PromptVersionActionDTO dto) {
        Long expectedActiveVersionId = expectedActiveVersion(dto);
        if (!Objects.equals(template.getActiveVersionId(), expectedActiveVersionId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
    }

    private Long expectedActiveVersion(PromptVersionActionDTO dto) {
        return dto == null ? null : dto.getExpectedCurrentActiveVersionId();
    }

    private Integer normalizeTemplateStatus(Integer status) {
        if (!CommonConstants.YES.equals(status) && !CommonConstants.NO.equals(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Prompt template content must be changed through version management.");
        }
        return status;
    }

    private boolean isTemplateEnabled(PromptTemplate template) {
        return CommonConstants.YES.equals(template.getStatus()) || CommonConstants.YES.equals(template.getEnabled());
    }

    private void disableOtherTemplatesForScene(PromptTemplate template) {
        promptTemplateMapper.update(null, new LambdaUpdateWrapper<PromptTemplate>()
                .eq(PromptTemplate::getScene, template.getScene())
                .ne(PromptTemplate::getId, template.getId())
                .set(PromptTemplate::getEnabled, CommonConstants.NO)
                .set(PromptTemplate::getStatus, CommonConstants.NO));
    }

    private int updateTemplateForActivation(PromptTemplate template, PromptTemplateVersion version,
                                            Long expectedActiveVersionId) {
        LambdaUpdateWrapper<PromptTemplate> wrapper = new LambdaUpdateWrapper<PromptTemplate>()
                .eq(PromptTemplate::getId, template.getId())
                .set(PromptTemplate::getActiveVersionId, version.getId())
                .set(PromptTemplate::getContent, version.getContent())
                .set(PromptTemplate::getTemplateContent, version.getContent())
                .set(PromptTemplate::getVariables, version.getVariablesJson())
                .set(PromptTemplate::getVersion, version.getVersionCode())
                .set(PromptTemplate::getEnabled, CommonConstants.YES)
                .set(PromptTemplate::getStatus, CommonConstants.YES);
        if (expectedActiveVersionId == null) {
            wrapper.isNull(PromptTemplate::getActiveVersionId);
        } else {
            wrapper.eq(PromptTemplate::getActiveVersionId, expectedActiveVersionId);
        }
        return promptTemplateMapper.update(null, wrapper);
    }

    private long defaultPage(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long defaultSize(Long pageSize) {
        return pageSize == null || pageSize < 1 ? 10L : Math.min(pageSize, 100L);
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private record PromptTestResult(String response, Long logId) {
    }
}

