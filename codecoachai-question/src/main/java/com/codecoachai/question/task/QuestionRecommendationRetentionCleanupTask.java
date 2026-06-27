package com.codecoachai.question.task;

import com.codecoachai.common.redis.lock.DistributedLockHelper;
import com.codecoachai.question.domain.entity.QuestionRecommendationBatch;
import com.codecoachai.question.domain.enums.QuestionRecommendationBatchStatus;
import com.codecoachai.question.domain.enums.QuestionRecommendationSourceType;
import com.codecoachai.question.util.QuestionRecommendationRequestPayloadUtils;
import com.codecoachai.question.util.QuestionRecommendationResultPayloadUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionRecommendationRetentionCleanupTask {

    private static final String RETENTION_CLEANUP_LOCK_KEY =
            "codecoachai:lock:question-recommendation:retention-cleanup";
    private static final List<String> TERMINAL_STATUSES = List.of(
            QuestionRecommendationBatchStatus.SUCCESS.getCode(),
            QuestionRecommendationBatchStatus.FAILED.getCode()
    );
    private static final String LEGACY_REQUEST_PAYLOAD_SQL = """
              AND (
                    request_json LIKE '%targetJobJson%'
                 OR request_json LIKE '%matchReportJson%'
                 OR request_json LIKE '%skillProfileJson%'
                 OR request_json LIKE '%skillGapsJson%'
                 OR request_json LIKE '%studyPlanJson%'
                 OR request_json LIKE '%studyTasksJson%'
              )
            """;
    private static final String FIND_LEGACY_REQUEST_ROWS = """
            SELECT id,
                   user_id,
                   source_type,
                   source_id,
                   match_report_id,
                   skill_profile_id,
                   study_plan_id,
                   strategy,
                   question_count,
                   request_json
            FROM question_recommendation_batch
            WHERE deleted = 0
              AND request_json IS NOT NULL
              AND request_json <> ''
            """ + LEGACY_REQUEST_PAYLOAD_SQL + """
            ORDER BY updated_at ASC
            LIMIT ?
            """;
    private static final String FIND_EXPIRED_BATCH_IDS = """
            SELECT id
            FROM question_recommendation_batch
            WHERE deleted = 0
              AND updated_at <= ?
              AND status IN (?, ?)
              AND request_json IS NOT NULL
              AND request_json <> ''
            """ + LEGACY_REQUEST_PAYLOAD_SQL + """
            ORDER BY updated_at ASC
            LIMIT ?
            """;
    private static final String FIND_EXPIRED_RESULT_ROWS = """
            SELECT id,
                   ai_call_log_id,
                   question_count,
                   result_json
            FROM question_recommendation_batch
            WHERE deleted = 0
              AND updated_at <= ?
              AND status IN (?, ?)
              AND result_json IS NOT NULL
              AND result_json <> ''
            ORDER BY updated_at ASC
            LIMIT ?
            """;
    private static final String SCRUB_REQUEST_FIELDS_TEMPLATE = """
            UPDATE question_recommendation_batch
            SET request_json = NULL
            WHERE deleted = 0
              AND id IN (%s)
            """;
    private static final String UPDATE_RESULT_METADATA = """
            UPDATE question_recommendation_batch
            SET result_json = ?
            WHERE deleted = 0
              AND id = ?
            """;
    private static final String UPDATE_REQUEST_SNAPSHOT = """
            UPDATE question_recommendation_batch
            SET source_type = ?,
                source_id = ?,
                match_report_id = ?,
                skill_profile_id = ?,
                study_plan_id = ?,
                strategy = ?,
                question_count = ?,
                request_json = ?
            WHERE deleted = 0
              AND id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DistributedLockHelper distributedLockHelper;
    private final ObjectMapper objectMapper;

    @Value("${codecoachai.question.recommendation.retention-cleanup.enabled:true}")
    private boolean enabled;

    @Value("${codecoachai.question.recommendation.retention-cleanup.retention-days:30}")
    private long retentionDays;

    @Value("${codecoachai.question.recommendation.retention-cleanup.scan-limit:100}")
    private int scanLimit;

    @Value("${codecoachai.question.recommendation.retention-cleanup.lock-wait-seconds:0}")
    private long lockWaitSeconds;

    @Value("${codecoachai.question.recommendation.retention-cleanup.lock-lease-seconds:300}")
    private long lockLeaseSeconds;

    @Scheduled(cron = "${codecoachai.question.recommendation.retention-cleanup.cron:0 45 3 * * ?}")
    public void cleanupExpiredRequestFields() {
        if (!enabled) {
            return;
        }
        try {
            boolean acquired = distributedLockHelper.tryLockAndRun(
                    RETENTION_CLEANUP_LOCK_KEY,
                    lockWaitSeconds,
                    lockLeaseSeconds,
                    this::cleanupCandidates);
            if (!acquired) {
                log.debug("Question recommendation retention cleanup skipped because another run is active");
            }
        } catch (Exception ex) {
            log.error("Question recommendation retention cleanup failed", ex);
        }
    }

    private void cleanupCandidates() {
        int boundedScanLimit = Math.max(scanLimit, 1);
        minimizeLegacyRequestFields(boundedScanLimit);
        scrubExpiredRequestFields(boundedScanLimit);
        minimizeExpiredResultFields(boundedScanLimit);
    }

    private void minimizeLegacyRequestFields(int boundedScanLimit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                FIND_LEGACY_REQUEST_ROWS,
                new Object[]{boundedScanLimit},
                new int[]{Types.INTEGER});
        int updated = 0;
        for (Map<String, Object> row : rows) {
            QuestionRecommendationBatch batch = toBatch(row);
            if (batch.getId() == null) {
                log.warn("Skip question_recommendation_batch legacy request minimization row because id is missing: {}", row);
                continue;
            }
            MinimizedRequestUpdate minimizedUpdate = buildMinimizedRequestUpdate(batch, row.get("request_json"));
            if (minimizedUpdate == null) {
                continue;
            }
            updated += jdbcTemplate.update(
                    UPDATE_REQUEST_SNAPSHOT,
                    minimizedUpdate.jdbcArgs(batch.getId()),
                    minimizedUpdate.jdbcArgTypes());
        }
        if (updated > 0) {
            log.info("Minimized legacy request snapshots in {} question_recommendation_batch rows", updated);
        }
    }

    private void scrubExpiredRequestFields(int boundedScanLimit) {
        if (retentionDays <= 0L) {
            log.warn("Skip question_recommendation_batch retention scrub because retentionDays={} is not positive",
                    retentionDays);
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(retentionDays, 0L));
        Object[] args = {
                Timestamp.valueOf(cutoff),
                TERMINAL_STATUSES.get(0),
                TERMINAL_STATUSES.get(1),
                boundedScanLimit
        };
        int[] argTypes = {
                Types.TIMESTAMP,
                Types.VARCHAR,
                Types.VARCHAR,
                Types.INTEGER
        };
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(FIND_EXPIRED_BATCH_IDS, args, argTypes);
        List<Long> ids = extractIds(rows);
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        String updateSql = SCRUB_REQUEST_FIELDS_TEMPLATE.formatted(placeholders);
        Object[] updateArgs = ids.toArray();
        int[] updateArgTypes = new int[ids.size()];
        Arrays.fill(updateArgTypes, Types.BIGINT);
        int updated = jdbcTemplate.update(updateSql, updateArgs, updateArgTypes);
        if (updated > 0) {
            log.info("Scrubbed request snapshots from {} expired question_recommendation_batch rows", updated);
        }
    }

    private void minimizeExpiredResultFields(int boundedScanLimit) {
        if (retentionDays <= 0L) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(retentionDays, 0L));
        Object[] args = {
                Timestamp.valueOf(cutoff),
                TERMINAL_STATUSES.get(0),
                TERMINAL_STATUSES.get(1),
                boundedScanLimit
        };
        int[] argTypes = {
                Types.TIMESTAMP,
                Types.VARCHAR,
                Types.VARCHAR,
                Types.INTEGER
        };
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(FIND_EXPIRED_RESULT_ROWS, args, argTypes);
        int updated = 0;
        for (Map<String, Object> row : rows) {
            String minimizedResultJson = buildMinimizedResultJson(row);
            if (!StringUtils.hasText(minimizedResultJson)) {
                continue;
            }
            updated += jdbcTemplate.update(
                    UPDATE_RESULT_METADATA,
                    new Object[]{minimizedResultJson, toLong(row.get("id"))},
                    new int[]{Types.VARCHAR, Types.BIGINT});
        }
        if (updated > 0) {
            log.info("Minimized expired result snapshots in {} question_recommendation_batch rows", updated);
        }
    }

    private String buildMinimizedResultJson(Map<String, Object> row) {
        Long batchId = toLong(row.get("id"));
        if (batchId == null) {
            log.warn("Skip question_recommendation_batch result minimization row because id is missing: {}", row);
            return null;
        }
        String resultJson = toText(row.get("result_json"));
        if (!StringUtils.hasText(resultJson) || isMinimizedResultPayload(resultJson)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(QuestionRecommendationResultPayloadUtils.buildMinimizedMetadata(
                    toLong(row.get("ai_call_log_id")),
                    batchId,
                    normalizeQuestionCount(toInteger(row.get("question_count")))));
        } catch (Exception ex) {
            log.warn("Skip question_recommendation_batch result minimization for id={} because metadata rebuild failed",
                    batchId, ex);
            return null;
        }
    }

    private boolean isMinimizedResultPayload(String resultJson) {
        try {
            JsonNode stored = objectMapper.readTree(resultJson);
            return stored != null
                    && stored.isObject()
                    && "MINIMIZED_METADATA".equals(stored.path("storageMode").asText());
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<Long> extractIds(List<Map<String, Object>> rows) {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long id = toLong(row.get("id"));
            if (id == null) {
                log.warn("Skip question_recommendation_batch retention cleanup row because id is missing: {}", row);
                continue;
            }
            ids.add(id);
        }
        return ids;
    }

    private QuestionRecommendationBatch toBatch(Map<String, Object> row) {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(toLong(row.get("id")));
        batch.setUserId(toLong(row.get("user_id")));
        batch.setSourceType(toText(row.get("source_type")));
        batch.setSourceId(toLong(row.get("source_id")));
        batch.setMatchReportId(toLong(row.get("match_report_id")));
        batch.setSkillProfileId(toLong(row.get("skill_profile_id")));
        batch.setStudyPlanId(toLong(row.get("study_plan_id")));
        batch.setStrategy(toText(row.get("strategy")));
        batch.setQuestionCount(toInteger(row.get("question_count")));
        batch.setRequestJson(toText(row.get("request_json")));
        return batch;
    }

    private MinimizedRequestUpdate buildMinimizedRequestUpdate(QuestionRecommendationBatch batch, Object rawRequestJson) {
        String requestJson = toText(rawRequestJson);
        if (!StringUtils.hasText(requestJson)) {
            return null;
        }
        try {
            JsonNode stored = objectMapper.readTree(requestJson);
            if (!hasLegacyRequestPayload(stored)) {
                return null;
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            Integer questionCount = normalizeQuestionCount(firstNonNull(
                    nodeInt(stored, "questionCount"),
                    batch.getQuestionCount()));
            String strategy = firstText(nodeText(stored, "strategy"), batch.getStrategy());
            String sourceType = normalizeSourceType(firstText(nodeText(stored, "sourceType"), batch.getSourceType()));
            Long matchReportId = firstNonNull(
                    nodeLong(stored, "matchReportId"),
                    batch.getMatchReportId());
            Long skillProfileId = firstNonNull(
                    nodeLong(stored, "skillProfileId"),
                    batch.getSkillProfileId());
            Long studyPlanId = firstNonNull(
                    nodeLong(stored, "studyPlanId"),
                    batch.getStudyPlanId());
            Long sourceId = sourceIdForType(
                    sourceType,
                    nodeLong(stored, "sourceId"),
                    batch.getSourceId(),
                    matchReportId,
                    skillProfileId,
                    studyPlanId);
            if (!StringUtils.hasText(sourceType) || sourceId == null) {
                return null;
            }
            if (QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode().equals(sourceType)
                    && matchReportId == null) {
                matchReportId = sourceId;
            }
            if (QuestionRecommendationSourceType.JD_GAP.getCode().equals(sourceType)
                    && skillProfileId == null) {
                skillProfileId = sourceId;
            }
            if (QuestionRecommendationSourceType.STUDY_PLAN.getCode().equals(sourceType)
                    && studyPlanId == null) {
                studyPlanId = sourceId;
            }
            snapshot.put("sourceType", sourceType);
            snapshot.put("sourceId", sourceId);
            snapshot.put("questionCount", questionCount);
            snapshot.put("difficultyPreference", nodeText(stored, "difficultyPreference"));
            snapshot.put("strategy", strategy);
            snapshot.put("skillProfileId", skillProfileId);
            snapshot.put("gapItemIds", extractGapItemIds(stored));
            snapshot.put("studyPlanId", studyPlanId);
            return new MinimizedRequestUpdate(
                    sourceType,
                    sourceId,
                    matchReportId,
                    skillProfileId,
                    studyPlanId,
                    strategy,
                    questionCount,
                    objectMapper.writeValueAsString(
                            QuestionRecommendationRequestPayloadUtils.withStoredRequestMarker(snapshot, batch)));
        } catch (Exception ex) {
            log.warn("Skip question_recommendation_batch legacy request minimization for id={} because snapshot rebuild failed",
                    batch == null ? null : batch.getId(), ex);
            return null;
        }
    }

    private Long sourceIdForType(String sourceType,
                                 Long storedSourceId,
                                 Long batchSourceId,
                                 Long matchReportId,
                                 Long skillProfileId,
                                 Long studyPlanId) {
        if (QuestionRecommendationSourceType.STUDY_PLAN.getCode().equals(sourceType)) {
            return firstNonNull(studyPlanId, storedSourceId, batchSourceId);
        }
        if (QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode().equals(sourceType)) {
            return firstNonNull(matchReportId, storedSourceId, batchSourceId);
        }
        if (QuestionRecommendationSourceType.JD_GAP.getCode().equals(sourceType)) {
            return firstNonNull(skillProfileId, storedSourceId, batchSourceId);
        }
        return null;
    }

    private String normalizeSourceType(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            return null;
        }
        String value = sourceType.trim().toUpperCase(Locale.ROOT);
        if ("GAP".equals(value)) {
            return QuestionRecommendationSourceType.JD_GAP.getCode();
        }
        if ("MATCH_REPORT".equals(value)) {
            return QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode();
        }
        for (QuestionRecommendationSourceType type : QuestionRecommendationSourceType.values()) {
            if (type.getCode().equals(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean hasLegacyRequestPayload(JsonNode stored) {
        if (stored == null || !stored.isObject()) {
            return false;
        }
        return StringUtils.hasText(nodeText(stored, "targetJobJson"))
                || StringUtils.hasText(nodeText(stored, "matchReportJson"))
                || StringUtils.hasText(nodeText(stored, "skillProfileJson"))
                || StringUtils.hasText(nodeText(stored, "skillGapsJson"))
                || StringUtils.hasText(nodeText(stored, "studyPlanJson"))
                || StringUtils.hasText(nodeText(stored, "studyTasksJson"));
    }

    private List<Long> extractGapItemIds(JsonNode stored) throws Exception {
        String skillGapsJson = nodeText(stored, "skillGapsJson");
        if (!StringUtils.hasText(skillGapsJson)) {
            return List.of();
        }
        JsonNode gaps = objectMapper.readTree(skillGapsJson);
        if (!gaps.isArray()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (JsonNode gap : gaps) {
            Long id = firstNonNull(nodeLong(gap, "id"), nodeLong(gap, "gapItemId"));
            if (id != null) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                try {
                    return Long.parseLong(trimmed);
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    private Integer toInteger(Object value) {
        Long number = toLong(value);
        return number == null ? null : number.intValue();
    }

    private String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nodeText(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        String text = node.get(fieldName).asText(null);
        return StringUtils.hasText(text) ? text : null;
    }

    private Long nodeLong(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value.isIntegralNumber()) {
            return value.asLong();
        }
        if (value.isTextual()) {
            return toLong(value.asText());
        }
        return null;
    }

    private Integer nodeInt(JsonNode node, String fieldName) {
        Long number = nodeLong(node, fieldName);
        return number == null ? null : number.intValue();
    }

    private Integer normalizeQuestionCount(Integer questionCount) {
        return questionCount == null ? 0 : Math.max(questionCount, 0);
    }

    private String firstText(String primary, String secondary) {
        return StringUtils.hasText(primary) ? primary : secondary;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record MinimizedRequestUpdate(
            String sourceType,
            Long sourceId,
            Long matchReportId,
            Long skillProfileId,
            Long studyPlanId,
            String strategy,
            Integer questionCount,
            String requestJson) {

        private Object[] jdbcArgs(Long batchId) {
            return new Object[]{
                    sourceType,
                    sourceId,
                    matchReportId,
                    skillProfileId,
                    studyPlanId,
                    strategy,
                    questionCount,
                    requestJson,
                    batchId
            };
        }

        private int[] jdbcArgTypes() {
            return new int[]{
                    Types.VARCHAR,
                    Types.BIGINT,
                    Types.BIGINT,
                    Types.BIGINT,
                    Types.BIGINT,
                    Types.VARCHAR,
                    Types.INTEGER,
                    Types.VARCHAR,
                    Types.BIGINT
            };
        }
    }
}
