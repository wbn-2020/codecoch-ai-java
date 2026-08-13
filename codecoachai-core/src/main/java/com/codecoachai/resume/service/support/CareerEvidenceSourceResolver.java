package com.codecoachai.resume.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.entity.JobApplicationPackage;
import com.codecoachai.resume.domain.entity.JobApplicationPackageSnapshot;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectEvidenceVersion;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.domain.entity.ProjectStoryGeneration;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageCreateDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageResultWriteDTO;
import com.codecoachai.resume.domain.vo.CareerEvidenceUsageVO;
import com.codecoachai.resume.careercontact.entity.CareerActivity;
import com.codecoachai.resume.careercontact.mapper.CareerActivityMapper;
import com.codecoachai.resume.careerinterview.entity.CareerInterviewProcess;
import com.codecoachai.resume.careerinterview.entity.CareerInterviewRound;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewProcessMapper;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewRoundMapper;
import com.codecoachai.resume.careeroffer.entity.CareerOfferDecision;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferDecisionMapper;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobApplicationPackageMapper;
import com.codecoachai.resume.mapper.JobApplicationPackageSnapshotMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceVersionMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectStoryGenerationMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class CareerEvidenceSourceResolver {

    private static final String PROJECT_EVIDENCE = "PROJECT_EVIDENCE";
    private static final String PROJECT_SKILL_EVIDENCE = "PROJECT_SKILL_EVIDENCE";
    private static final String PROJECT_STORY_GENERATION = "PROJECT_STORY_GENERATION";
    private static final String APPLICATION_PACKAGE_SNAPSHOT = "APPLICATION_PACKAGE_SNAPSHOT";
    private static final String RESUME_VERSION = "RESUME_VERSION";
    private static final String MATCH_REPORT = "MATCH_REPORT";

    private final JobApplicationMapper applicationMapper;
    private final ProjectEvidenceMapper projectEvidenceMapper;
    private final ProjectEvidenceVersionMapper projectVersionMapper;
    private final ProjectSkillEvidenceMapper skillEvidenceMapper;
    private final ProjectStoryGenerationMapper storyGenerationMapper;
    private final JobApplicationPackageMapper packageMapper;
    private final JobApplicationPackageSnapshotMapper packageSnapshotMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final ResumeJobMatchReportMapper matchReportMapper;
    private final JobApplicationEventMapper applicationEventMapper;
    private final CareerInterviewProcessMapper interviewProcessMapper;
    private final CareerInterviewRoundMapper interviewRoundMapper;
    private final CareerOfferDecisionMapper offerDecisionMapper;
    private final CareerActivityMapper activityMapper;
    private final ObjectMapper objectMapper;

    public JobApplication ownedApplication(Long userId, Long applicationId) {
        if (userId == null || applicationId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "投递记录参数无效。");
        }
        JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .isNull(JobApplication::getArchivedAt)
                .last("LIMIT 1"));
        if (application == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "投递记录不存在或无权访问。");
        }
        return application;
    }

    public AssetResolution resolveAsset(Long userId, JobApplication application,
                                        CareerEvidenceUsageCreateDTO request) {
        if (request == null || application == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "证据使用请求无效。");
        }
        String assetType = normalize(request.getAssetType());
        Long assetId = request.getAssetId();
        if (assetId == null || !StringUtils.hasText(request.getAssetVersion())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "资产版本不能为空。");
        }
        AssetResolution resolution = switch (assetType) {
            case PROJECT_EVIDENCE -> projectEvidence(userId, assetId, request.getAssetVersion());
            case PROJECT_SKILL_EVIDENCE -> projectSkill(userId, assetId, request.getAssetVersion());
            case PROJECT_STORY_GENERATION -> storyGeneration(userId, assetId, request.getAssetVersion());
            case APPLICATION_PACKAGE_SNAPSHOT ->
                    packageSnapshot(userId, application, assetId, request.getAssetVersion());
            case RESUME_VERSION -> resumeVersion(userId, assetId, request.getAssetVersion());
            case MATCH_REPORT -> matchReport(userId, assetId, request.getAssetVersion());
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的证据资产类型。");
        };
        if (resolution.targetJobId() != null && application.getTargetJobId() != null
                && !resolution.targetJobId().equals(application.getTargetJobId())) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "证据资产与投递岗位不一致。");
        }
        return resolution;
    }

    public EventResolution resolveEvent(Long userId, JobApplication application,
                                        CareerEvidenceUsageResultWriteDTO request) {
        if (request == null || application == null
                || !StringUtils.hasText(request.getEventType()) || request.getEventId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结果来源事件不能为空。");
        }
        String eventType = normalize(request.getEventType());
        // CAMPAIGN_REVIEW_SNAPSHOT was previously listed here but had no validation branch, so every
        // request fell through to the RESOURCE_RELATION_CONFLICT below — a "supported" type that always
        // failed. Its source lives in the AI service (campaignreview) and cannot be validated from the
        // resume service, so it is dropped from the accepted set until cross-service validation exists;
        // callers now get an honest "unsupported type" PARAM_ERROR instead of a misleading conflict.
        if (!java.util.Set.of("APPLICATION_EVENT", "INTERVIEW_ROUND", "OFFER_DECISION",
                "CONTACT_ACTIVITY").contains(eventType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的结果来源事件类型。");
        }
        if ("APPLICATION_EVENT".equals(eventType)) {
            JobApplicationEvent event = applicationEventMapper.selectOne(new LambdaQueryWrapper<JobApplicationEvent>()
                    .eq(JobApplicationEvent::getId, request.getEventId())
                    .eq(JobApplicationEvent::getUserId, userId)
                    .eq(JobApplicationEvent::getApplicationId, application.getId())
                    .eq(JobApplicationEvent::getDeleted, CommonConstants.NO)
                    .last("LIMIT 1"));
            if (event == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "结果来源事件不存在或无权访问。");
            }
            Map<String, Object> eventPayload = new LinkedHashMap<>();
            eventPayload.put("eventType", event.getEventType());
            eventPayload.put("eventTime", event.getEventTime());
            eventPayload.put("summary", event.getSummary());
            eventPayload.put("reviewJson", event.getReviewJson());
            String sourceHash = hash(eventPayload);
            return new EventResolution(eventType, event.getId(),
                    event.getUpdatedAt() == null ? String.valueOf(event.getId()) : event.getUpdatedAt().toString(),
                    sourceHash, event.getEventTime(), event.getSummary());
        }
        if ("INTERVIEW_ROUND".equals(eventType)) {
            CareerInterviewRound round = interviewRoundMapper.selectOwned(request.getEventId(), userId);
            CareerInterviewProcess process = round == null || round.getProcessId() == null
                    ? null : interviewProcessMapper.selectOwned(round.getProcessId(), userId);
            if (round == null || process == null || !java.util.Objects.equals(
                    process.getApplicationId(), application.getId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "面试轮次不存在或不属于当前投递。");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("roundId", round.getId());
            payload.put("roundNo", round.getRoundNo());
            payload.put("status", round.getStatus());
            payload.put("resultSummary", round.getResultSummary());
            payload.put("nextStep", round.getNextStep());
            return new EventResolution(eventType, round.getId(), String.valueOf(round.getLockVersion()),
                    hash(payload), request.getOccurredAt(), round.getResultSummary());
        }
        if ("OFFER_DECISION".equals(eventType)) {
            CareerOfferDecision decision = offerDecisionMapper.selectOwned(request.getEventId(), userId);
            if (decision == null || (application.getCampaignId() != null
                    && !java.util.Objects.equals(application.getCampaignId(), decision.getCampaignId()))) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Offer 决策不存在或不属于当前投递。");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("decisionId", decision.getId());
            payload.put("status", decision.getStatus());
            payload.put("outcome", decision.getOutcome());
            payload.put("selectedOfferId", decision.getSelectedOfferId());
            return new EventResolution(eventType, decision.getId(), String.valueOf(decision.getLockVersion()),
                    hash(payload), request.getOccurredAt(), decision.getOutcome());
        }
        if ("CONTACT_ACTIVITY".equals(eventType)) {
            CareerActivity activity = activityMapper.selectOne(new LambdaQueryWrapper<CareerActivity>()
                    .eq(CareerActivity::getId, request.getEventId())
                    .eq(CareerActivity::getUserId, userId)
                    .eq(CareerActivity::getApplicationId, application.getId())
                    .eq(CareerActivity::getDeleted, CommonConstants.NO)
                    .last("LIMIT 1"));
            if (activity == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "联系人活动不存在或不属于当前投递。");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("activityType", activity.getActivityType());
            payload.put("channelType", activity.getChannelType());
            payload.put("subject", activity.getSubject());
            payload.put("summary", activity.getSummary());
            payload.put("occurredAt", activity.getOccurredAt());
            return new EventResolution(eventType, activity.getId(),
                    activity.getUpdatedAt() == null ? String.valueOf(activity.getId())
                            : activity.getUpdatedAt().toString(),
                    hash(payload), activity.getOccurredAt(), activity.getSummary());
        }
        throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                "复盘快照来源需要由所属服务完成校验后再记录。");
    }

    public JobApplicationPackageSnapshot ownedPackageSnapshotForApplication(
            Long userId, Long applicationId, Long snapshotId) {
        JobApplication application = ownedApplication(userId, applicationId);
        JobApplicationPackageSnapshot snapshot = packageSnapshotMapper.selectOwned(snapshotId, userId);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "投递包快照不存在或无权访问。");
        }
        JobApplicationPackage root = packageMapper.selectOne(new LambdaQueryWrapper<JobApplicationPackage>()
                .eq(JobApplicationPackage::getId, snapshot.getPackageId())
                .eq(JobApplicationPackage::getUserId, userId)
                .eq(JobApplicationPackage::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        assertPackageBelongsToApplication(root, application);
        return snapshot;
    }

    public CareerEvidenceUsageVO.SourceRef sourceRef(AssetResolution asset) {
        CareerEvidenceUsageVO.SourceRef source = new CareerEvidenceUsageVO.SourceRef();
        source.setSourceType(asset.assetType());
        source.setSourceId(asset.assetId());
        source.setSourceVersion(asset.assetVersion());
        source.setSourceHash(asset.sourceHash());
        source.setSummary(asset.summary());
        source.setSourceUpdatedAt(asset.sourceUpdatedAt());
        source.setObservedAt(asset.observedAt());
        return source;
    }

    private AssetResolution projectEvidence(Long userId, Long assetId, String version) {
        ProjectEvidence project = projectEvidenceMapper.selectOne(new LambdaQueryWrapper<ProjectEvidence>()
                .eq(ProjectEvidence::getId, assetId)
                .eq(ProjectEvidence::getUserId, userId)
                .eq(ProjectEvidence::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (project == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "项目证据不存在或无权访问。");
        }
        ProjectEvidenceVersion row = projectVersionMapper.selectOwnedVersion(
                assetId, userId, parseVersion(version));
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目证据版本不存在。");
        }
        return new AssetResolution(PROJECT_EVIDENCE, assetId, String.valueOf(row.getVersionNo()),
                row.getContentHash(), row.getContentHash(), project.getTargetJobId(),
                row.getCreatedAt(), row.getCreatedAt(), project.getTitle());
    }

    private AssetResolution projectSkill(Long userId, Long assetId, String version) {
        ProjectSkillEvidence skill = skillEvidenceMapper.selectOne(new LambdaQueryWrapper<ProjectSkillEvidence>()
                .eq(ProjectSkillEvidence::getId, assetId)
                .eq(ProjectSkillEvidence::getUserId, userId)
                .eq(ProjectSkillEvidence::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (skill == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "技能证据不存在或无权访问。");
        }
        ProjectEvidence project = projectEvidenceMapper.selectOne(new LambdaQueryWrapper<ProjectEvidence>()
                .eq(ProjectEvidence::getId, skill.getProjectEvidenceId())
                .eq(ProjectEvidence::getUserId, userId)
                .eq(ProjectEvidence::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (project == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "技能证据所属项目不存在。");
        }
        ProjectEvidenceVersion row = projectVersionMapper.selectOwnedVersion(
                project.getId(), userId, parseVersion(version));
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "技能证据对应的项目版本不存在。");
        }
        JsonNode skillSnapshot = findSkillSnapshot(row, skill.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectVersionHash", row.getContentHash());
        payload.put("skill", skillSnapshot);
        String contentHash = hash(payload);
        String skillName = skillSnapshot.path("skillName").asText(null);
        return new AssetResolution(PROJECT_SKILL_EVIDENCE, assetId, String.valueOf(row.getVersionNo()),
                row.getContentHash(), contentHash, project.getTargetJobId(),
                row.getCreatedAt(), row.getCreatedAt(),
                StringUtils.hasText(skillName) ? skillName : "技能证据");
    }

    private AssetResolution storyGeneration(Long userId, Long assetId, String version) {
        ProjectStoryGeneration row = storyGenerationMapper.selectOne(new LambdaQueryWrapper<ProjectStoryGeneration>()
                .eq(ProjectStoryGeneration::getId, assetId)
                .eq(ProjectStoryGeneration::getUserId, userId)
                .eq(ProjectStoryGeneration::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (row == null || !matchesVersion(version, String.valueOf(row.getId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "项目故事版本不存在或无权访问。");
        }
        String payload = firstText(row.getStructuredResultJson(), row.getResultText());
        String hash = ResumeArtifactHashes.sha256(payload == null ? "" : payload);
        return new AssetResolution(PROJECT_STORY_GENERATION, assetId, String.valueOf(row.getId()),
                hash, hash, row.getTargetJobId(), row.getCreatedAt(), row.getUpdatedAt(), "项目故事生成稿");
    }

    private AssetResolution packageSnapshot(Long userId, JobApplication application,
                                            Long assetId, String version) {
        JobApplicationPackageSnapshot row = packageSnapshotMapper.selectOwned(assetId, userId);
        if (row == null || !matchesVersion(version, String.valueOf(row.getSnapshotVersion()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "投递包快照不存在或无权访问。");
        }
        JobApplicationPackage root = packageMapper.selectOne(new LambdaQueryWrapper<JobApplicationPackage>()
                .eq(JobApplicationPackage::getId, row.getPackageId())
                .eq(JobApplicationPackage::getUserId, userId)
                .eq(JobApplicationPackage::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        assertPackageBelongsToApplication(root, application);
        String hash = row.getContentHash();
        return new AssetResolution(APPLICATION_PACKAGE_SNAPSHOT, assetId,
                String.valueOf(row.getSnapshotVersion()), hash, hash, root.getTargetJobId(),
                row.getCreatedAt(), row.getCapturedAt(), "投递包快照");
    }

    private AssetResolution resumeVersion(Long userId, Long assetId, String version) {
        ResumeVersion row = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, assetId)
                .eq(ResumeVersion::getUserId, userId)
                .eq(ResumeVersion::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (row == null || !matchesVersion(version, String.valueOf(row.getVersionNo()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "简历版本不存在或无权访问。");
        }
        String hash = ResumeArtifactHashes.sha256(row.getSnapshotJson());
        return new AssetResolution(RESUME_VERSION, assetId, String.valueOf(row.getVersionNo()),
                hash, hash, null, row.getCreatedAt(), row.getUpdatedAt(), row.getVersionName());
    }

    private AssetResolution matchReport(Long userId, Long assetId, String version) {
        ResumeJobMatchReport row = matchReportMapper.selectOne(new LambdaQueryWrapper<ResumeJobMatchReport>()
                .eq(ResumeJobMatchReport::getId, assetId)
                .eq(ResumeJobMatchReport::getUserId, userId)
                .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (row == null || !matchesVersion(version, String.valueOf(row.getId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "匹配报告不存在或无权访问。");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("overallScore", row.getOverallScore());
        payload.put("summary", row.getSummary());
        payload.put("rawResultJson", row.getRawResultJson());
        payload.put("resumeVersionId", row.getResumeVersionId());
        String hash = hash(payload);
        return new AssetResolution(MATCH_REPORT, assetId, String.valueOf(row.getId()),
                hash, hash, row.getTargetJobId(), row.getCreatedAt(), row.getUpdatedAt(), "岗位匹配报告");
    }

    private Integer parseVersion(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "资产版本无效。");
        }
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "V", 0, 1)) {
            normalized = normalized.substring(1);
        }
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "资产版本无效。");
        }
    }

    private JsonNode findSkillSnapshot(ProjectEvidenceVersion version, Long skillId) {
        try {
            JsonNode skills = objectMapper.readTree(version.getSnapshotJson())
                    .path("skillEvidences");
            if (skills.isArray()) {
                for (JsonNode item : skills) {
                    if (item.path("id").canConvertToLong()
                            && Objects.equals(item.path("id").longValue(), skillId)) {
                        return item;
                    }
                }
            }
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "项目证据版本快照无法解析。");
        }
        throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                "技能证据不属于指定的项目证据版本。");
    }

    private void assertPackageBelongsToApplication(
            JobApplicationPackage root, JobApplication application) {
        if (root == null || application == null) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "投递包快照与当前投递记录不一致。");
        }
        if (root.getApplicationId() != null) {
            if (!Objects.equals(root.getApplicationId(), application.getId())) {
                throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "投递包快照与当前投递记录不一致。");
            }
            return;
        }
        if (root.getTargetJobId() == null || application.getTargetJobId() == null
                || !Objects.equals(root.getTargetJobId(), application.getTargetJobId())) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "未绑定投递的投递包快照与当前岗位不一致。");
        }
    }

    private boolean matchesVersion(String requested, String actual) {
        return "CURRENT".equalsIgnoreCase(requested.trim())
                || requested.trim().equals(actual)
                || ("V" + actual).equalsIgnoreCase(requested.trim());
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String hash(Object value) {
        try {
            return ResumeArtifactHashes.sha256(objectMapper.writeValueAsString(value));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "证据来源哈希计算失败。");
        }
    }

    public record AssetResolution(String assetType, Long assetId, String assetVersion,
                                  String sourceHash, String contentHash, Long targetJobId,
                                  LocalDateTime observedAt, LocalDateTime sourceUpdatedAt,
                                  String summary) {
    }

    public record EventResolution(String eventType, Long eventId, String sourceVersion,
                                  String sourceHash, LocalDateTime occurredAt, String summary) {
    }
}
