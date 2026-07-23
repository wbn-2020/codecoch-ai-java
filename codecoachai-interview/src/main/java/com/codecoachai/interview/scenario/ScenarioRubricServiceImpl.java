package com.codecoachai.interview.scenario;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ScenarioRubricServiceImpl implements ScenarioRubricService {

    private final InterviewScenarioVersionMapper scenarioVersionMapper;
    private final InterviewRubricVersionMapper rubricVersionMapper;
    private final InterviewScenarioBindingMapper bindingMapper;
    private final InterviewSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RubricVersionVO createRubricVersion(RubricVersionCreateDTO dto) {
        validateDimensions(dto.getDimensions());
        String code = normalizeCode(dto.getRubricCode(), "rubricCode");
        InterviewRubricVersion version = new InterviewRubricVersion();
        version.setRubricCode(code);
        version.setVersionNo(nextRubricVersion(code));
        version.setRubricName(dto.getRubricName().trim());
        version.setDescription(trimToNull(dto.getDescription()));
        version.setLocale(defaultLocale(dto.getLocale()));
        version.setDimensionsJson(writeJson(dto.getDimensions()));
        version.setVersionStatus(RubricVersionStatus.DRAFT.name());
        version.setCreatedBy(LoginUserContext.getUserId());
        rubricVersionMapper.insert(version);
        return toRubricVO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RubricVersionVO publishRubricVersion(Long versionId) {
        InterviewRubricVersion version = requireRubric(versionId);
        validateDimensions(readStoredJson(version.getDimensionsJson(), "Stored rubric dimensions are invalid"));
        lockRubricCode(version.getRubricCode());
        version = requireRubricForUpdate(versionId);
        validateDimensions(readStoredJson(version.getDimensionsJson(), "Stored rubric dimensions are invalid"));
        if (RubricVersionStatus.PUBLISHED.name().equals(version.getVersionStatus())) {
            return toRubricVO(version);
        }
        LocalDateTime publishedAt = LocalDateTime.now();
        if (rubricVersionMapper.update(null, new LambdaUpdateWrapper<InterviewRubricVersion>()
                .eq(InterviewRubricVersion::getId, version.getId())
                .eq(InterviewRubricVersion::getVersionStatus, RubricVersionStatus.DRAFT.name())
                .set(InterviewRubricVersion::getVersionStatus, RubricVersionStatus.PUBLISHED.name())
                .set(InterviewRubricVersion::getPublishedAt, publishedAt)) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Rubric version publish state changed");
        }
        version.setVersionStatus(RubricVersionStatus.PUBLISHED.name());
        version.setPublishedAt(publishedAt);
        rubricVersionMapper.update(null, new LambdaUpdateWrapper<InterviewRubricVersion>()
                .eq(InterviewRubricVersion::getRubricCode, version.getRubricCode())
                .eq(InterviewRubricVersion::getVersionStatus, RubricVersionStatus.PUBLISHED.name())
                .ne(InterviewRubricVersion::getId, version.getId())
                .set(InterviewRubricVersion::getVersionStatus, RubricVersionStatus.RETIRED.name()));
        return toRubricVO(version);
    }

    @Override
    public List<RubricVersionVO> listRubricVersions(String rubricCode) {
        return rubricVersionMapper.selectList(new LambdaQueryWrapper<InterviewRubricVersion>()
                        .eq(InterviewRubricVersion::getRubricCode, normalizeCode(rubricCode, "rubricCode"))
                        .orderByDesc(InterviewRubricVersion::getVersionNo))
                .stream().map(this::toRubricVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScenarioVersionVO createScenarioVersion(ScenarioVersionCreateDTO dto) {
        validateScript(dto.getScript());
        InterviewRubricVersion rubric = requireRubric(dto.getRubricVersionId());
        if (!RubricVersionStatus.PUBLISHED.name().equals(rubric.getVersionStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Scenario must reference a published rubric version");
        }
        String code = normalizeCode(dto.getScenarioCode(), "scenarioCode");
        InterviewScenarioVersion version = new InterviewScenarioVersion();
        version.setScenarioCode(code);
        version.setVersionNo(nextScenarioVersion(code));
        version.setScenarioName(dto.getScenarioName().trim());
        version.setDescription(trimToNull(dto.getDescription()));
        version.setLocale(defaultLocale(dto.getLocale()));
        version.setScriptJson(writeJson(dto.getScript()));
        version.setRubricVersionId(rubric.getId());
        version.setVersionStatus(ScenarioVersionStatus.DRAFT.name());
        version.setCreatedBy(LoginUserContext.getUserId());
        scenarioVersionMapper.insert(version);
        return toScenarioVO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScenarioVersionVO publishScenarioVersion(Long versionId) {
        InterviewScenarioVersion version = requireScenario(versionId);
        validateScript(readStoredJson(version.getScriptJson(), "Stored scenario script is invalid"));
        InterviewRubricVersion rubric = requireRubric(version.getRubricVersionId());
        if (RubricVersionStatus.DRAFT.name().equals(rubric.getVersionStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Bound rubric version has not been published");
        }
        lockScenarioCode(version.getScenarioCode());
        version = requireScenarioForUpdate(versionId);
        validateScript(readStoredJson(version.getScriptJson(), "Stored scenario script is invalid"));
        if (ScenarioVersionStatus.PUBLISHED.name().equals(version.getVersionStatus())) {
            return toScenarioVO(version);
        }
        LocalDateTime publishedAt = LocalDateTime.now();
        if (scenarioVersionMapper.update(null, new LambdaUpdateWrapper<InterviewScenarioVersion>()
                .eq(InterviewScenarioVersion::getId, version.getId())
                .eq(InterviewScenarioVersion::getVersionStatus, ScenarioVersionStatus.DRAFT.name())
                .set(InterviewScenarioVersion::getVersionStatus, ScenarioVersionStatus.PUBLISHED.name())
                .set(InterviewScenarioVersion::getPublishedAt, publishedAt)) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Scenario version publish state changed");
        }
        version.setVersionStatus(ScenarioVersionStatus.PUBLISHED.name());
        version.setPublishedAt(publishedAt);
        scenarioVersionMapper.update(null, new LambdaUpdateWrapper<InterviewScenarioVersion>()
                .eq(InterviewScenarioVersion::getScenarioCode, version.getScenarioCode())
                .eq(InterviewScenarioVersion::getVersionStatus, ScenarioVersionStatus.PUBLISHED.name())
                .ne(InterviewScenarioVersion::getId, version.getId())
                .set(InterviewScenarioVersion::getVersionStatus, ScenarioVersionStatus.RETIRED.name()));
        return toScenarioVO(version);
    }

    @Override
    public List<ScenarioVersionVO> listScenarioVersions(String scenarioCode) {
        return scenarioVersionMapper.selectList(new LambdaQueryWrapper<InterviewScenarioVersion>()
                        .eq(InterviewScenarioVersion::getScenarioCode, normalizeCode(scenarioCode, "scenarioCode"))
                        .orderByDesc(InterviewScenarioVersion::getVersionNo))
                .stream().map(this::toScenarioVO).toList();
    }

    @Override
    public ScenarioVersionVO getCurrentScenario(String scenarioCode) {
        InterviewScenarioVersion version = scenarioVersionMapper.selectOne(
                new LambdaQueryWrapper<InterviewScenarioVersion>()
                        .eq(InterviewScenarioVersion::getScenarioCode, normalizeCode(scenarioCode, "scenarioCode"))
                        .eq(InterviewScenarioVersion::getVersionStatus, ScenarioVersionStatus.PUBLISHED.name())
                        .orderByDesc(InterviewScenarioVersion::getVersionNo)
                        .last("limit 1"));
        if (version == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Published scenario does not exist");
        }
        return toScenarioVO(version);
    }

    @Override
    public ScenarioVersionVO getPublishedScenarioVersion(Long scenarioVersionId) {
        InterviewScenarioVersion version = requireScenario(scenarioVersionId);
        if (!ScenarioVersionStatus.PUBLISHED.name().equals(version.getVersionStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only published scenario versions can be selected");
        }
        return toScenarioVO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = DuplicateKeyException.class)
    public ScenarioBindingVO bindScenario(Long sessionId, ScenarioBindingCreateDTO dto) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);
        String bindingSource = defaultBindingSource(dto.getBindingSource());
        InterviewScenarioBinding existing = findBinding(sessionId, userId);
        if (existing != null) {
            validateBindingReplay(existing, dto.getScenarioVersionId(), bindingSource);
            return toBindingVO(existing);
        }
        InterviewScenarioVersion scenario = requireScenario(dto.getScenarioVersionId());
        if (!ScenarioVersionStatus.PUBLISHED.name().equals(scenario.getVersionStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only published scenario versions can be bound");
        }
        InterviewScenarioBinding binding = new InterviewScenarioBinding();
        binding.setUserId(userId);
        binding.setSessionId(sessionId);
        binding.setScenarioVersionId(scenario.getId());
        binding.setRubricVersionId(scenario.getRubricVersionId());
        binding.setBindingSource(bindingSource);
        try {
            bindingMapper.insert(binding);
        } catch (DuplicateKeyException ex) {
            InterviewScenarioBinding winner = findBindingForUpdate(sessionId, userId);
            if (winner == null) {
                throw ex;
            }
            validateBindingReplay(winner, scenario.getId(), bindingSource);
            return toBindingVO(winner);
        }
        return toBindingVO(binding);
    }

    @Override
    public ScenarioBindingVO getBinding(Long sessionId) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);
        InterviewScenarioBinding binding = findBinding(sessionId, userId);
        if (binding == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview scenario is not bound");
        }
        return toBindingVO(binding);
    }

    private int nextRubricVersion(String code) {
        InterviewRubricVersion latest = rubricVersionMapper.selectOne(
                new LambdaQueryWrapper<InterviewRubricVersion>()
                        .eq(InterviewRubricVersion::getRubricCode, code)
                        .orderByDesc(InterviewRubricVersion::getVersionNo)
                        .last("limit 1 for update"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private int nextScenarioVersion(String code) {
        InterviewScenarioVersion latest = scenarioVersionMapper.selectOne(
                new LambdaQueryWrapper<InterviewScenarioVersion>()
                        .eq(InterviewScenarioVersion::getScenarioCode, code)
                        .orderByDesc(InterviewScenarioVersion::getVersionNo)
                        .last("limit 1 for update"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private void validateDimensions(JsonNode dimensions) {
        if (dimensions == null || !dimensions.isArray() || dimensions.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Rubric dimensions must be a non-empty array");
        }
        Set<String> codes = new HashSet<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (JsonNode dimension : dimensions) {
            if (!dimension.isObject()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Rubric dimensions must be JSON objects");
            }
            String code = dimension.path("code").asText("").trim();
            if (!StringUtils.hasText(code) || !codes.add(code.toUpperCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Rubric dimension codes must be present and unique");
            }
            if (!dimension.has("weight") || !dimension.path("weight").isNumber()
                    || dimension.path("weight").asDouble() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Rubric dimension weight must be positive");
            }
            totalWeight = totalWeight.add(dimension.path("weight").decimalValue());
        }
        if (totalWeight.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Rubric dimension weights must sum to 100");
        }
    }

    private void validateScript(JsonNode script) {
        if (script == null || !script.isObject()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Scenario script must be a JSON object");
        }
        int questionBudget = requirePositiveInt(script, "questionBudget", "Scenario question budget");
        int timeBudget = requirePositiveInt(script, "timeBudgetMinutes", "Scenario time budget");
        JsonNode stages = script.path("stages");
        if (!stages.isArray() || stages.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Scenario script stages must be a non-empty array");
        }
        Set<String> stageCodes = new HashSet<>();
        long totalQuestions = 0;
        long totalMinutes = 0;
        for (JsonNode stage : stages) {
            if (!stage.isObject()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Scenario stages must be JSON objects");
            }
            String stageCode = stage.path("code").asText("").trim();
            if (!StringUtils.hasText(stageCode)
                    || !stageCodes.add(stageCode.toUpperCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Scenario stage codes must be present and unique");
            }
            totalQuestions += requirePositiveInt(stage, "questionCount", "Scenario stage question budget");
            totalMinutes += requirePositiveInt(stage, "timeBudgetMinutes", "Scenario stage time budget");

            JsonNode followUpNode = stage.path("maxFollowUpCount");
            if (!followUpNode.isMissingNode()
                    && (!followUpNode.isIntegralNumber() || !followUpNode.canConvertToInt())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Scenario follow-up limit must be an integer");
            }
            int followUpLimit = followUpNode.asInt(0);
            if (followUpLimit < 0 || followUpLimit > 5) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Scenario follow-up limit must be between 0 and 5");
            }
            JsonNode allowFollowUpNode = stage.path("allowFollowUp");
            if (!allowFollowUpNode.isMissingNode() && !allowFollowUpNode.isBoolean()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Scenario allowFollowUp must be a boolean");
            }
            boolean allowFollowUp = allowFollowUpNode.isMissingNode() || allowFollowUpNode.booleanValue();
            if (!allowFollowUp && followUpLimit != 0) {
                throw new BusinessException(
                        ErrorCode.PARAM_ERROR, "Disabled scenario follow-up must have a zero limit");
            }
        }
        if (totalQuestions > questionBudget) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR, "Scenario stage question budgets exceed the total budget");
        }
        if (totalMinutes > timeBudget) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR, "Scenario stage time budgets exceed the total budget");
        }
    }

    private int requirePositiveInt(JsonNode parent, String field, String label) {
        JsonNode value = parent.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + " must be a positive integer");
        }
        return value.asInt();
    }

    private InterviewRubricVersion requireRubric(Long id) {
        InterviewRubricVersion version = id == null ? null : rubricVersionMapper.selectById(id);
        if (version == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Rubric version does not exist");
        }
        return version;
    }

    private InterviewScenarioVersion requireScenario(Long id) {
        InterviewScenarioVersion version = id == null ? null : scenarioVersionMapper.selectById(id);
        if (version == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Scenario version does not exist");
        }
        return version;
    }

    private InterviewRubricVersion requireRubricForUpdate(Long id) {
        InterviewRubricVersion version = id == null ? null : rubricVersionMapper.selectOne(
                new LambdaQueryWrapper<InterviewRubricVersion>()
                        .eq(InterviewRubricVersion::getId, id)
                        .last("limit 1 for update"));
        if (version == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Rubric version does not exist");
        }
        return version;
    }

    private InterviewScenarioVersion requireScenarioForUpdate(Long id) {
        InterviewScenarioVersion version = id == null ? null : scenarioVersionMapper.selectOne(
                new LambdaQueryWrapper<InterviewScenarioVersion>()
                        .eq(InterviewScenarioVersion::getId, id)
                        .last("limit 1 for update"));
        if (version == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Scenario version does not exist");
        }
        return version;
    }

    private void requireOwnedSession(Long sessionId, Long userId) {
        InterviewSession session = sessionMapper.selectOne(new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getId, sessionId)
                .eq(InterviewSession::getUserId, userId)
                .eq(InterviewSession::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview session does not exist");
        }
    }

    private InterviewScenarioBinding findBinding(Long sessionId, Long userId) {
        return bindingMapper.selectOne(new LambdaQueryWrapper<InterviewScenarioBinding>()
                .eq(InterviewScenarioBinding::getSessionId, sessionId)
                .eq(InterviewScenarioBinding::getUserId, userId)
                .eq(InterviewScenarioBinding::getDeleted, CommonConstants.NO)
                .last("limit 1"));
    }

    private InterviewScenarioBinding findBindingForUpdate(Long sessionId, Long userId) {
        return bindingMapper.selectOne(new LambdaQueryWrapper<InterviewScenarioBinding>()
                .eq(InterviewScenarioBinding::getSessionId, sessionId)
                .eq(InterviewScenarioBinding::getUserId, userId)
                .eq(InterviewScenarioBinding::getDeleted, CommonConstants.NO)
                .last("limit 1 for update"));
    }

    private void validateBindingReplay(
            InterviewScenarioBinding binding, Long scenarioVersionId, String bindingSource) {
        if (!binding.getScenarioVersionId().equals(scenarioVersionId)
                || !bindingSource.equals(binding.getBindingSource())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview scenario binding is immutable");
        }
    }

    private void lockRubricCode(String code) {
        rubricVersionMapper.selectOne(new LambdaQueryWrapper<InterviewRubricVersion>()
                .eq(InterviewRubricVersion::getRubricCode, code)
                .orderByDesc(InterviewRubricVersion::getVersionNo)
                .last("limit 1 for update"));
    }

    private void lockScenarioCode(String code) {
        scenarioVersionMapper.selectOne(new LambdaQueryWrapper<InterviewScenarioVersion>()
                .eq(InterviewScenarioVersion::getScenarioCode, code)
                .orderByDesc(InterviewScenarioVersion::getVersionNo)
                .last("limit 1 for update"));
    }

    private Long requireUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private String normalizeCode(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, field + " is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9_-]{1,63}")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, field + " format is invalid");
        }
        return normalized;
    }

    private String defaultLocale(String locale) {
        return StringUtils.hasText(locale) ? locale.trim() : "zh-CN";
    }

    private String defaultBindingSource(String source) {
        return StringUtils.hasText(source) ? source.trim().toUpperCase(Locale.ROOT) : "USER_SELECTED";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "JSON content is invalid");
        }
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Stored version JSON is invalid");
        }
    }

    private JsonNode readStoredJson(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        try {
            JsonNode result = objectMapper.readTree(value);
            if (result == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, message);
            }
            return result;
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
    }

    private RubricVersionVO toRubricVO(InterviewRubricVersion version) {
        RubricVersionVO vo = new RubricVersionVO();
        vo.setRubricVersionId(version.getId());
        vo.setRubricCode(version.getRubricCode());
        vo.setVersionNo(version.getVersionNo());
        vo.setRubricName(version.getRubricName());
        vo.setDescription(version.getDescription());
        vo.setLocale(version.getLocale());
        vo.setDimensions(readJson(version.getDimensionsJson()));
        vo.setVersionStatus(version.getVersionStatus());
        vo.setPublishedAt(version.getPublishedAt());
        vo.setCreatedAt(version.getCreatedAt());
        return vo;
    }

    private ScenarioVersionVO toScenarioVO(InterviewScenarioVersion version) {
        ScenarioVersionVO vo = new ScenarioVersionVO();
        vo.setScenarioVersionId(version.getId());
        vo.setScenarioCode(version.getScenarioCode());
        vo.setVersionNo(version.getVersionNo());
        vo.setScenarioName(version.getScenarioName());
        vo.setDescription(version.getDescription());
        vo.setLocale(version.getLocale());
        vo.setScript(readJson(version.getScriptJson()));
        vo.setRubricVersionId(version.getRubricVersionId());
        vo.setVersionStatus(version.getVersionStatus());
        vo.setPublishedAt(version.getPublishedAt());
        vo.setCreatedAt(version.getCreatedAt());
        return vo;
    }

    private ScenarioBindingVO toBindingVO(InterviewScenarioBinding binding) {
        ScenarioBindingVO vo = new ScenarioBindingVO();
        vo.setBindingId(binding.getId());
        vo.setSessionId(binding.getSessionId());
        vo.setScenarioVersionId(binding.getScenarioVersionId());
        vo.setRubricVersionId(binding.getRubricVersionId());
        vo.setBindingSource(binding.getBindingSource());
        vo.setCreatedAt(binding.getCreatedAt());
        return vo;
    }
}
