package com.codecoachai.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.ai.agent.domain.dto.AgentContextUsageReferenceRecordDTO;
import com.codecoachai.ai.agent.domain.entity.AgentContextUsageReference;
import com.codecoachai.ai.agent.domain.entity.AgentMemory;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeChunk;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeDocument;
import com.codecoachai.ai.agent.domain.vo.impact.AgentContextImpactPreviewVO;
import com.codecoachai.ai.agent.domain.vo.impact.AgentContextImpactPreviewVO.AffectedConsumerVO;
import com.codecoachai.ai.agent.mapper.AgentContextUsageReferenceMapper;
import com.codecoachai.ai.agent.mapper.AgentMemoryMapper;
import com.codecoachai.ai.agent.mapper.PersonalKnowledgeChunkMapper;
import com.codecoachai.ai.agent.mapper.PersonalKnowledgeDocumentMapper;
import com.codecoachai.ai.agent.service.AgentContextUsageReferenceService;
import com.codecoachai.common.core.util.TextFingerprintUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentContextUsageReferenceServiceImpl implements AgentContextUsageReferenceService {

    private static final int DEFAULT_RECENT_DAYS = 30;
    private static final int MAX_CONSUMER_PREVIEW = 50;
    private static final BigDecimal MIN_STRONG_MEMORY_CONFIDENCE = BigDecimal.valueOf(0.6);
    private static final String USER_CONFIRMED_MEMORY_SOURCE_PREFIX = "USER_CONFIRMED_";
    private static final String SOURCE_KNOWLEDGE_DOCUMENT = "KNOWLEDGE_DOCUMENT";
    private static final String SOURCE_KNOWLEDGE_CHUNK = "KNOWLEDGE_CHUNK";
    private static final String SOURCE_MEMORY = "MEMORY";

    private final AgentContextUsageReferenceMapper usageReferenceMapper;
    private final PersonalKnowledgeDocumentMapper personalKnowledgeDocumentMapper;
    private final PersonalKnowledgeChunkMapper personalKnowledgeChunkMapper;
    private final AgentMemoryMapper agentMemoryMapper;

    @Override
    public void record(AgentContextUsageReferenceRecordDTO record) {
        AgentContextUsageReference entity = toEntity(record);
        if (entity == null) {
            return;
        }
        try {
            usageReferenceMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            try {
                usageReferenceMapper.update(null, new LambdaUpdateWrapper<AgentContextUsageReference>()
                        .eq(AgentContextUsageReference::getUserId, entity.getUserId())
                        .eq(AgentContextUsageReference::getSourceType, entity.getSourceType())
                        .eq(AgentContextUsageReference::getSourceId, entity.getSourceId())
                        .eq(AgentContextUsageReference::getSourceVersion, entity.getSourceVersion())
                        .eq(AgentContextUsageReference::getConsumerType, entity.getConsumerType())
                        .eq(AgentContextUsageReference::getConsumerId, entity.getConsumerId())
                        .eq(AgentContextUsageReference::getUsageScene, entity.getUsageScene())
                        .eq(AgentContextUsageReference::getDeleted, 0)
                        .set(AgentContextUsageReference::getTraceId, entity.getTraceId())
                        .set(AgentContextUsageReference::getUsageStrength, entity.getUsageStrength())
                        .set(AgentContextUsageReference::getConfidence, entity.getConfidence())
                        .set(AgentContextUsageReference::getSnapshotHash, entity.getSnapshotHash())
                        .set(AgentContextUsageReference::getUpdatedAt, LocalDateTime.now()));
            } catch (RuntimeException updateEx) {
                log.warn("Usage reference duplicate update failed userId={} sourceType={} sourceId={} consumerType={} consumerId={}",
                        entity.getUserId(), entity.getSourceType(), entity.getSourceId(), entity.getConsumerType(),
                        entity.getConsumerId(), updateEx);
            }
        } catch (RuntimeException ex) {
            log.warn("Usage reference record failed userId={} sourceType={} sourceId={} consumerType={} consumerId={}",
                    entity.getUserId(), entity.getSourceType(), entity.getSourceId(), entity.getConsumerType(),
                    entity.getConsumerId(), ex);
        }
    }

    @Override
    public void recordAll(List<AgentContextUsageReferenceRecordDTO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        records.forEach(this::record);
    }

    @Override
    public AgentContextImpactPreviewVO previewKnowledgeDocument(Long userId, Long documentId) {
        PersonalKnowledgeDocument document = ownedDocument(userId, documentId);
        List<Long> chunkIds = personalKnowledgeChunkMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .select(PersonalKnowledgeChunk::getId)
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .eq(PersonalKnowledgeChunk::getDocumentId, documentId)
                        .eq(PersonalKnowledgeChunk::getDeleted, 0))
                .stream()
                .map(PersonalKnowledgeChunk::getId)
                .filter(Objects::nonNull)
                .toList();
        List<AgentContextUsageReference> refs = new ArrayList<>();
        refs.addAll(listRefs(userId, SOURCE_KNOWLEDGE_DOCUMENT, documentId));
        if (!chunkIds.isEmpty()) {
            refs.addAll(usageReferenceMapper.selectList(new LambdaQueryWrapper<AgentContextUsageReference>()
                    .eq(AgentContextUsageReference::getUserId, userId)
                    .eq(AgentContextUsageReference::getSourceType, SOURCE_KNOWLEDGE_CHUNK)
                    .in(AgentContextUsageReference::getSourceId, chunkIds)
                    .eq(AgentContextUsageReference::getDeleted, 0)
                    .orderByDesc(AgentContextUsageReference::getCreatedAt)));
        }
        boolean futureContextImpact = !"DISABLED".equalsIgnoreCase(firstText(document.getStatus(), ""));
        return buildPreview(SOURCE_KNOWLEDGE_DOCUMENT, documentId, document.getTitle(), refs, futureContextImpact);
    }

    @Override
    public AgentContextImpactPreviewVO previewKnowledgeChunk(Long userId, Long chunkId) {
        PersonalKnowledgeChunk chunk = ownedChunk(userId, chunkId);
        PersonalKnowledgeDocument document = ownedDocument(userId, chunk.getDocumentId());
        boolean futureContextImpact = !"DISABLED".equalsIgnoreCase(firstText(document.getStatus(), ""))
                && !"FAILED".equalsIgnoreCase(firstText(chunk.getIndexStatus(), ""));
        return buildPreview(SOURCE_KNOWLEDGE_CHUNK, chunkId,
                firstText(chunk.getSourceRef(), document.getTitle(), "Knowledge chunk " + chunkId),
                listRefs(userId, SOURCE_KNOWLEDGE_CHUNK, chunkId), futureContextImpact);
    }

    @Override
    public AgentContextImpactPreviewVO previewMemory(Long userId, Long memoryId) {
        AgentMemory memory = ownedMemory(userId, memoryId);
        boolean futureContextImpact = canEnterAgentContext(memory);
        return buildPreview(SOURCE_MEMORY, memoryId, firstText(memory.getMemoryType(), "Memory " + memoryId),
                listRefs(userId, SOURCE_MEMORY, memoryId), futureContextImpact);
    }

    private AgentContextUsageReference toEntity(AgentContextUsageReferenceRecordDTO record) {
        if (record == null
                || record.getUserId() == null
                || !StringUtils.hasText(record.getSourceType())
                || record.getSourceId() == null
                || !StringUtils.hasText(record.getConsumerType())
                || record.getConsumerId() == null
                || !StringUtils.hasText(record.getUsageScene())) {
            return null;
        }
        AgentContextUsageReference entity = new AgentContextUsageReference();
        entity.setUserId(record.getUserId());
        entity.setSourceType(code(record.getSourceType(), 64));
        entity.setSourceId(record.getSourceId());
        entity.setSourceVersion(StringUtils.hasText(record.getSourceVersion()) ? code(record.getSourceVersion(), 64) : "");
        entity.setConsumerType(code(record.getConsumerType(), 64));
        entity.setConsumerId(record.getConsumerId());
        entity.setTraceId(limit(record.getTraceId(), 128));
        entity.setUsageScene(code(record.getUsageScene(), 64));
        entity.setUsageStrength(normalizeStrength(record.getUsageStrength()));
        entity.setConfidence(clampConfidence(record.getConfidence()));
        entity.setSnapshotHash(normalizeHash(record.getSnapshotHash(), entity));
        return entity;
    }

    private AgentContextImpactPreviewVO buildPreview(String sourceType, Long sourceId, String sourceTitle,
                                                     List<AgentContextUsageReference> refs,
                                                     boolean futureContextImpact) {
        List<AgentContextUsageReference> safeRefs = refs == null ? List.of() : refs.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AgentContextUsageReference::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        LocalDateTime recentSince = LocalDateTime.now().minusDays(DEFAULT_RECENT_DAYS);
        int recentCount = (int) safeRefs.stream()
                .filter(ref -> ref.getCreatedAt() != null && !ref.getCreatedAt().isBefore(recentSince))
                .count();
        AgentContextImpactPreviewVO vo = new AgentContextImpactPreviewVO();
        vo.setSourceType(sourceType);
        vo.setSourceId(sourceId);
        vo.setSourceTitle(sourceTitle);
        vo.setReferenceCount(safeRefs.size());
        vo.setRecentReferenceCount(recentCount);
        vo.setAffectedModules(affectedModules(safeRefs));
        vo.setAffectedConsumers(affectedConsumers(safeRefs));
        vo.setFutureContextImpact(futureContextImpact);
        vo.setHistoricalOnly(!futureContextImpact && !safeRefs.isEmpty());
        vo.setSafeToDisable(!futureContextImpact && recentCount == 0);
        vo.setGeneratedAt(LocalDateTime.now());
        vo.setWarnings(warnings(vo));
        vo.setRecommendedActions(recommendedActions(vo));
        return vo;
    }

    private List<String> affectedModules(List<AgentContextUsageReference> refs) {
        return refs.stream()
                .map(AgentContextUsageReference::getConsumerType)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private List<AffectedConsumerVO> affectedConsumers(List<AgentContextUsageReference> refs) {
        Map<String, AffectedConsumerVO> consumers = new LinkedHashMap<>();
        for (AgentContextUsageReference ref : refs) {
            String key = ref.getConsumerType() + ":" + ref.getConsumerId() + ":" + firstText(ref.getUsageScene(), "");
            consumers.computeIfAbsent(key, ignored -> toAffectedConsumer(ref));
            if (consumers.size() >= MAX_CONSUMER_PREVIEW) {
                break;
            }
        }
        return new ArrayList<>(consumers.values());
    }

    private AffectedConsumerVO toAffectedConsumer(AgentContextUsageReference ref) {
        AffectedConsumerVO vo = new AffectedConsumerVO();
        vo.setConsumerType(ref.getConsumerType());
        vo.setConsumerId(ref.getConsumerId());
        vo.setTraceId(ref.getTraceId());
        vo.setUsageScene(ref.getUsageScene());
        vo.setUsageStrength(ref.getUsageStrength());
        vo.setConfidence(ref.getConfidence());
        vo.setSnapshotHash(ref.getSnapshotHash());
        vo.setHistorical(true);
        vo.setCreatedAt(ref.getCreatedAt());
        vo.setSummary(ref.getConsumerType() + "#" + ref.getConsumerId() + " used this context in " + ref.getUsageScene());
        return vo;
    }

    private List<String> warnings(AgentContextImpactPreviewVO preview) {
        List<String> warnings = new ArrayList<>();
        if (Boolean.TRUE.equals(preview.getFutureContextImpact())) {
            warnings.add("This source can still enter future AI or Agent context.");
        }
        if (preview.getReferenceCount() != null && preview.getReferenceCount() > 0) {
            warnings.add("Historical reports and runs keep their original references; disabling affects future context only.");
        }
        if (preview.getRecentReferenceCount() != null && preview.getRecentReferenceCount() > 0) {
            warnings.add("Recent references exist in the last " + DEFAULT_RECENT_DAYS + " days.");
        }
        return warnings;
    }

    private List<String> recommendedActions(AgentContextImpactPreviewVO preview) {
        if (Boolean.TRUE.equals(preview.getSafeToDisable())) {
            return List.of("Disable or delete can proceed after normal user confirmation.");
        }
        List<String> actions = new ArrayList<>();
        if (Boolean.TRUE.equals(preview.getFutureContextImpact())) {
            actions.add("Review whether this source should remain available to future context builders.");
        }
        if (preview.getReferenceCount() != null && preview.getReferenceCount() > 0) {
            actions.add("Open affected consumers before deleting if an audit trail is required.");
        }
        if (actions.isEmpty()) {
            actions.add("Proceed with normal confirmation.");
        }
        return actions;
    }

    private List<AgentContextUsageReference> listRefs(Long userId, String sourceType, Long sourceId) {
        return usageReferenceMapper.selectList(new LambdaQueryWrapper<AgentContextUsageReference>()
                .eq(AgentContextUsageReference::getUserId, userId)
                .eq(AgentContextUsageReference::getSourceType, sourceType)
                .eq(AgentContextUsageReference::getSourceId, sourceId)
                .eq(AgentContextUsageReference::getDeleted, 0)
                .orderByDesc(AgentContextUsageReference::getCreatedAt));
    }

    private PersonalKnowledgeDocument ownedDocument(Long userId, Long id) {
        PersonalKnowledgeDocument document = personalKnowledgeDocumentMapper.selectOne(
                new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                        .eq(PersonalKnowledgeDocument::getUserId, userId)
                        .eq(PersonalKnowledgeDocument::getId, id)
                        .eq(PersonalKnowledgeDocument::getDeleted, 0));
        if (document == null) {
            throw new IllegalArgumentException("Knowledge document does not exist or is not accessible");
        }
        return document;
    }

    private PersonalKnowledgeChunk ownedChunk(Long userId, Long id) {
        PersonalKnowledgeChunk chunk = personalKnowledgeChunkMapper.selectOne(
                new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .eq(PersonalKnowledgeChunk::getId, id)
                        .eq(PersonalKnowledgeChunk::getDeleted, 0));
        if (chunk == null) {
            throw new IllegalArgumentException("Knowledge chunk does not exist or is not accessible");
        }
        return chunk;
    }

    private AgentMemory ownedMemory(Long userId, Long id) {
        AgentMemory memory = agentMemoryMapper.selectOne(new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getUserId, userId)
                .eq(AgentMemory::getId, id)
                .eq(AgentMemory::getDeleted, 0));
        if (memory == null) {
            throw new IllegalArgumentException("Memory does not exist or is not accessible");
        }
        return memory;
    }

    private boolean canEnterAgentContext(AgentMemory memory) {
        if (memory == null || memory.getEnabled() == null || memory.getEnabled() != 1) {
            return false;
        }
        if (memory.getConfidence() == null || memory.getConfidence().compareTo(MIN_STRONG_MEMORY_CONFIDENCE) < 0) {
            return false;
        }
        return isManualMemorySource(memory.getSourceType()) || isUserConfirmedMemorySource(memory.getSourceType());
    }

    private boolean isManualMemorySource(String sourceType) {
        String normalized = sourceType == null ? "MANUAL" : sourceType.trim().toUpperCase(Locale.ROOT);
        return Set.of("MANUAL", "USER_MANUAL", "USER_NOTE").contains(normalized);
    }

    private boolean isUserConfirmedMemorySource(String sourceType) {
        return sourceType != null
                && sourceType.trim().toUpperCase(Locale.ROOT).startsWith(USER_CONFIRMED_MEMORY_SOURCE_PREFIX);
    }

    private BigDecimal clampConfidence(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ONE : value;
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            normalized = BigDecimal.ZERO;
        }
        if (normalized.compareTo(BigDecimal.ONE) > 0) {
            normalized = BigDecimal.ONE;
        }
        return normalized.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeStrength(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "MEDIUM";
        if (!Set.of("WEAK", "MEDIUM", "STRONG").contains(normalized)) {
            return "MEDIUM";
        }
        return normalized;
    }

    private String normalizeHash(String value, AgentContextUsageReference entity) {
        if (StringUtils.hasText(value) && value.length() <= 64) {
            return value.trim();
        }
        return TextFingerprintUtils.sha256Hex(entity.getUserId() + ":" + entity.getSourceType() + ":"
                + entity.getSourceId() + ":" + entity.getConsumerType() + ":" + entity.getConsumerId());
    }

    private String code(String value, int maxLength) {
        String normalized = (StringUtils.hasText(value) ? value.trim() : "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_\\-]", "_");
        return limit(normalized, maxLength);
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
