package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.dto.ResumeSuggestionCreateDTO;
import com.codecoachai.resume.domain.dto.ResumeSuggestionBatchAcceptDTO;
import com.codecoachai.resume.domain.dto.ResumeSuggestionDecisionDTO;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeSuggestion;
import com.codecoachai.resume.domain.entity.ResumeSuggestionDecision;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.vo.ResumeSuggestionDecisionVO;
import com.codecoachai.resume.domain.vo.ResumeSuggestionVO;
import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.codecoachai.resume.mapper.ResumeSuggestionDecisionMapper;
import com.codecoachai.resume.mapper.ResumeSuggestionMapper;
import com.codecoachai.resume.service.ResumeSearchSyncOutboxService;
import com.codecoachai.resume.service.ResumeSuggestionService;
import com.codecoachai.resume.service.support.ResumeVersionSnapshotManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ResumeSuggestionServiceImpl implements ResumeSuggestionService {

    private static final TypeReference<List<Map<String, Object>>> EVIDENCE_REFS_TYPE = new TypeReference<>() {
    };

    private final ResumeSuggestionMapper suggestionMapper;
    private final ResumeSuggestionDecisionMapper decisionMapper;
    private final ResumeVersionSnapshotManager snapshotManager;
    private final ResumeSearchSyncOutboxService searchSyncOutboxService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeSuggestionVO create(ResumeSuggestionCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        ResumeVersion version = snapshotManager.ownedVersion(dto.getSourceResumeVersionId(), userId);
        ObjectNode snapshot = snapshotManager.readSnapshot(version);
        String sourceText = snapshotManager.sectionText(snapshot, dto.getSectionKey());
        validateAnchor(sourceText, dto);

        ResumeSuggestion suggestion = new ResumeSuggestion();
        suggestion.setUserId(userId);
        suggestion.setResumeId(version.getResumeId());
        suggestion.setSourceResumeVersionId(version.getId());
        suggestion.setSourceType(StringUtils.hasText(dto.getSourceType()) ? dto.getSourceType().trim().toUpperCase(Locale.ROOT) : "AI");
        suggestion.setSourceId(dto.getSourceId());
        suggestion.setSourceVersion(dto.getSourceVersion());
        suggestion.setSectionKey(dto.getSectionKey());
        suggestion.setSectionId(dto.getSectionId());
        suggestion.setFieldPath(dto.getFieldPath());
        suggestion.setAnchorStart(dto.getAnchorStart());
        suggestion.setAnchorEnd(dto.getAnchorEnd());
        suggestion.setAnchorTextHash(ResumeArtifactHashes.sha256(dto.getOriginalText()));
        suggestion.setOriginalText(dto.getOriginalText());
        suggestion.setSuggestedText(dto.getSuggestedText());
        suggestion.setEvidenceRefsJson(writeJson(dto.getEvidenceReferences()));
        suggestion.setRiskLevel(normalizeRiskLevel(dto.getRiskLevel()));
        suggestion.setRationale(dto.getRationale());
        suggestion.setStatus("PENDING");
        suggestion.setDecisionVersion(0);
        suggestionMapper.insert(suggestion);
        return toVO(suggestion, List.of());
    }

    @Override
    public List<ResumeSuggestionVO> list(Long resumeId, String status) {
        Long userId = SecurityAssert.requireLoginUserId();
        String normalizedStatus = StringUtils.hasText(status) ? normalizeStatus(status) : null;
        return suggestionMapper.selectList(new LambdaQueryWrapper<ResumeSuggestion>()
                        .eq(ResumeSuggestion::getUserId, userId)
                        .eq(resumeId != null, ResumeSuggestion::getResumeId, resumeId)
                        .eq(normalizedStatus != null, ResumeSuggestion::getStatus, normalizedStatus)
                        .eq(ResumeSuggestion::getDeleted, CommonConstants.NO)
                        .orderByDesc(ResumeSuggestion::getCreatedAt)
                        .orderByDesc(ResumeSuggestion::getId))
                .stream().map(item -> toVO(item, List.of())).toList();
    }

    @Override
    public ResumeSuggestionVO detail(Long suggestionId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ResumeSuggestion suggestion = ownedSuggestion(suggestionId, userId);
        List<ResumeSuggestionDecision> decisions = decisionMapper.selectList(
                new LambdaQueryWrapper<ResumeSuggestionDecision>()
                        .eq(ResumeSuggestionDecision::getUserId, userId)
                        .eq(ResumeSuggestionDecision::getSuggestionId, suggestionId)
                        .eq(ResumeSuggestionDecision::getDeleted, CommonConstants.NO)
                        .orderByAsc(ResumeSuggestionDecision::getDecisionVersion));
        return toVO(suggestion, decisions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeSuggestionVO decide(Long suggestionId, ResumeSuggestionDecisionDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        String decisionType = dto.getDecisionType().trim().toUpperCase(Locale.ROOT);
        ResumeSuggestionDecision existing = decisionMapper.selectOne(
                new LambdaQueryWrapper<ResumeSuggestionDecision>()
                        .eq(ResumeSuggestionDecision::getUserId, userId)
                        .eq(ResumeSuggestionDecision::getIdempotencyKey, dto.getIdempotencyKey())
                        .eq(ResumeSuggestionDecision::getDeleted, CommonConstants.NO)
                        .last("LIMIT 1"));
        if (existing != null) {
            if (!Objects.equals(existing.getSuggestionId(), suggestionId)) {
                throw new BusinessException(
                        ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "Idempotency key was used by another suggestion");
            }
            ResumeSuggestion replayedSuggestion = ownedSuggestion(suggestionId, userId);
            if (!decisionType.equals(existing.getDecisionType())) {
                throw new BusinessException(
                        ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "Idempotency key payload does not match the original decision");
            }
            if ("ACCEPT".equals(decisionType) && StringUtils.hasText(dto.getEditedText())
                    && !Objects.equals(dto.getEditedText().trim(), acceptedText(replayedSuggestion))) {
                throw new BusinessException(
                        ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "Idempotency key payload does not match the accepted text");
            }
            return detail(suggestionId);
        }

        ResumeSuggestion suggestion = ownedSuggestion(suggestionId, userId);
        String fromStatus = suggestion.getStatus();
        String toStatus;
        ResumeVersion resultVersion = null;
        if ("ACCEPT".equals(decisionType)) {
            requireStatus(suggestion, "PENDING");
            String acceptedText = StringUtils.hasText(dto.getEditedText())
                    ? dto.getEditedText().trim()
                    : suggestion.getSuggestedText();
            if (!StringUtils.hasText(acceptedText)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Accepted suggestion text must not be blank");
            }
            resultVersion = applySuggestion(suggestion, false, acceptedText);
            toStatus = "ACCEPTED";
            suggestion.setAppliedResumeVersionId(resultVersion.getId());
            suggestion.setAcceptedText(acceptedText);
        } else if ("REJECT".equals(decisionType)) {
            requireStatus(suggestion, "PENDING");
            toStatus = "REJECTED";
        } else if ("UNDO".equals(decisionType)) {
            requireStatus(suggestion, "ACCEPTED");
            resultVersion = applySuggestion(suggestion, true, acceptedText(suggestion));
            toStatus = "UNDONE";
            suggestion.setUndoResumeVersionId(resultVersion.getId());
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Decision type must be ACCEPT, REJECT, or UNDO");
        }

        int nextDecisionVersion = suggestion.getDecisionVersion() == null ? 1 : suggestion.getDecisionVersion() + 1;
        int updated = suggestionMapper.update(null, new LambdaUpdateWrapper<ResumeSuggestion>()
                .eq(ResumeSuggestion::getId, suggestion.getId())
                .eq(ResumeSuggestion::getUserId, userId)
                .eq(ResumeSuggestion::getStatus, fromStatus)
                .eq(ResumeSuggestion::getDecisionVersion, suggestion.getDecisionVersion())
                .set(ResumeSuggestion::getStatus, toStatus)
                .set(ResumeSuggestion::getDecisionVersion, nextDecisionVersion)
                .set(ResumeSuggestion::getAcceptedText, suggestion.getAcceptedText())
                .set(ResumeSuggestion::getAppliedResumeVersionId, suggestion.getAppliedResumeVersionId())
                .set(ResumeSuggestion::getUndoResumeVersionId, suggestion.getUndoResumeVersionId())
                .set(ResumeSuggestion::getDecidedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "Suggestion decision changed concurrently");
        }

        ResumeSuggestionDecision decision = new ResumeSuggestionDecision();
        decision.setUserId(userId);
        decision.setSuggestionId(suggestionId);
        decision.setDecisionType(decisionType);
        decision.setFromStatus(fromStatus);
        decision.setToStatus(toStatus);
        decision.setDecisionVersion(nextDecisionVersion);
        decision.setResultResumeVersionId(resultVersion == null ? null : resultVersion.getId());
        decision.setIdempotencyKey(dto.getIdempotencyKey());
        decision.setNote(dto.getNote());
        decisionMapper.insert(decision);

        suggestion.setStatus(toStatus);
        suggestion.setDecisionVersion(nextDecisionVersion);
        suggestion.setDecidedAt(LocalDateTime.now());
        if (resultVersion != null) {
            searchSyncOutboxService.enqueue(suggestion.getResumeId(), userId, ResumeSearchSyncOutboxService.OP_UPSERT);
        }
        return detail(suggestionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ResumeSuggestionVO> acceptLowRiskBatch(ResumeSuggestionBatchAcceptDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(dto.getSuggestionIds()));
        if (ids.isEmpty() || ids.size() > 50) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Batch suggestion count must be between 1 and 50");
        }
        List<String> idempotencyKeys = ids.stream()
                .map(id -> dto.getIdempotencyKey() + ":" + id)
                .toList();
        List<ResumeSuggestionDecision> replayed = decisionMapper.selectList(
                new LambdaQueryWrapper<ResumeSuggestionDecision>()
                        .eq(ResumeSuggestionDecision::getUserId, userId)
                        .in(ResumeSuggestionDecision::getIdempotencyKey, idempotencyKeys)
                        .eq(ResumeSuggestionDecision::getDeleted, CommonConstants.NO));
        if (!replayed.isEmpty()) {
            if (replayed.size() != ids.size()
                    || replayed.stream().anyMatch(item -> !"ACCEPT".equals(item.getDecisionType()))) {
                throw new BusinessException(
                        ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "Batch idempotency key is partially used");
            }
            return ids.stream().map(this::detail).toList();
        }

        List<ResumeSuggestion> suggestions = suggestionMapper.selectList(
                new LambdaQueryWrapper<ResumeSuggestion>()
                        .eq(ResumeSuggestion::getUserId, userId)
                        .in(ResumeSuggestion::getId, ids)
                        .eq(ResumeSuggestion::getDeleted, CommonConstants.NO));
        if (suggestions.size() != ids.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "One or more suggestions do not exist");
        }
        ResumeSuggestion first = suggestions.get(0);
        if (suggestions.stream().anyMatch(item -> !"PENDING".equals(item.getStatus())
                || !"LOW".equals(item.getRiskLevel())
                || !Objects.equals(first.getResumeId(), item.getResumeId())
                || !Objects.equals(first.getSourceResumeVersionId(), item.getSourceResumeVersionId()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Batch accept requires pending low-risk suggestions from the same resume version");
        }

        ResumeVersion sourceVersion = snapshotManager.ownedVersion(first.getSourceResumeVersionId(), userId);
        ObjectNode snapshot = snapshotManager.readSnapshot(sourceVersion);
        List<ResumeSuggestion> ordered = suggestions.stream()
                .sorted(Comparator.comparing(ResumeSuggestion::getSectionKey)
                        .thenComparing(ResumeSuggestion::getAnchorStart, Comparator.reverseOrder()))
                .toList();
        String currentSection = null;
        int nextHigherAnchorStart = Integer.MAX_VALUE;
        for (ResumeSuggestion suggestion : ordered) {
            if (!Objects.equals(currentSection, suggestion.getSectionKey())) {
                currentSection = suggestion.getSectionKey();
                nextHigherAnchorStart = Integer.MAX_VALUE;
            }
            if (suggestion.getAnchorEnd() > nextHigherAnchorStart) {
                throw new BusinessException(
                        ErrorCode.SEMANTIC_VALIDATION_ERROR,
                        "Batch suggestions contain overlapping anchors");
            }
            String sourceText = snapshotManager.sectionText(snapshot, suggestion.getSectionKey());
            int start = suggestion.getAnchorStart();
            int end = suggestion.getAnchorEnd();
            if (start < 0 || end < start || end > sourceText.length()
                    || !sourceText.substring(start, end).equals(suggestion.getOriginalText())
                    || !ResumeArtifactHashes.sha256(suggestion.getOriginalText())
                    .equals(suggestion.getAnchorTextHash())) {
                throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "Suggestion source anchor is stale");
            }
            snapshotManager.replaceSectionText(snapshot, suggestion.getSectionKey(), start, end,
                    suggestion.getOriginalText(), suggestion.getSuggestedText());
            nextHigherAnchorStart = start;
        }

        Resume resume = snapshotManager.ownedResume(first.getResumeId(), userId);
        ResumeVersion resultVersion = snapshotManager.insertAndApplyIfCurrent(
                resume, first.getSourceResumeVersionId(), snapshot,
                "SUGGESTION_BATCH_ACCEPT", first.getSourceResumeVersionId(),
                "Batch accept " + suggestions.size() + " suggestions");
        LocalDateTime decidedAt = LocalDateTime.now();
        for (ResumeSuggestion suggestion : suggestions) {
            int nextDecisionVersion = suggestion.getDecisionVersion() == null
                    ? 1 : suggestion.getDecisionVersion() + 1;
            int updated = suggestionMapper.update(null, new LambdaUpdateWrapper<ResumeSuggestion>()
                    .eq(ResumeSuggestion::getId, suggestion.getId())
                    .eq(ResumeSuggestion::getUserId, userId)
                    .eq(ResumeSuggestion::getStatus, "PENDING")
                    .eq(ResumeSuggestion::getDecisionVersion, suggestion.getDecisionVersion())
                    .set(ResumeSuggestion::getStatus, "ACCEPTED")
                    .set(ResumeSuggestion::getDecisionVersion, nextDecisionVersion)
                    .set(ResumeSuggestion::getAcceptedText, suggestion.getSuggestedText())
                    .set(ResumeSuggestion::getAppliedResumeVersionId, resultVersion.getId())
                    .set(ResumeSuggestion::getDecidedAt, decidedAt));
            if (updated != 1) {
                throw new BusinessException(
                        ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "Suggestion decision changed concurrently");
            }
            ResumeSuggestionDecision decision = new ResumeSuggestionDecision();
            decision.setUserId(userId);
            decision.setSuggestionId(suggestion.getId());
            decision.setDecisionType("ACCEPT");
            decision.setFromStatus("PENDING");
            decision.setToStatus("ACCEPTED");
            decision.setDecisionVersion(nextDecisionVersion);
            decision.setResultResumeVersionId(resultVersion.getId());
            decision.setIdempotencyKey(dto.getIdempotencyKey() + ":" + suggestion.getId());
            decision.setNote(dto.getNote());
            decisionMapper.insert(decision);
        }
        searchSyncOutboxService.enqueue(first.getResumeId(), userId,
                ResumeSearchSyncOutboxService.OP_UPSERT);
        return ids.stream().map(this::detail).toList();
    }

    private ResumeVersion applySuggestion(ResumeSuggestion suggestion, boolean undo, String acceptedText) {
        Resume resume = snapshotManager.ownedResume(suggestion.getResumeId(), suggestion.getUserId());
        if (undo) {
            snapshotManager.lockOwnedResume(resume);
        }
        Long versionId = undo ? latestUndoVersionId(suggestion) : suggestion.getSourceResumeVersionId();
        ResumeVersion sourceVersion = snapshotManager.ownedVersion(versionId, suggestion.getUserId());
        ObjectNode snapshot = snapshotManager.readSnapshot(sourceVersion);
        String expected = undo ? acceptedText : suggestion.getOriginalText();
        String replacement = undo ? suggestion.getOriginalText() : acceptedText;
        if (!ResumeArtifactHashes.sha256(expected).equals(undo
                ? ResumeArtifactHashes.sha256(acceptedText)
                : suggestion.getAnchorTextHash())) {
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "Suggestion anchor hash is invalid");
        }
        int anchorStart = undo ? appliedAnchorStart(suggestion) : suggestion.getAnchorStart();
        snapshotManager.replaceSectionText(snapshot, suggestion.getSectionKey(),
                anchorStart, anchorStart + expected.length(), expected, replacement);
        String sourceType = undo ? "SUGGESTION_UNDO" : "SUGGESTION_ACCEPT";
        String name = undo ? "Undo suggestion #" + suggestion.getId() : "Suggestion #" + suggestion.getId();
        return snapshotManager.insertAndApplyIfCurrent(
                resume, versionId, snapshot, sourceType, suggestion.getId(), name);
    }

    private Long latestUndoVersionId(ResumeSuggestion suggestion) {
        if (suggestion.getAppliedResumeVersionId() == null) {
            return suggestion.getSourceResumeVersionId();
        }
        return allBatchSiblings(suggestion).stream()
                .map(ResumeSuggestion::getUndoResumeVersionId)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(suggestion.getAppliedResumeVersionId());
    }

    private int appliedAnchorStart(ResumeSuggestion suggestion) {
        if (suggestion.getAppliedResumeVersionId() == null) {
            return suggestion.getAnchorStart();
        }
        int offsetDelta = sectionBatchSiblings(suggestion).stream()
                .filter(item -> "ACCEPTED".equals(item.getStatus()))
                .filter(item -> item.getAnchorStart() < suggestion.getAnchorStart())
                .mapToInt(item -> acceptedText(item).length() - item.getOriginalText().length())
                .sum();
        return suggestion.getAnchorStart() + offsetDelta;
    }

    private List<ResumeSuggestion> allBatchSiblings(ResumeSuggestion suggestion) {
        return suggestionMapper.selectList(
                new LambdaQueryWrapper<ResumeSuggestion>()
                        .eq(ResumeSuggestion::getUserId, suggestion.getUserId())
                        .eq(ResumeSuggestion::getResumeId, suggestion.getResumeId())
                        .eq(ResumeSuggestion::getSourceResumeVersionId, suggestion.getSourceResumeVersionId())
                        .eq(ResumeSuggestion::getAppliedResumeVersionId, suggestion.getAppliedResumeVersionId())
                        .eq(ResumeSuggestion::getDeleted, CommonConstants.NO));
    }

    private List<ResumeSuggestion> sectionBatchSiblings(ResumeSuggestion suggestion) {
        return suggestionMapper.selectList(
                new LambdaQueryWrapper<ResumeSuggestion>()
                        .eq(ResumeSuggestion::getUserId, suggestion.getUserId())
                        .eq(ResumeSuggestion::getResumeId, suggestion.getResumeId())
                        .eq(ResumeSuggestion::getSourceResumeVersionId, suggestion.getSourceResumeVersionId())
                        .eq(ResumeSuggestion::getAppliedResumeVersionId, suggestion.getAppliedResumeVersionId())
                        .eq(ResumeSuggestion::getSectionKey, suggestion.getSectionKey())
                        .eq(ResumeSuggestion::getDeleted, CommonConstants.NO));
    }

    private String acceptedText(ResumeSuggestion suggestion) {
        return StringUtils.hasText(suggestion.getAcceptedText())
                ? suggestion.getAcceptedText()
                : suggestion.getSuggestedText();
    }

    private void validateAnchor(String sourceText, ResumeSuggestionCreateDTO dto) {
        int start = dto.getAnchorStart();
        int end = dto.getAnchorEnd();
        if (end < start || end > sourceText.length() || !sourceText.substring(start, end).equals(dto.getOriginalText())) {
            throw new BusinessException(
                    ErrorCode.STALE_SOURCE_VERSION,
                    "Suggestion anchor does not match the source version");
        }
    }

    private void requireStatus(ResumeSuggestion suggestion, String expected) {
        if (!expected.equals(suggestion.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "Suggestion in status " + suggestion.getStatus() + " cannot perform this decision");
        }
    }

    private ResumeSuggestion ownedSuggestion(Long suggestionId, Long userId) {
        ResumeSuggestion suggestion = suggestionMapper.selectOne(new LambdaQueryWrapper<ResumeSuggestion>()
                .eq(ResumeSuggestion::getId, suggestionId)
                .eq(ResumeSuggestion::getUserId, userId)
                .eq(ResumeSuggestion::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (suggestion == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Resume suggestion does not exist");
        }
        return suggestion;
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("PENDING", "ACCEPTED", "REJECTED", "UNDONE").contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Invalid suggestion status");
        }
        return normalized;
    }

    private String normalizeRiskLevel(String value) {
        String normalized = StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "MEDIUM";
        if (!Set.of("LOW", "MEDIUM", "HIGH").contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Risk level must be LOW, MEDIUM, or HIGH");
        }
        return normalized;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Suggestion evidence references are invalid");
        }
    }

    private List<Map<String, Object>> readEvidenceReferences(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, EVIDENCE_REFS_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private ResumeSuggestionVO toVO(ResumeSuggestion item, List<ResumeSuggestionDecision> decisions) {
        ResumeSuggestionVO vo = new ResumeSuggestionVO();
        vo.setId(item.getId());
        vo.setResumeId(item.getResumeId());
        vo.setSourceResumeVersionId(item.getSourceResumeVersionId());
        vo.setSourceType(item.getSourceType());
        vo.setSourceId(item.getSourceId());
        vo.setSourceVersion(item.getSourceVersion());
        vo.setSectionKey(item.getSectionKey());
        vo.setSectionId(item.getSectionId());
        vo.setFieldPath(item.getFieldPath());
        vo.setAnchorStart(item.getAnchorStart());
        vo.setAnchorEnd(item.getAnchorEnd());
        vo.setAnchorTextHash(item.getAnchorTextHash());
        vo.setOriginalText(item.getOriginalText());
        vo.setSuggestedText(item.getSuggestedText());
        vo.setAcceptedText(item.getAcceptedText());
        vo.setEvidenceReferences(readEvidenceReferences(item.getEvidenceRefsJson()));
        vo.setRiskLevel(item.getRiskLevel());
        vo.setRationale(item.getRationale());
        vo.setStatus(item.getStatus());
        vo.setDecisionVersion(item.getDecisionVersion());
        vo.setAppliedResumeVersionId(item.getAppliedResumeVersionId());
        vo.setUndoResumeVersionId(item.getUndoResumeVersionId());
        vo.setDecidedAt(item.getDecidedAt());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setDecisions(decisions.stream().map(this::toDecisionVO).toList());
        return vo;
    }

    private ResumeSuggestionDecisionVO toDecisionVO(ResumeSuggestionDecision item) {
        ResumeSuggestionDecisionVO vo = new ResumeSuggestionDecisionVO();
        vo.setId(item.getId());
        vo.setDecisionType(item.getDecisionType());
        vo.setFromStatus(item.getFromStatus());
        vo.setToStatus(item.getToStatus());
        vo.setDecisionVersion(item.getDecisionVersion());
        vo.setResultResumeVersionId(item.getResultResumeVersionId());
        vo.setIdempotencyKey(item.getIdempotencyKey());
        vo.setNote(item.getNote());
        vo.setCreatedAt(item.getCreatedAt());
        return vo;
    }
}
