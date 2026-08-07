package com.codecoachai.resume.careerresearch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.careerresearch.dto.CareerResearchSnapshotGenerateDTO;
import com.codecoachai.resume.careerresearch.dto.CareerResearchSourceCreateDTO;
import com.codecoachai.resume.careerresearch.dto.CareerResearchSourceVersionCreateDTO;
import com.codecoachai.resume.careerresearch.entity.CareerResearchReport;
import com.codecoachai.resume.careerresearch.entity.CareerResearchSnapshot;
import com.codecoachai.resume.careerresearch.entity.CareerResearchSnapshotSource;
import com.codecoachai.resume.careerresearch.entity.CareerResearchSource;
import com.codecoachai.resume.careerresearch.entity.CareerResearchSourceVersion;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchReportMapper;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchSnapshotMapper;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchSnapshotSourceMapper;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchSourceMapper;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchSourceVersionMapper;
import com.codecoachai.resume.careerresearch.service.CareerResearchCitationValidator;
import com.codecoachai.resume.careerresearch.service.CareerResearchClaimManager;
import com.codecoachai.resume.careerresearch.service.CareerResearchClaimManager.Claim;
import com.codecoachai.resume.careerresearch.service.CareerResearchGenerator;
import com.codecoachai.resume.careerresearch.service.CareerResearchGenerator.GenerationRequest;
import com.codecoachai.resume.careerresearch.service.CareerResearchGenerator.SourceInput;
import com.codecoachai.resume.careerresearch.service.CareerResearchService;
import com.codecoachai.resume.careerresearch.vo.CareerResearchDraft;
import com.codecoachai.resume.careerresearch.vo.CareerResearchSnapshotVO;
import com.codecoachai.resume.careerresearch.vo.CareerResearchSourceVO;
import com.codecoachai.resume.careerresearch.vo.CareerResearchSourceVersionVO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CareerResearchServiceImpl implements CareerResearchService {
    private static final int MAX_CONTENT_LENGTH = 1_000_000;
    private final CareerResearchSourceMapper sourceMapper;
    private final CareerResearchSourceVersionMapper sourceVersionMapper;
    private final CareerResearchReportMapper reportMapper;
    private final CareerResearchSnapshotMapper snapshotMapper;
    private final CareerResearchSnapshotSourceMapper snapshotSourceMapper;
    private final JobApplicationMapper applicationMapper;
    private final CareerResearchClaimManager claimManager;
    private final CareerResearchCitationValidator citationValidator;
    private final ObjectProvider<CareerResearchGenerator> generatorProvider;
    private final ObjectMapper objectMapper;

    @Override
    public List<CareerResearchSourceVO> listSources(Long applicationId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(userId, applicationId);
        return sourceMapper.selectList(new LambdaQueryWrapper<CareerResearchSource>()
                        .eq(CareerResearchSource::getUserId, userId)
                        .eq(CareerResearchSource::getApplicationId, applicationId)
                        .eq(CareerResearchSource::getDeleted, CommonConstants.NO)
                        .orderByDesc(CareerResearchSource::getCreatedAt)
                        .last("LIMIT 100"))
                .stream().map(this::toSourceView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerResearchSourceVO createSource(Long applicationId,
                                               CareerResearchSourceCreateDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(userId, applicationId);
        CareerResearchSource source = new CareerResearchSource();
        source.setUserId(userId);
        source.setApplicationId(applicationId);
        source.setSourceType(normalize(request.getSourceType(), 40));
        source.setTitle(requireText(request.getTitle(), "研究来源标题不能为空", 200));
        source.setOfficialUrl(truncate(request.getOfficialUrl(), 1000));
        source.setExternalRef(truncate(request.getExternalRef(), 255));
        source.setStatus("ACTIVE");
        source.setLockVersion(0);
        sourceMapper.insert(source);
        CareerResearchSourceVersion version = insertVersion(
                source, request.getContent(), request.getContentSummary(), request.getCapturedAt());
        if (sourceMapper.advanceCurrentVersion(source.getId(), userId, 0, version.getId()) != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "研究来源版本占用失败");
        }
        source.setCurrentVersionId(version.getId());
        source.setLockVersion(1);
        return toSourceView(source);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerResearchSourceVersionVO addSourceVersion(
            Long sourceId, CareerResearchSourceVersionCreateDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerResearchSource source = ownedActiveSource(userId, sourceId);
        String content = requireContent(request.getContent());
        String contentHash = hash(normalizeContent(content));
        CareerResearchSourceVersion existing =
                sourceVersionMapper.selectByContentHash(userId, sourceId, contentHash);
        if (existing != null) {
            return toVersionView(existing);
        }
        CareerResearchSourceVersion version = insertVersion(
                source, content, request.getContentSummary(), request.getCapturedAt());
        if (sourceMapper.advanceCurrentVersion(
                sourceId, userId, source.getLockVersion(), version.getId()) != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "研究来源已被其他请求更新，请刷新后重试");
        }
        return toVersionView(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deactivateSource(Long sourceId) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerResearchSource source = ownedActiveSource(userId, sourceId);
        source.setStatus("INACTIVE");
        source.setLockVersion(defaultLock(source.getLockVersion()) + 1);
        sourceMapper.updateById(source);
    }

    @Override
    public CareerResearchSnapshotVO generateSnapshot(Long applicationId,
                                                     CareerResearchSnapshotGenerateDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(userId, applicationId);
        List<CareerResearchSourceVersion> versions = activeCurrentVersions(userId, applicationId);
        String sourceSetHash = sourceSetHash(versions);
        CareerResearchSnapshot reusable = claimManager.findReusableSnapshot(
                userId, applicationId, sourceSetHash);
        if (reusable != null) {
            return getSnapshot(reusable.getId());
        }
        String claimToken = hash(userId + "|RESEARCH|" + applicationId + "|"
                + (StringUtils.hasText(request.getIdempotencyKey())
                ? request.getIdempotencyKey().trim() : UUID.randomUUID()));
        CareerResearchSnapshot replay = snapshotMapper.selectOne(
                new LambdaQueryWrapper<CareerResearchSnapshot>()
                        .eq(CareerResearchSnapshot::getUserId, userId)
                        .eq(CareerResearchSnapshot::getApplicationId, applicationId)
                        .eq(CareerResearchSnapshot::getGenerationClaimToken, claimToken)
                        .eq(CareerResearchSnapshot::getDeleted, CommonConstants.NO)
                        .last("LIMIT 1"));
        if (replay != null) {
            return ownedSnapshot(userId, replay.getId());
        }
        Claim claim = claimManager.claim(userId, applicationId, claimToken);
        try {
            CareerResearchDraft draft = generateDraft(userId, applicationId, versions);
            Long snapshotId = claimManager.complete(
                    claim, userId, applicationId, sourceSetHash, writeJson(draft),
                    draft.getConfidenceLevel(), draft.getFallbackReason(),
                    draft.getAiCallLogId(), versions);
            return getSnapshot(snapshotId);
        } catch (RuntimeException ex) {
            claimManager.fail(claim, userId);
            throw ex;
        }
    }

    @Override
    public CareerResearchSnapshotVO latestSnapshot(Long applicationId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(userId, applicationId);
        CareerResearchReport report = reportMapper.selectOne(new LambdaQueryWrapper<CareerResearchReport>()
                .eq(CareerResearchReport::getUserId, userId)
                .eq(CareerResearchReport::getApplicationId, applicationId)
                .eq(CareerResearchReport::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (report == null || report.getCurrentSnapshotId() == null) {
            return null;
        }
        return ownedSnapshot(userId, report.getCurrentSnapshotId());
    }

    @Override
    public CareerResearchSnapshotVO getSnapshot(Long snapshotId) {
        return ownedSnapshot(SecurityAssert.requireLoginUserId(), snapshotId);
    }

    private CareerResearchDraft generateDraft(Long userId, Long applicationId,
                                              List<CareerResearchSourceVersion> versions) {
        if (versions.isEmpty()) {
            return ruleFrame("当前没有可用的研究来源");
        }
        CareerResearchGenerator generator = generatorProvider.getIfAvailable();
        if (generator == null) {
            return ruleFrame("AI 研究生成能力暂不可用");
        }
        List<SourceInput> inputs = new ArrayList<>();
        for (CareerResearchSourceVersion version : versions) {
            CareerResearchSource source = sourceMapper.selectById(version.getSourceId());
            inputs.add(new SourceInput(
                    source.getId(), version.getId(), source.getSourceType(), source.getTitle(),
                    version.getContentSummary(), version.getContentText()));
        }
        CareerResearchDraft generated;
        try {
            generated = generator.generate(new GenerationRequest(userId, applicationId, inputs));
        } catch (RuntimeException ex) {
            return ruleFrame("AI 研究生成失败");
        }
        CareerResearchCitationValidator.ValidationResult validation =
                citationValidator.validate(userId, generated, versions);
        if (!validation.valid()) {
            return ruleFrame(validation.reason());
        }
        if (!StringUtils.hasText(generated.getConfidenceLevel())) {
            generated.setConfidenceLevel("MEDIUM");
        }
        return generated;
    }

    private CareerResearchDraft ruleFrame(String reason) {
        CareerResearchDraft draft = new CareerResearchDraft();
        draft.setUnknowns(List.of("公司和岗位事实尚未通过有效来源核验。"));
        draft.setSourceLimits(List.of(reason));
        draft.setQuestionsToVerify(List.of(
                "需要向招聘方确认哪些职责和成功标准？",
                "哪些公司与岗位信息仍需要官方来源核验？"));
        draft.setPreparationFocus(List.of("在获得可引用来源前，只使用用户已确认的事实。"));
        draft.setRiskSignals(List.of());
        draft.setSourceRefs(List.of());
        draft.setConfidenceLevel("LOW");
        draft.setFallbackReason(reason);
        return draft;
    }

    private List<CareerResearchSourceVersion> activeCurrentVersions(Long userId, Long applicationId) {
        List<CareerResearchSource> sources = sourceMapper.selectList(
                new LambdaQueryWrapper<CareerResearchSource>()
                        .eq(CareerResearchSource::getUserId, userId)
                        .eq(CareerResearchSource::getApplicationId, applicationId)
                        .eq(CareerResearchSource::getStatus, "ACTIVE")
                        .eq(CareerResearchSource::getDeleted, CommonConstants.NO)
                        .isNotNull(CareerResearchSource::getCurrentVersionId)
                        .orderByAsc(CareerResearchSource::getId)
                        .last("LIMIT 100"));
        List<CareerResearchSourceVersion> versions = new ArrayList<>();
        for (CareerResearchSource source : sources) {
            CareerResearchSourceVersion version = sourceVersionMapper.selectOne(
                    new LambdaQueryWrapper<CareerResearchSourceVersion>()
                            .eq(CareerResearchSourceVersion::getId, source.getCurrentVersionId())
                            .eq(CareerResearchSourceVersion::getSourceId, source.getId())
                            .eq(CareerResearchSourceVersion::getUserId, userId)
                            .eq(CareerResearchSourceVersion::getDeleted, CommonConstants.NO)
                            .last("LIMIT 1"));
            if (version == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "有效研究来源的当前版本无效");
            }
            versions.add(version);
        }
        return versions;
    }

    private CareerResearchSourceVersion insertVersion(CareerResearchSource source, String rawContent,
                                                      String contentSummary, LocalDateTime capturedAt) {
        String content = requireContent(rawContent);
        String contentHash = hash(normalizeContent(content));
        CareerResearchSourceVersion existing = sourceVersionMapper.selectByContentHash(
                source.getUserId(), source.getId(), contentHash);
        if (existing != null) {
            return existing;
        }
        CareerResearchSourceVersion version = new CareerResearchSourceVersion();
        version.setUserId(source.getUserId());
        version.setSourceId(source.getId());
        version.setVersionToken(UUID.randomUUID().toString());
        version.setContentHash(contentHash);
        version.setContentSummary(truncate(contentSummary, 2000));
        version.setContentText(content);
        version.setCapturedAt(capturedAt == null ? LocalDateTime.now() : capturedAt);
        try {
            sourceVersionMapper.insert(version);
        } catch (DuplicateKeyException ex) {
            CareerResearchSourceVersion winner = sourceVersionMapper.selectByContentHash(
                    source.getUserId(), source.getId(), contentHash);
            if (winner != null) {
                return winner;
            }
            throw ex;
        }
        return version;
    }

    private CareerResearchSourceVO toSourceView(CareerResearchSource source) {
        CareerResearchSourceVO view = new CareerResearchSourceVO();
        view.setId(source.getId());
        view.setApplicationId(source.getApplicationId());
        view.setSourceType(source.getSourceType());
        view.setTitle(source.getTitle());
        view.setOfficialUrl(source.getOfficialUrl());
        view.setExternalRef(source.getExternalRef());
        view.setStatus(source.getStatus());
        view.setCurrentVersionId(source.getCurrentVersionId());
        if (source.getCurrentVersionId() != null) {
            CareerResearchSourceVersion version = sourceVersionMapper.selectById(source.getCurrentVersionId());
            if (version != null && Objects.equals(version.getUserId(), source.getUserId())
                    && Objects.equals(version.getSourceId(), source.getId())) {
                view.setCurrentVersion(toVersionView(version));
            }
        }
        view.setCreatedAt(source.getCreatedAt());
        view.setUpdatedAt(source.getUpdatedAt());
        return view;
    }

    private CareerResearchSourceVersionVO toVersionView(CareerResearchSourceVersion version) {
        CareerResearchSourceVersionVO view = new CareerResearchSourceVersionVO();
        view.setId(version.getId());
        view.setSourceId(version.getSourceId());
        view.setVersionToken(version.getVersionToken());
        view.setContentHash(version.getContentHash());
        view.setContentSummary(version.getContentSummary());
        view.setCapturedAt(version.getCapturedAt());
        view.setCreatedAt(version.getCreatedAt());
        return view;
    }

    private CareerResearchSnapshotVO ownedSnapshot(Long userId, Long snapshotId) {
        CareerResearchSnapshot snapshot = snapshotMapper.selectOne(
                new LambdaQueryWrapper<CareerResearchSnapshot>()
                        .eq(CareerResearchSnapshot::getId, snapshotId)
                        .eq(CareerResearchSnapshot::getUserId, userId)
                        .eq(CareerResearchSnapshot::getDeleted, CommonConstants.NO)
                        .last("LIMIT 1"));
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "研究快照不存在");
        }
        CareerResearchSnapshotVO view = new CareerResearchSnapshotVO();
        view.setId(snapshot.getId());
        view.setReportId(snapshot.getReportId());
        view.setApplicationId(snapshot.getApplicationId());
        view.setSourceSetHash(snapshot.getSourceSetHash());
        view.setResearch(readDraft(snapshot.getSnapshotJson()));
        view.setFallbackReason(snapshot.getFallbackReason());
        view.setCreatedAt(snapshot.getCreatedAt());
        view.setSourceVersionIds(snapshotSourceMapper.selectList(
                        new LambdaQueryWrapper<CareerResearchSnapshotSource>()
                                .eq(CareerResearchSnapshotSource::getUserId, userId)
                                .eq(CareerResearchSnapshotSource::getSnapshotId, snapshotId)
                                .eq(CareerResearchSnapshotSource::getDeleted, CommonConstants.NO)
                                .orderByAsc(CareerResearchSnapshotSource::getId))
                .stream().map(CareerResearchSnapshotSource::getSourceVersionId).toList());
        return view;
    }

    private CareerResearchSource ownedActiveSource(Long userId, Long sourceId) {
        CareerResearchSource source = sourceMapper.selectOne(new LambdaQueryWrapper<CareerResearchSource>()
                .eq(CareerResearchSource::getId, sourceId)
                .eq(CareerResearchSource::getUserId, userId)
                .eq(CareerResearchSource::getStatus, "ACTIVE")
                .eq(CareerResearchSource::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (source == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "有效研究来源不存在");
        }
        return source;
    }

    private JobApplication ownedApplication(Long userId, Long applicationId) {
        JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (application == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "机会不存在");
        }
        return application;
    }

    private String sourceSetHash(List<CareerResearchSourceVersion> versions) {
        String canonical = versions.stream()
                .sorted(Comparator.comparing(CareerResearchSourceVersion::getSourceId))
                .map(version -> version.getSourceId() + ":" + version.getId() + ":" + version.getContentHash())
                .reduce("", (left, right) -> left + "|" + right);
        return hash(canonical);
    }

    private String requireContent(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "研究来源内容不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "研究来源内容不能超过 1000000 个字符");
        }
        return normalized;
    }

    private String normalizeContent(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private String requireText(String value, String message, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        return truncate(value.trim(), maxLength);
    }

    private String normalize(String value, int maxLength) {
        return requireText(value, "研究来源类型不能为空", maxLength).toUpperCase(Locale.ROOT);
    }

    private int defaultLock(Integer value) {
        return value == null ? 0 : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String writeJson(CareerResearchDraft draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Research snapshot is invalid");
        }
    }

    private CareerResearchDraft readDraft(String json) {
        try {
            return objectMapper.readValue(json, CareerResearchDraft.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Research snapshot is invalid");
        }
    }
}
