package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.dto.ResumeImportRepairRequestDTO;
import com.codecoachai.resume.domain.dto.ResumeImportRepairRollbackDTO;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.entity.ResumeImportRepairAudit;
import com.codecoachai.resume.domain.vo.ResumeImportRepairResultVO;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.mapper.ResumeImportRepairAuditMapper;
import com.codecoachai.resume.service.support.ResumeImportNormalizer;
import com.codecoachai.resume.service.support.ResumeImportRepairSnapshotCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResumeImportHistoricalRepairServiceImplTest {

    @Mock
    private ResumeAnalysisRecordMapper analysisRecordMapper;
    @Mock
    private ResumeImportRepairAuditMapper repairAuditMapper;

    private ObjectMapper objectMapper;
    private ResumeImportNormalizer normalizer;
    private ResumeImportHistoricalRepairServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        if (TableInfoHelper.getTableInfo(ResumeAnalysisRecord.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    ResumeAnalysisRecord.class);
        }
        if (TableInfoHelper.getTableInfo(ResumeImportRepairAudit.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    ResumeImportRepairAudit.class);
        }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        normalizer = new ResumeImportNormalizer(objectMapper);
        service = new ResumeImportHistoricalRepairServiceImpl(
                analysisRecordMapper,
                repairAuditMapper,
                normalizer,
                new ResumeImportRepairSnapshotCipher(Base64.getEncoder().encodeToString(new byte[32])),
                objectMapper);
    }

    @Test
    void defaultsToDryRunAndReturnsOnlyRedactedRepairSummary() throws Exception {
        ResumeAnalysisRecord record = legacyRecord();
        when(analysisRecordMapper.selectList(any())).thenReturn(List.of(record));

        ResumeImportRepairResultVO result = service.repair(repairRequest(), 7L);

        assertTrue(result.isDryRun());
        assertEquals(1, result.getMatchedRecords());
        assertEquals("WOULD_REPAIR", result.getRecords().get(0).getStatus());
        String responseJson = objectMapper.writeValueAsString(result);
        assertFalse(responseJson.contains("candidate@career.dev"));
        assertFalse(responseJson.contains("+8613812345678"));
        verify(analysisRecordMapper, never()).update(isNull(), any());
        verify(repairAuditMapper, never()).insert(any(ResumeImportRepairAudit.class));
    }

    @Test
    void rejectsUnboundedRepairRequestsBeforeSelectingAnyRecord() {
        ResumeImportRepairRequestDTO request = new ResumeImportRepairRequestDTO();
        request.setRepairBatchId("resume-repair-1001");

        assertThrows(BusinessException.class, () -> service.repair(request, 7L));

        verify(analysisRecordMapper, never()).selectList(any());
    }

    @Test
    void executionWritesEncryptedAuditAndVerifiesCanonicalRecord() {
        ResumeAnalysisRecord record = legacyRecord();
        ResumeImportNormalizer.NormalizationResult normalized =
                normalizer.normalize(record.getStructuredJson());
        ResumeAnalysisRecord persisted = canonicalRecord(record, normalized);
        when(analysisRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(repairAuditMapper.selectCount(any())).thenReturn(0L);
        when(analysisRecordMapper.update(isNull(), any())).thenReturn(1);
        when(analysisRecordMapper.selectById(record.getId())).thenReturn(persisted);

        ResumeImportRepairRequestDTO request = repairRequest();
        request.setDryRun(false);
        ResumeImportRepairResultVO result = service.repair(request, 7L);

        assertFalse(result.isDryRun());
        assertEquals(1, result.getChangedRecords());
        assertEquals("REPAIRED", result.getRecords().get(0).getStatus());
        ArgumentCaptor<ResumeImportRepairAudit> auditCaptor =
                ArgumentCaptor.forClass(ResumeImportRepairAudit.class);
        verify(repairAuditMapper).insert(auditCaptor.capture());
        ResumeImportRepairAudit audit = auditCaptor.getValue();
        assertEquals("APPLIED", audit.getStatus());
        assertFalse(audit.getBeforeSnapshotCiphertext().contains("candidate@career.dev"));
        assertFalse(audit.getAfterSnapshotCiphertext().contains("+8613812345678"));
        assertEquals(64, audit.getBeforeHash().length());
        assertEquals(64, audit.getAfterHash().length());
    }

    @Test
    void rollbackRestoresOnlyTheExactAuditedRecordAfterSnapshotCheck() {
        ResumeAnalysisRecord legacy = legacyRecord();
        ResumeImportNormalizer.NormalizationResult normalized =
                normalizer.normalize(legacy.getStructuredJson());
        ResumeAnalysisRecord canonical = canonicalRecord(legacy, normalized);
        when(analysisRecordMapper.selectList(any())).thenReturn(List.of(legacy));
        when(repairAuditMapper.selectCount(any())).thenReturn(0L);
        when(analysisRecordMapper.update(isNull(), any())).thenReturn(1, 1);
        when(analysisRecordMapper.selectById(legacy.getId()))
                .thenReturn(canonical, canonical, legacy);

        ResumeImportRepairRequestDTO repairRequest = repairRequest();
        repairRequest.setDryRun(false);
        service.repair(repairRequest, 7L);

        ArgumentCaptor<ResumeImportRepairAudit> auditCaptor =
                ArgumentCaptor.forClass(ResumeImportRepairAudit.class);
        verify(repairAuditMapper).insert(auditCaptor.capture());
        ResumeImportRepairAudit repairAudit = auditCaptor.getValue();
        repairAudit.setId(5001L);
        when(repairAuditMapper.selectList(any())).thenReturn(List.of(repairAudit));

        ResumeImportRepairRollbackDTO rollbackRequest = new ResumeImportRepairRollbackDTO();
        rollbackRequest.setDryRun(false);
        rollbackRequest.setAuditIds(List.of(5001L));
        rollbackRequest.setMaxRecords(1);

        ResumeImportRepairResultVO result =
                service.rollback("resume-repair-1001", rollbackRequest, 7L);

        assertEquals(1, result.getChangedRecords());
        assertEquals("RESTORED", result.getRecords().get(0).getStatus());
        verify(repairAuditMapper, org.mockito.Mockito.times(2)).insert(auditCaptor.capture());
        List<ResumeImportRepairAudit> insertedAudits = auditCaptor.getAllValues();
        ResumeImportRepairAudit rollbackAudit = insertedAudits.get(insertedAudits.size() - 1);
        assertEquals("ROLLBACK", rollbackAudit.getOperation());
        assertEquals("RESTORED", rollbackAudit.getStatus());
        assertFalse(rollbackAudit.getAfterSnapshotCiphertext().contains("candidate@career.dev"));
    }

    private ResumeImportRepairRequestDTO repairRequest() {
        ResumeImportRepairRequestDTO request = new ResumeImportRepairRequestDTO();
        request.setRepairBatchId("resume-repair-1001");
        request.setAnalysisRecordIds(List.of(1001L));
        request.setMaxRecords(1);
        return request;
    }

    private ResumeAnalysisRecord legacyRecord() {
        ResumeAnalysisRecord record = new ResumeAnalysisRecord();
        record.setId(1001L);
        record.setUserId(17L);
        record.setFileId(31L);
        record.setParseStatus("WAIT_CONFIRM");
        record.setRawText("Candidate Java backend resume");
        record.setStructuredJson(validStructuredJson());
        record.setGeneratedAt(LocalDateTime.of(2026, 8, 14, 10, 0));
        record.setUpdatedAt(LocalDateTime.of(2026, 8, 14, 10, 0));
        record.setDeleted(0);
        return record;
    }

    private ResumeAnalysisRecord canonicalRecord(
            ResumeAnalysisRecord source, ResumeImportNormalizer.NormalizationResult normalized) {
        ResumeAnalysisRecord record = new ResumeAnalysisRecord();
        record.setId(source.getId());
        record.setUserId(source.getUserId());
        record.setFileId(source.getFileId());
        record.setParseStatus(source.getParseStatus());
        record.setRawText(source.getRawText());
        record.setStructuredJson(normalized.normalizedJson());
        record.setSchemaVersion(normalized.structuredResume().getSchemaVersion());
        record.setPolicyVersion(ResumeImportNormalizer.POLICY_VERSION);
        record.setSourceHash(normalizer.sourceHash(source.getRawText()));
        record.setValidationStatus(normalized.qualityReport().getValidationStatus());
        record.setQualityReportJson(normalized.qualityReportJson());
        record.setGeneratedAt(source.getGeneratedAt());
        record.setRepairBatchId("resume-repair-1001");
        record.setUpdatedAt(source.getUpdatedAt());
        record.setDeleted(0);
        return record;
    }

    private String validStructuredJson() {
        return """
                {
                  "schemaVersion":"resume-import-v1",
                  "basicInfo":{
                    "name":"Candidate",
                    "phone":"+8613812345678",
                    "email":"candidate@career.dev",
                    "location":"Shanghai"
                  },
                  "targetPosition":"Java Backend Engineer",
                  "summary":"AI 解析简历: Java backend delivery",
                  "skills":["Java","Spring Boot","Java"],
                  "workExperiences":[{
                    "company":"Example Systems",
                    "position":"Backend Engineer",
                    "period":"2023.01-present",
                    "description":"Built reliable services",
                    "responsibilities":["API design"],
                    "achievements":["Improved delivery flow"]
                  }],
                  "projectExperiences":[],
                  "educationExperiences":[]
                }
                """;
    }
}
