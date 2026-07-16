package com.codecoachai.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.dto.InterviewRemediationCreateDTO;
import com.codecoachai.interview.domain.entity.InterviewRemediation;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.vo.CreateInterviewVO;
import com.codecoachai.interview.domain.vo.InterviewRemediationOptionVO;
import com.codecoachai.interview.domain.vo.InterviewRemediationOptionsVO;
import com.codecoachai.interview.domain.vo.InterviewRemediationVO;
import com.codecoachai.interview.mapper.InterviewRemediationMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.service.InterviewRemediationService;
import com.codecoachai.interview.service.InterviewService;
import com.codecoachai.interview.support.InterviewReportTrustPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InterviewRemediationServiceImpl implements InterviewRemediationService {

    private static final String STRENGTH_NORMAL = "NORMAL";
    private static final String STRENGTH_STRONG = "STRONG";
    private static final String STATUS_CREATING = "CREATING";
    private static final String STATUS_CREATED = "CREATED";
    private static final int MAX_OPTIONS = 10;

    private final InterviewRemediationMapper remediationMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewService interviewService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewRemediationVO create(InterviewRemediationCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        List<Long> requirementIds = normalizeRequirementIds(dto.getSourceRequirementIds());
        String purpose = dto.getPracticePurpose().trim();
        String idempotencyKey = dto.getIdempotencyKey().trim();
        String strength = Boolean.TRUE.equals(dto.getStrongRemediation()) ? STRENGTH_STRONG : STRENGTH_NORMAL;

        InterviewRemediation existing = findByIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            validateReplayPayload(existing, dto.getSourceReportId(), requirementIds, purpose, strength);
            return toVO(existing, true, null);
        }

        InterviewReport sourceReport = reportMapper.selectById(dto.getSourceReportId());
        if (sourceReport == null
                || CommonConstants.YES.equals(sourceReport.getDeleted())
                || !userId.equals(sourceReport.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "来源面试报告不存在或不可用");
        }
        if (!InterviewReportTrustPolicy.isAvailableForRemediation(sourceReport)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "来源面试报告尚未成功生成，不能创建复练");
        }
        InterviewSession sourceSession = sessionMapper.selectById(sourceReport.getSessionId());
        if (sourceSession == null
                || CommonConstants.YES.equals(sourceSession.getDeleted())
                || !userId.equals(sourceSession.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "来源面试场次不存在或不可用");
        }
        if (STRENGTH_STRONG.equals(strength)
                && !InterviewReportTrustPolicy.isTrustedForFormalAction(sourceReport)) {
            String reason = InterviewReportTrustPolicy.isSampleInsufficient(sourceReport)
                    ? "来源报告样本不足，不能生成强复练"
                    : "来源报告可信度不足，不能生成强复练";
            throw new BusinessException(ErrorCode.PARAM_ERROR, reason);
        }

        InterviewRemediation remediation = new InterviewRemediation();
        remediation.setUserId(userId);
        remediation.setSourceReportId(sourceReport.getId());
        remediation.setSourceSessionId(sourceSession.getId());
        remediation.setTargetJobId(sourceSession.getTargetJobId());
        remediation.setSourceRequirementIds(writeJson(requirementIds));
        remediation.setPracticePurpose(purpose);
        remediation.setRemediationStrength(strength);
        remediation.setRubricVersion(sourceReport.getRubricVersion());
        remediation.setStatus(STATUS_CREATING);
        remediation.setIdempotencyKey(idempotencyKey);
        try {
            remediationMapper.insert(remediation);
        } catch (DuplicateKeyException ex) {
            InterviewRemediation concurrent = findByIdempotencyKey(userId, idempotencyKey);
            if (concurrent == null) {
                throw ex;
            }
            validateReplayPayload(concurrent, sourceReport.getId(), requirementIds, purpose, strength);
            return toVO(concurrent, true, null);
        }

        CreateInterviewVO interview = interviewService.create(
                buildInterviewRequest(sourceSession, sourceReport, requirementIds, purpose, strength));
        if (interview == null || interview.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "复练面试创建失败");
        }

        InterviewSession targetPatch = new InterviewSession();
        targetPatch.setId(interview.getId());
        targetPatch.setSourceReportId(sourceReport.getId());
        targetPatch.setSourceRequirementIds(writeJson(requirementIds));
        targetPatch.setPracticePurpose(purpose);
        targetPatch.setRemediationStrength(strength);
        if (sessionMapper.updateById(targetPatch) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "复练来源上下文保存失败");
        }

        remediation.setTargetSessionId(interview.getId());
        remediation.setStatus(STATUS_CREATED);
        if (remediationMapper.updateById(remediation) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "复练记录保存失败");
        }
        return toVO(remediation, false, interview);
    }

    @Override
    public InterviewRemediationOptionsVO options(Long interviewId) {
        Long userId = SecurityAssert.requireLoginUserId();
        InterviewSession session = sessionMapper.selectById(interviewId);
        if (session == null
                || CommonConstants.YES.equals(session.getDeleted())
                || !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试场次不存在或不可用");
        }
        InterviewReport report = reportMapper.selectOne(new LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getSessionId, session.getId())
                .eq(InterviewReport::getUserId, userId)
                .eq(InterviewReport::getDeleted, CommonConstants.NO)
                .orderByDesc(InterviewReport::getId)
                .last("limit 1"));

        InterviewRemediationOptionsVO result = new InterviewRemediationOptionsVO();
        result.setInterviewId(session.getId());
        result.setTargetJobId(session.getTargetJobId());
        result.setOptions(List.of());
        result.setRemediationCreated(false);
        if (report == null) {
            result.setTrustStatus("UNAVAILABLE");
            result.setRemediationAvailable(false);
            result.setRemediationUnavailableReason(InterviewReportTrustPolicy.remediationUnavailableReason(null));
            result.setStrongRemediationAvailable(false);
            result.setStrongRemediationUnavailableReason(
                    InterviewReportTrustPolicy.strongRemediationUnavailableReason(null));
            return result;
        }

        boolean strongRemediationAvailable = InterviewReportTrustPolicy.isTrustedForFormalAction(report);
        result.setSourceReportId(report.getId());
        result.setRubricVersion(report.getRubricVersion());
        result.setTrustStatus(reportTrustStatus(report));
        result.setStrongRemediationAvailable(strongRemediationAvailable);
        result.setStrongRemediationUnavailableReason(
                InterviewReportTrustPolicy.strongRemediationUnavailableReason(report));
        applyExistingRemediationState(result, findLatestBySourceReport(userId, report.getId()));
        if (!InterviewReportTrustPolicy.isAvailableForRemediation(report)) {
            result.setRemediationAvailable(false);
            result.setRemediationUnavailableReason(InterviewReportTrustPolicy.remediationUnavailableReason(report));
            return result;
        }

        List<Long> reportRequirementIds = reportRequirementIds(report, session);
        Map<String, InterviewRemediationOptionVO> options = new LinkedHashMap<>();
        appendQuestionOptions(options, report, reportRequirementIds);
        appendRubricOptions(options, report, reportRequirementIds);
        appendRequirementOptions(options, report, reportRequirementIds);
        appendTextOptions(options, "WEAK_POINT", "复练报告弱项",
                textItems(report.getWeakPoints()), reportRequirementIds);
        appendTextOptions(options, "MAIN_PROBLEM", "复练主要问题",
                textItems(firstText(report.getMainProblems(), report.getWeaknesses())), reportRequirementIds);
        appendTextOptions(options, "PROJECT_PROBLEM", "复练项目表达问题",
                textItems(report.getProjectProblems()), reportRequirementIds);
        options.values().forEach(option -> option.setStrongRemediation(strongRemediationAvailable));

        result.setRemediationAvailable(true);
        result.setRemediationUnavailableReason(null);
        result.setOptions(options.values().stream().limit(MAX_OPTIONS).toList());
        return result;
    }

    private void appendQuestionOptions(
            Map<String, InterviewRemediationOptionVO> options,
            InterviewReport report,
            List<Long> fallbackRequirementIds) {
        for (JsonNode item : arrayNodes(report.getQaReview())) {
            Integer score = score(item);
            if (score == null || !isFailedScore(score)) {
                continue;
            }
            String question = firstNodeText(item, "questionContent", "question", "title");
            String improvement = firstNodeText(item,
                    "improvementSuggestion", "suggestion", "comment", "aiComment", "weakness");
            String purpose = firstText(improvement,
                    StringUtils.hasText(question) ? "重新回答并补强：" + question : "复练本轮低分题目");
            addOption(options, "FAILED_QUESTION",
                    StringUtils.hasText(question) ? truncate(question, 80) : "复练低分题目",
                    purpose,
                    firstNodeText(item, "comment", "aiComment", "evidenceSummary", "userAnswer"),
                    preferredRequirementIds(item, fallbackRequirementIds));
        }
    }

    private void appendRubricOptions(
            Map<String, InterviewRemediationOptionVO> options,
            InterviewReport report,
            List<Long> fallbackRequirementIds) {
        for (JsonNode item : arrayNodes(report.getRubricScores())) {
            Integer score = score(item);
            if (score == null || score > 3) {
                continue;
            }
            String dimension = firstNodeText(item, "dimension", "name", "title");
            String improvement = firstNodeText(item,
                    "improvementSuggestion", "suggestion", "comment", "evidenceSummary");
            String title = StringUtils.hasText(dimension) ? "补强 " + dimension : "补强低分能力维度";
            addOption(options, "WEAK_DIMENSION", title,
                    firstText(improvement, title),
                    firstNodeText(item, "evidenceSummary", "comment"),
                    preferredRequirementIds(item, fallbackRequirementIds));
        }
    }

    private void appendRequirementOptions(
            Map<String, InterviewRemediationOptionVO> options,
            InterviewReport report,
            List<Long> fallbackRequirementIds) {
        for (JsonNode item : arrayNodes(report.getAdviceEvidence())) {
            List<Long> requirementIds = positiveIds(item);
            if (requirementIds.isEmpty()) {
                continue;
            }
            String title = firstNodeText(item, "title", "requirementName", "name");
            String purpose = firstNodeText(item, "content", "improvementSuggestion", "description", "title");
            addOption(options, "REQUIREMENT",
                    firstText(title, "补强岗位要求"),
                    firstText(purpose, "针对报告识别出的岗位要求缺口进行复练"),
                    firstNodeText(item, "evidenceSummary", "sampleWarning"),
                    requirementIds.isEmpty() ? fallbackRequirementIds : requirementIds);
        }
    }

    private void appendTextOptions(
            Map<String, InterviewRemediationOptionVO> options,
            String reasonType,
            String title,
            List<String> items,
            List<Long> requirementIds) {
        for (String item : items) {
            addOption(options, reasonType, title, item, item, requirementIds);
        }
    }

    private void addOption(
            Map<String, InterviewRemediationOptionVO> options,
            String reasonType,
            String title,
            String purpose,
            String evidence,
            List<Long> requirementIds) {
        String normalizedPurpose = truncate(purpose, 500);
        if (!StringUtils.hasText(normalizedPurpose) || options.size() >= MAX_OPTIONS) {
            return;
        }
        String dedupeKey = normalizedPurpose.toLowerCase(Locale.ROOT);
        if (options.containsKey(dedupeKey)) {
            return;
        }
        InterviewRemediationOptionVO option = new InterviewRemediationOptionVO();
        option.setOptionKey(reasonType + "-" + (options.size() + 1));
        option.setReasonType(reasonType);
        option.setTitle(truncate(firstText(title, "专项复练"), 128));
        option.setDescription(normalizedPurpose);
        option.setEvidence(truncate(evidence, 500));
        option.setSourceRequirementIds(normalizeRequirementIds(requirementIds));
        option.setPracticePurpose(normalizedPurpose);
        option.setStrongRemediation(true);
        options.put(dedupeKey, option);
    }

    private List<Long> reportRequirementIds(InterviewReport report, InterviewSession session) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(readList(session.getSourceRequirementIds(), new TypeReference<List<Long>>() {
        }));
        ids.addAll(positiveIds(readTree(report.getQaReview())));
        ids.addAll(positiveIds(readTree(report.getRubricScores())));
        ids.addAll(positiveIds(readTree(report.getAdviceEvidence())));
        ids.addAll(positiveIds(readTree(report.getAbilityProfileUpdates())));
        return normalizeRequirementIds(new ArrayList<>(ids));
    }

    private List<Long> preferredRequirementIds(JsonNode item, List<Long> fallback) {
        List<Long> ids = positiveIds(item);
        return ids.isEmpty() ? fallback : ids;
    }

    private List<Long> positiveIds(JsonNode node) {
        Set<Long> result = new LinkedHashSet<>();
        collectPositiveIds(node, null, result);
        return normalizeRequirementIds(new ArrayList<>(result));
    }

    private void collectPositiveIds(JsonNode node, String fieldName, Set<Long> result) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    collectPositiveIds(entry.getValue(), entry.getKey(), result));
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectPositiveIds(child, fieldName, result);
            }
            return;
        }
        String normalizedField = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        if ((normalizedField.endsWith("requirementid")
                || normalizedField.endsWith("requirementids")
                || normalizedField.equals("sourcerequirementids"))
                && node.canConvertToLong()
                && node.asLong() > 0) {
            result.add(node.asLong());
        }
    }

    private List<JsonNode> arrayNodes(String json) {
        JsonNode root = readTree(json);
        if (root == null || !root.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        root.forEach(result::add);
        return result;
    }

    private JsonNode readTree(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> textItems(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(value);
            if (root.isArray()) {
                List<String> result = new ArrayList<>();
                for (JsonNode item : root) {
                    String text = item.isTextual() ? item.asText() : firstNodeText(item, "title", "content", "description");
                    if (StringUtils.hasText(text)) {
                        result.add(text.trim());
                    }
                }
                return result;
            }
        } catch (Exception ignored) {
            // Plain report text is handled below.
        }
        return List.of(value.trim());
    }

    private Integer score(JsonNode item) {
        if (item == null) {
            return null;
        }
        for (String field : List.of("score", "aiScore")) {
            JsonNode value = item.get(field);
            if (value != null && value.canConvertToInt()) {
                return value.asInt();
            }
        }
        return null;
    }

    private boolean isFailedScore(int score) {
        return score <= 5 ? score <= 3 : score < 60;
    }

    private String firstNodeText(JsonNode item, String... fields) {
        if (item == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = item.get(field);
            if (value != null && value.isValueNode() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private CreateInterviewDTO buildInterviewRequest(
            InterviewSession source,
            InterviewReport report,
            List<Long> requirementIds,
            String purpose,
            String strength) {
        CreateInterviewDTO request = new CreateInterviewDTO();
        request.setMode(source.getMode());
        request.setInterviewMode(source.getMode());
        request.setResumeId(source.getResumeId());
        request.setApplicationId(source.getApplicationId());
        request.setApplicationPackageId(source.getApplicationPackageId() == null
                ? null : source.getApplicationPackageId().toString());
        request.setTargetJobId(source.getTargetJobId());
        request.setJdAnalysisId(source.getJdAnalysisId());
        request.setResumeVersionId(source.getResumeVersionId());
        request.setSkillProfileId(source.getSkillProfileId());
        request.setMatchReportId(source.getMatchReportId());
        request.setTitle(remediationTitle(source.getTitle()));
        request.setMaxQuestionCount(source.getMaxQuestionCount());
        request.setTargetPosition(source.getTargetPosition());
        request.setExperienceLevel(source.getExperienceLevel());
        request.setIndustryTemplateId(source.getIndustryTemplateId());
        request.setIndustryDirection(source.getIndustryDirection());
        request.setDifficulty(source.getDifficulty());
        request.setInterviewerStyle(source.getInterviewerStyle());
        request.setBasedOnResume(source.getBasedOnResume());
        request.setTrainingScene(source.getTrainingScene());
        request.setTargetSkillDomain(source.getTargetSkillDomain());
        request.setTargetSkillCodes(readList(source.getTargetSkillCodes(), new TypeReference<>() {
        }));
        request.setTargetLevel(source.getTargetLevel());
        request.setProjectEvidenceIds(readList(source.getProjectEvidenceIds(), new TypeReference<>() {
        }));
        request.setFollowUpIntensity(STRENGTH_STRONG.equals(strength)
                ? "HIGH" : source.getFollowUpIntensity());
        request.setPracticeMode(STRENGTH_STRONG.equals(strength) ? "STRONG_REMEDIATION" : "REMEDIATION");
        request.setRecommendationSource("INTERVIEW_REPORT");
        request.setRecommendationReason("sourceReportId=" + report.getId()
                + ", sourceRequirementIds=" + requirementIds + ", practicePurpose=" + purpose);
        return request;
    }

    private InterviewRemediation findByIdempotencyKey(Long userId, String idempotencyKey) {
        return remediationMapper.selectOne(new LambdaQueryWrapper<InterviewRemediation>()
                .eq(InterviewRemediation::getUserId, userId)
                .eq(InterviewRemediation::getIdempotencyKey, idempotencyKey)
                .eq(InterviewRemediation::getDeleted, CommonConstants.NO)
                .last("limit 1"));
    }

    private InterviewRemediation findLatestBySourceReport(Long userId, Long sourceReportId) {
        return remediationMapper.selectOne(new LambdaQueryWrapper<InterviewRemediation>()
                .eq(InterviewRemediation::getUserId, userId)
                .eq(InterviewRemediation::getSourceReportId, sourceReportId)
                .eq(InterviewRemediation::getDeleted, CommonConstants.NO)
                .orderByDesc(InterviewRemediation::getCreatedAt)
                .orderByDesc(InterviewRemediation::getId)
                .last("limit 1"));
    }

    private void applyExistingRemediationState(
            InterviewRemediationOptionsVO result, InterviewRemediation remediation) {
        if (remediation == null) {
            return;
        }
        result.setRemediationId(remediation.getId());
        result.setRemediationTargetSessionId(remediation.getTargetSessionId());
        result.setRemediationStatus(remediation.getStatus());
        result.setRemediationCreated(STATUS_CREATED.equalsIgnoreCase(remediation.getStatus())
                && remediation.getTargetSessionId() != null);
    }

    private String reportTrustStatus(InterviewReport report) {
        if (InterviewReportTrustPolicy.isFallbackOrUntrusted(report)) {
            return "FALLBACK";
        }
        return InterviewReportTrustPolicy.isTrustedForFormalAction(report) ? "VERIFIED" : "PARTIAL";
    }

    private void validateReplayPayload(
            InterviewRemediation existing,
            Long sourceReportId,
            List<Long> requirementIds,
            String purpose,
            String strength) {
        List<Long> savedIds = readList(existing.getSourceRequirementIds(), new TypeReference<>() {
        });
        if (!sourceReportId.equals(existing.getSourceReportId())
                || !requirementIds.equals(normalizeRequirementIds(savedIds))
                || !purpose.equals(existing.getPracticePurpose())
                || !strength.equals(existing.getRemediationStrength())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "幂等键已被不同的复练请求占用");
        }
    }

    private InterviewRemediationVO toVO(
            InterviewRemediation remediation, boolean replay, CreateInterviewVO interview) {
        InterviewRemediationVO vo = new InterviewRemediationVO();
        vo.setId(remediation.getId());
        vo.setSourceReportId(remediation.getSourceReportId());
        vo.setSourceSessionId(remediation.getSourceSessionId());
        vo.setTargetSessionId(remediation.getTargetSessionId());
        vo.setTargetJobId(remediation.getTargetJobId());
        vo.setSourceRequirementIds(readList(remediation.getSourceRequirementIds(), new TypeReference<>() {
        }));
        vo.setPracticePurpose(remediation.getPracticePurpose());
        vo.setRemediationStrength(remediation.getRemediationStrength());
        vo.setRubricVersion(remediation.getRubricVersion());
        vo.setStatus(remediation.getStatus());
        vo.setIdempotentReplay(replay);
        vo.setInterview(interview);
        return vo;
    }

    private List<Long> normalizeRequirementIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .sorted()
                .toList();
    }

    private String remediationTitle(String sourceTitle) {
        String title = "复练：" + (StringUtils.hasText(sourceTitle) ? sourceTitle.trim() : "模拟面试");
        return title.length() <= 128 ? title : title.substring(0, 128);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "复练上下文序列化失败");
        }
    }

    private <T> List<T> readList(String value, TypeReference<List<T>> type) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            List<T> result = objectMapper.readValue(value, type);
            return result == null ? List.of() : new ArrayList<>(result);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
