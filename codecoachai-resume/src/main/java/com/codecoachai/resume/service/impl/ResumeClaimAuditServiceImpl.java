package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.config.ResumeExportProperties;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ResumeClaimAudit;
import com.codecoachai.resume.domain.entity.ResumeClaimAuditFinding;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.vo.ResumeClaimAuditFindingVO;
import com.codecoachai.resume.domain.vo.ResumeClaimAuditVO;
import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ResumeClaimAuditFindingMapper;
import com.codecoachai.resume.mapper.ResumeClaimAuditMapper;
import com.codecoachai.resume.service.ResumeClaimAuditService;
import com.codecoachai.resume.service.support.ResumeVersionSnapshotManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ResumeClaimAuditServiceImpl implements ResumeClaimAuditService {

    private static final String AUDIT_VERSION = "CLAIM_AUDIT_V1";
    private static final int MAX_CLAIMS = 500;
    private static final int MAX_EVIDENCE = 200;
    private static final Pattern QUANTITY = Pattern.compile(
            "(?i)(?:\\b\\d+(?:\\.\\d+)?\\s*(?:%|x|k|m|ms|s|min|h|d|day|days|week|weeks|month|months|year|years|万|亿|人|次|倍|小时|天|周|月|年)\\b|\\b\\d+(?:\\.\\d+)?\\b)");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> REF_LIST = new TypeReference<>() {};

    private final ResumeClaimAuditMapper auditMapper;
    private final ResumeClaimAuditFindingMapper findingMapper;
    private final ProjectEvidenceMapper evidenceMapper;
    private final ResumeVersionSnapshotManager snapshotManager;
    private final ResumeExportProperties exportProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeClaimAuditVO audit(Long resumeVersionId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ResumeVersion version = snapshotManager.ownedVersion(resumeVersionId, userId);
        String snapshotJson = version.getSnapshotJson();
        if (snapshotJson.getBytes(StandardCharsets.UTF_8).length > exportProperties.effectiveMaxSourceTextBytes()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume version is too large to audit");
        }

        ResumeClaimAudit audit = new ResumeClaimAudit();
        audit.setUserId(userId);
        audit.setResumeId(version.getResumeId());
        audit.setResumeVersionId(version.getId());
        audit.setSourceHash(ResumeArtifactHashes.sha256(snapshotJson));
        audit.setAuditVersion(AUDIT_VERSION);
        audit.setStatus("RUNNING");
        audit.setClaimCount(0);
        audit.setVerifiedCount(0);
        audit.setPartialCount(0);
        audit.setUnsupportedCount(0);
        audit.setRiskCount(0);
        auditMapper.insert(audit);

        try {
            List<Claim> claims = claims(snapshotManager.readSnapshot(version));
            List<ProjectEvidence> evidence = evidenceMapper.selectList(new LambdaQueryWrapper<ProjectEvidence>()
                    .eq(ProjectEvidence::getUserId, userId)
                    .eq(ProjectEvidence::getSourceResumeId, version.getResumeId())
                    .eq(ProjectEvidence::getDeleted, CommonConstants.NO)
                    .orderByDesc(ProjectEvidence::getUpdatedAt)
                    .last("LIMIT " + MAX_EVIDENCE));
            int verified = 0;
            int partial = 0;
            int unsupported = 0;
            int risk = 0;
            for (int index = 0; index < claims.size(); index++) {
                ResumeClaimAuditFinding finding = evaluate(audit.getId(), userId, index, claims.get(index), evidence);
                findingMapper.insert(finding);
                switch (finding.getEvidenceStatus()) {
                    case "VERIFIED" -> verified++;
                    case "PARTIAL" -> partial++;
                    case "RISK" -> risk++;
                    default -> unsupported++;
                }
            }
            audit.setStatus("COMPLETED");
            audit.setClaimCount(claims.size());
            audit.setVerifiedCount(verified);
            audit.setPartialCount(partial);
            audit.setUnsupportedCount(unsupported);
            audit.setRiskCount(risk);
            audit.setCompletedAt(LocalDateTime.now());
            auditMapper.updateById(audit);
            return detail(audit.getId());
        } catch (RuntimeException ex) {
            audit.setStatus("FAILED");
            audit.setErrorMessage(safeError(ex));
            audit.setCompletedAt(LocalDateTime.now());
            auditMapper.updateById(audit);
            throw ex;
        }
    }

    @Override
    public ResumeClaimAuditVO detail(Long auditId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ResumeClaimAudit audit = ownedAudit(auditId, userId);
        List<ResumeClaimAuditFinding> findings = findingMapper.selectList(
                new LambdaQueryWrapper<ResumeClaimAuditFinding>()
                        .eq(ResumeClaimAuditFinding::getAuditId, auditId)
                        .eq(ResumeClaimAuditFinding::getUserId, userId)
                        .eq(ResumeClaimAuditFinding::getDeleted, CommonConstants.NO)
                        .orderByAsc(ResumeClaimAuditFinding::getClaimIndex));
        return toVO(audit, findings);
    }

    @Override
    public List<ResumeClaimAuditVO> list(Long resumeId) {
        Long userId = SecurityAssert.requireLoginUserId();
        return auditMapper.selectList(new LambdaQueryWrapper<ResumeClaimAudit>()
                        .eq(ResumeClaimAudit::getUserId, userId)
                        .eq(resumeId != null, ResumeClaimAudit::getResumeId, resumeId)
                        .eq(ResumeClaimAudit::getDeleted, CommonConstants.NO)
                        .orderByDesc(ResumeClaimAudit::getCreatedAt)
                        .orderByDesc(ResumeClaimAudit::getId)
                        .last("LIMIT 100"))
                .stream().map(item -> toVO(item, List.of())).toList();
    }

    private ResumeClaimAuditFinding evaluate(Long auditId, Long userId, int index, Claim claim,
                                             List<ProjectEvidence> evidence) {
        List<String> quantities = quantities(claim.text());
        Set<String> claimTokens = tokens(claim.text());
        double bestOverlap = 0d;
        List<Map<String, Object>> refs = new ArrayList<>();
        boolean quantityMatched = quantities.isEmpty();
        for (ProjectEvidence item : evidence) {
            String evidenceText = evidenceText(item);
            double overlap = overlap(claimTokens, tokens(evidenceText));
            boolean itemQuantityMatched = quantities.isEmpty()
                    || quantities.stream().allMatch(quantity -> normalizeQuantity(evidenceText).contains(normalizeQuantity(quantity)));
            if (overlap >= 0.12d || itemQuantityMatched && !quantities.isEmpty()) {
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("projectEvidenceId", item.getId());
                ref.put("title", item.getTitle());
                ref.put("overlap", Math.round(overlap * 100d) / 100d);
                ref.put("quantityMatched", itemQuantityMatched);
                refs.add(ref);
            }
            bestOverlap = Math.max(bestOverlap, overlap);
            quantityMatched = quantityMatched || itemQuantityMatched;
        }
        refs = refs.stream().limit(5).toList();

        String status;
        String reason;
        if (!quantities.isEmpty() && quantityMatched && bestOverlap >= 0.20d) {
            status = "VERIFIED";
            reason = "Quantified value and related project evidence text both match.";
        } else if (quantities.isEmpty() && bestOverlap >= 0.35d) {
            status = "VERIFIED";
            reason = "Related project evidence strongly overlaps this factual claim.";
        } else if (bestOverlap >= 0.15d || quantityMatched && !quantities.isEmpty()) {
            status = "PARTIAL";
            reason = "Some related evidence exists, but the claim is not fully supported.";
        } else if (!quantities.isEmpty()) {
            status = "RISK";
            reason = "Quantified claim has no matching value in linked project evidence.";
        } else {
            status = "UNSUPPORTED";
            reason = "No sufficiently related linked project evidence was found.";
        }

        ResumeClaimAuditFinding finding = new ResumeClaimAuditFinding();
        finding.setAuditId(auditId);
        finding.setUserId(userId);
        finding.setSectionKey(claim.section());
        finding.setClaimIndex(index);
        finding.setClaimType(quantities.isEmpty() ? "FACT" : "QUANTIFIED");
        finding.setClaimText(claim.text());
        finding.setClaimHash(ResumeArtifactHashes.sha256(claim.text()));
        finding.setQuantitiesJson(write(quantities));
        finding.setEvidenceStatus(status);
        finding.setEvidenceRefsJson(write(refs));
        finding.setReason(reason);
        return finding;
    }

    private List<Claim> claims(JsonNode snapshot) {
        List<Claim> claims = new ArrayList<>();
        collect("summary", snapshot.get("summary"), claims);
        collect("workExperience", snapshot.get("workExperience"), claims);
        collect("projects", snapshot.get("projects"), claims);
        return claims.stream().limit(MAX_CLAIMS).toList();
    }

    private void collect(String section, JsonNode node, List<Claim> output) {
        if (node == null || node.isNull() || output.size() >= MAX_CLAIMS) {
            return;
        }
        if (node.isTextual()) {
            JsonNode parsed = parseEmbeddedJson(node.asText());
            if (parsed != null) {
                collect(section, parsed, output);
                return;
            }
            for (String sentence : node.asText().split("\\r?\\n|(?<=[。.!?；;])\\s*")) {
                String clean = sentence.trim();
                if (clean.length() >= 6 && clean.length() <= 2000) {
                    output.add(new Claim(section, clean));
                    if (output.size() >= MAX_CLAIMS) {
                        return;
                    }
                }
            }
            return;
        }
        if (node.isContainerNode()) {
            node.elements().forEachRemaining(value -> collect(section, value, output));
        }
    }

    private JsonNode parseEmbeddedJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!(trimmed.startsWith("[") || trimmed.startsWith("{"))) {
            return null;
        }
        try {
            return objectMapper.readTree(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> quantities(String text) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Matcher matcher = QUANTITY.matcher(text);
        while (matcher.find() && values.size() < 20) {
            values.add(matcher.group().trim());
        }
        return List.copyOf(values);
    }

    private Set<String> tokens(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
        Set<String> tokens = new LinkedHashSet<>();
        for (int index = 0; index < normalized.length(); index++) {
            int end = Math.min(normalized.length(), index + 2);
            tokens.add(normalized.substring(index, end));
        }
        return tokens;
    }

    private double overlap(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0d;
        }
        long matches = left.stream().filter(right::contains).count();
        return (double) matches / Math.min(left.size(), right.size());
    }

    private String evidenceText(ProjectEvidence item) {
        return String.join(" ", nonBlank(item.getTitle(), item.getRole(), item.getBackground(),
                item.getResponsibility(), item.getTechStack(), item.getDifficulty(), item.getSolution(),
                item.getResult(), item.getReflection()));
    }

    private List<String> nonBlank(String... values) {
        return java.util.Arrays.stream(values).filter(StringUtils::hasText).toList();
    }

    private String normalizeQuantity(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private ResumeClaimAudit ownedAudit(Long auditId, Long userId) {
        ResumeClaimAudit audit = auditMapper.selectOne(new LambdaQueryWrapper<ResumeClaimAudit>()
                .eq(ResumeClaimAudit::getId, auditId)
                .eq(ResumeClaimAudit::getUserId, userId)
                .eq(ResumeClaimAudit::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (audit == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Resume claim audit does not exist");
        }
        return audit;
    }

    private ResumeClaimAuditVO toVO(ResumeClaimAudit audit, List<ResumeClaimAuditFinding> findings) {
        ResumeClaimAuditVO vo = new ResumeClaimAuditVO();
        vo.setId(audit.getId());
        vo.setResumeId(audit.getResumeId());
        vo.setResumeVersionId(audit.getResumeVersionId());
        vo.setSourceHash(audit.getSourceHash());
        vo.setAuditVersion(audit.getAuditVersion());
        vo.setStatus(audit.getStatus());
        vo.setClaimCount(audit.getClaimCount());
        vo.setVerifiedCount(audit.getVerifiedCount());
        vo.setPartialCount(audit.getPartialCount());
        vo.setUnsupportedCount(audit.getUnsupportedCount());
        vo.setRiskCount(audit.getRiskCount());
        vo.setErrorMessage(audit.getErrorMessage());
        vo.setCompletedAt(audit.getCompletedAt());
        vo.setCreatedAt(audit.getCreatedAt());
        vo.setFindings(findings.stream().map(this::toFindingVO).toList());
        return vo;
    }

    private ResumeClaimAuditFindingVO toFindingVO(ResumeClaimAuditFinding finding) {
        ResumeClaimAuditFindingVO vo = new ResumeClaimAuditFindingVO();
        vo.setId(finding.getId());
        vo.setSectionKey(finding.getSectionKey());
        vo.setClaimIndex(finding.getClaimIndex());
        vo.setClaimType(finding.getClaimType());
        vo.setClaimText(finding.getClaimText());
        vo.setClaimHash(finding.getClaimHash());
        vo.setQuantities(read(finding.getQuantitiesJson(), STRING_LIST, List.of()));
        vo.setEvidenceStatus(finding.getEvidenceStatus());
        vo.setEvidenceRefs(read(finding.getEvidenceRefsJson(), REF_LIST, List.of()));
        vo.setReason(finding.getReason());
        return vo;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Audit result serialization failed");
        }
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String safeError(RuntimeException ex) {
        return ex instanceof BusinessException ? ex.getMessage() : "Resume claim audit failed";
    }

    private record Claim(String section, String text) {
    }
}
