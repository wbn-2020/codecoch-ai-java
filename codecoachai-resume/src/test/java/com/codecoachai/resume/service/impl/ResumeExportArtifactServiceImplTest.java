package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.ResumeArtifactPackageCreateDTO;
import com.codecoachai.resume.config.ResumeExportProperties;
import com.codecoachai.resume.domain.dto.ResumeExportCreateDTO;
import com.codecoachai.resume.domain.entity.JobApplicationPackage;
import com.codecoachai.resume.domain.entity.ResumeArtifact;
import com.codecoachai.resume.domain.entity.ResumeAtsTemplate;
import com.codecoachai.resume.domain.entity.ResumeExport;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.vo.ResumeExportVO;
import com.codecoachai.resume.export.AtsResumeDocumentFactory;
import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.codecoachai.resume.export.ResumeDocumentRenderer;
import com.codecoachai.resume.export.ResumeUploadAdmissionGuard;
import com.codecoachai.resume.export.ResumeZipBuilder;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import com.codecoachai.resume.mapper.JobApplicationPackageMapper;
import com.codecoachai.resume.mapper.ResumeArtifactMapper;
import com.codecoachai.resume.mapper.ResumeAtsTemplateMapper;
import com.codecoachai.resume.mapper.ResumeExportMapper;
import com.codecoachai.resume.service.support.ResumeVersionSnapshotManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@ExtendWith(MockitoExtension.class)
class ResumeExportArtifactServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock
    private ResumeAtsTemplateMapper templateMapper;
    @Mock
    private ResumeExportMapper exportMapper;
    @Mock
    private ResumeArtifactMapper artifactMapper;
    @Mock
    private JobApplicationPackageMapper packageMapper;
    @Mock
    private ResumeVersionSnapshotManager snapshotManager;
    @Mock
    private AtsResumeDocumentFactory documentFactory;
    @Mock
    private ResumeDocumentRenderer renderer;
    @Mock
    private FileFeignClient fileFeignClient;

    private ObjectMapper objectMapper;
    private ResumeExportProperties properties;
    private ResumeExportArtifactServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        init(ResumeArtifact.class);
        init(ResumeAtsTemplate.class);
    }

    private static void init(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).username("resume-user").build());
        objectMapper = new ObjectMapper();
        properties = new ResumeExportProperties();
        properties.setMaxConcurrentUploads(1);
        properties.setUploadAcquireTimeoutMillis(0);
        service = new ResumeExportArtifactServiceImpl(
                templateMapper, exportMapper, artifactMapper, packageMapper, snapshotManager,
                documentFactory, List.of(renderer), new ResumeZipBuilder(objectMapper),
                fileFeignClient, properties, new ResumeUploadAdmissionGuard(properties), objectMapper);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void applicationPackageSnapshotProducesFiveTraceableMaterials() throws Exception {
        String snapshot = """
                {
                  "companyName": "Example Co",
                  "jobTitle": "Backend Engineer",
                  "readinessLevel": "READY",
                  "matchSummary": {"gaps": ["distributed tracing"]},
                  "projectEvidenceCoverage": {
                    "selectedEvidence": [{
                      "title": "Order Platform",
                      "role": "Lead",
                      "techStack": "Java, Redis",
                      "completenessStatus": "CONFIRMED"
                    }]
                  },
                  "interviewPreparation": {"topics": ["JVM", "transactions"]},
                  "checklist": [{"key": "resume", "label": "Resume reviewed", "passed": true}]
                }
                """;

        Method materialsMethod = ResumeExportArtifactServiceImpl.class
                .getDeclaredMethod("applicationMaterials", String.class);
        materialsMethod.setAccessible(true);
        Map<String, String> materials = (Map<String, String>) materialsMethod.invoke(service, snapshot);

        assertEquals(List.of(
                "cover-letter-draft.txt",
                "email-draft.txt",
                "project-case-study.txt",
                "interview-cheatsheet.txt",
                "preflight-checklist.txt"), materials.keySet().stream().toList());
        assertTrue(materials.get("project-case-study.txt").contains("Order Platform"));
        assertTrue(materials.get("interview-cheatsheet.txt").contains("JVM"));

        Method manifestMethod = ResumeExportArtifactServiceImpl.class
                .getDeclaredMethod("embeddedFileManifest", String.class, String.class);
        manifestMethod.setAccessible(true);
        Map<String, Object> manifest = (Map<String, Object>) manifestMethod.invoke(
                service, "cover-letter-draft.txt", materials.get("cover-letter-draft.txt"));
        byte[] content = materials.get("cover-letter-draft.txt").getBytes(StandardCharsets.UTF_8);
        assertEquals(content.length, manifest.get("size"));
        assertEquals(ResumeArtifactHashes.sha256(materials.get("cover-letter-draft.txt")), manifest.get("sha256"));
        assertEquals(Boolean.TRUE, manifest.get("embedded"));
    }

    @Test
    void createExportRejectsTemplateDefinitionThatIsNotJsonObject() {
        ResumeAtsTemplate template = template("[\"not-an-object\"]");
        template.setDefinitionHash(ResumeArtifactHashes.sha256(template.getDefinitionJson()));
        stubExportSource(template);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createExport(exportRequest()));

        assertTrue(error.getMessage().contains("Template definition"));
        verify(artifactMapper, never()).insert(any(ResumeArtifact.class));
    }

    @Test
    void createExportRejectsTemplateDefinitionHashMismatch() {
        ResumeAtsTemplate template = template("{\"sectionOrder\":[\"SUMMARY\"]}");
        template.setDefinitionHash(ResumeArtifactHashes.sha256("{\"different\":true}"));
        stubExportSource(template);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createExport(exportRequest()));

        assertTrue(error.getMessage().contains("Template definition"));
        verify(artifactMapper, never()).insert(any(ResumeArtifact.class));
    }

    @Test
    void createExportMarksArtifactFailedWhenExportRecordInsertFails() throws Exception {
        stubExportSource(validTemplate());
        doAnswer(invocation -> {
            ((ResumeArtifact) invocation.getArgument(0)).setId(30L);
            return 1;
        }).when(artifactMapper).insert(any(ResumeArtifact.class));
        when(exportMapper.insert(any(ResumeExport.class)))
                .thenThrow(new IllegalStateException("export insert failed"));

        assertThrows(BusinessException.class, () -> service.createExport(exportRequest()));

        verify(artifactMapper).updateById(argThat((ResumeArtifact item) ->
                item.getId() != null && "FAILED".equals(item.getStatus())));
        verify(fileFeignClient, never()).upload(any(), any(), any());
    }

    @Test
    void createExportDeletesUploadedFileWhenFinalDatabaseUpdateFails() throws Exception {
        stubSuccessfulExportPreparation();
        InnerFileUploadVO uploaded = new InnerFileUploadVO();
        uploaded.setFileId(88L);
        when(fileFeignClient.upload(any(), any(), any())).thenReturn(Result.success(uploaded));
        when(artifactMapper.updateById(any(ResumeArtifact.class)))
                .thenThrow(new IllegalStateException("artifact update failed"))
                .thenReturn(1);

        assertThrows(BusinessException.class, () -> service.createExport(exportRequest()));

        verify(fileFeignClient).delete(88L, USER_ID, "RESUME");
        verify(artifactMapper, times(2)).updateById(any(ResumeArtifact.class));
    }

    @Test
    void createExportReleasesUploadPermitAfterFeignFailure() throws Exception {
        stubSuccessfulExportPreparation();
        InnerFileUploadVO uploaded = new InnerFileUploadVO();
        uploaded.setFileId(88L);
        when(fileFeignClient.upload(any(), any(), any()))
                .thenThrow(new IllegalStateException("file service unavailable"))
                .thenReturn(Result.success(uploaded));
        when(artifactMapper.updateById(any(ResumeArtifact.class))).thenReturn(1);
        when(exportMapper.updateById(any(ResumeExport.class))).thenReturn(1);

        assertThrows(BusinessException.class, () -> service.createExport(exportRequest()));

        ResumeExportVO retry = service.createExport(exportRequest());

        assertEquals("READY", retry.getStatus());
        verify(fileFeignClient, times(2)).upload(any(), any(), any());
    }

    @Test
    void pdfDownloadRejectsActualBytesWhenHashDoesNotMatchArtifact() throws Exception {
        byte[] actual = "%PDF-real-content".getBytes(StandardCharsets.UTF_8);
        ResumeArtifact artifact = readyFileArtifact("resume.pdf", "application/pdf", actual);
        artifact.setSha256(ResumeArtifactHashes.sha256("different"));
        when(artifactMapper.selectOne(any())).thenReturn(artifact);
        when(fileFeignClient.download(artifact.getFileId(), USER_ID, "RESUME"))
                .thenReturn(ResponseEntity.ok(new ByteArrayResource(actual)));

        assertThrows(BusinessException.class, () -> service.download(artifact.getId()));
    }

    @Test
    void docxDownloadRejectsActualBytesWhenSizeDoesNotMatchArtifact() throws Exception {
        byte[] actual = "PK-real-docx-content".getBytes(StandardCharsets.UTF_8);
        ResumeArtifact artifact = readyFileArtifact(
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                actual);
        artifact.setFileSize((long) actual.length + 1);
        when(artifactMapper.selectOne(any())).thenReturn(artifact);
        when(fileFeignClient.download(artifact.getFileId(), USER_ID, "RESUME"))
                .thenReturn(ResponseEntity.ok(new ByteArrayResource(actual)));

        assertThrows(BusinessException.class, () -> service.download(artifact.getId()));
    }

    @Test
    void rebuiltZipUsesActualEntriesForManifestAndResponseHash() throws Exception {
        byte[] pdfBytes = "%PDF-package".getBytes(StandardCharsets.UTF_8);
        byte[] docxBytes = "PK-package-docx".getBytes(StandardCharsets.UTF_8);
        ResumeArtifact pdfArtifact = readyFileArtifact("resume.pdf", "application/pdf", pdfBytes);
        pdfArtifact.setId(11L);
        ResumeArtifact docxArtifact = readyFileArtifact(
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes);
        docxArtifact.setId(12L);
        docxArtifact.setFileId(71L);
        ResumeArtifact zipArtifact = readyZipArtifact(pdfArtifact, docxArtifact);
        when(artifactMapper.selectOne(any())).thenReturn(zipArtifact, pdfArtifact, docxArtifact);
        when(fileFeignClient.download(pdfArtifact.getFileId(), USER_ID, "RESUME"))
                .thenReturn(ResponseEntity.ok(new ByteArrayResource(pdfBytes)));
        when(fileFeignClient.download(docxArtifact.getFileId(), USER_ID, "RESUME"))
                .thenReturn(ResponseEntity.ok(new ByteArrayResource(docxBytes)));

        ResponseEntity<StreamingResponseBody> response = service.download(zipArtifact.getId());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);
        byte[] actualZip = output.toByteArray();
        Map<String, byte[]> entries = unzip(actualZip);

        assertEquals(sha256(actualZip), response.getHeaders().getFirst("X-Artifact-SHA256"));
        assertTrue(entries.containsKey("application-package.json"));
        Map<String, Object> manifest = objectMapper.readValue(entries.get("manifest.json"), LinkedHashMap.class);
        List<Map<String, Object>> files = (List<Map<String, Object>>) manifest.get("files");
        assertEquals(entries.keySet().stream().filter(name -> !"manifest.json".equals(name)).count(), files.size());
        for (Map<String, Object> file : files) {
            byte[] entryBytes = entries.get(file.get("name").toString());
            assertTrue(entryBytes != null, file.get("name").toString());
            assertEquals(entryBytes.length, ((Number) file.get("size")).longValue());
            assertEquals(sha256(entryBytes), file.get("sha256"));
        }
    }

    @Test
    void rebuiltZipRejectsChildArtifactWhoseActualBytesDoNotMatchMetadata() throws Exception {
        byte[] actualPdf = "%PDF-tampered".getBytes(StandardCharsets.UTF_8);
        byte[] docxBytes = "PK-docx".getBytes(StandardCharsets.UTF_8);
        ResumeArtifact pdfArtifact = readyFileArtifact("resume.pdf", "application/pdf", actualPdf);
        pdfArtifact.setId(11L);
        pdfArtifact.setSha256(ResumeArtifactHashes.sha256("expected-pdf"));
        ResumeArtifact docxArtifact = readyFileArtifact(
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes);
        docxArtifact.setId(12L);
        docxArtifact.setFileId(71L);
        ResumeArtifact zipArtifact = readyZipArtifact(pdfArtifact, docxArtifact);
        when(artifactMapper.selectOne(any())).thenReturn(zipArtifact, pdfArtifact);
        when(fileFeignClient.download(pdfArtifact.getFileId(), USER_ID, "RESUME"))
                .thenReturn(ResponseEntity.ok(new ByteArrayResource(actualPdf)));

        assertThrows(BusinessException.class, () -> service.download(zipArtifact.getId()));
    }

    @Test
    void rebuiltZipRejectsManifestWithoutRequiredChildArtifacts() throws Exception {
        ResumeArtifact zipArtifact = readyZipArtifact();
        Map<String, Object> stored = objectMapper.readValue(zipArtifact.getManifestJson(), LinkedHashMap.class);
        ((Map<String, Object>) stored.get("zipManifest")).remove("files");
        zipArtifact.setManifestJson(objectMapper.writeValueAsString(stored));
        when(artifactMapper.selectOne(any())).thenReturn(zipArtifact);

        assertThrows(BusinessException.class, () -> service.download(zipArtifact.getId()));
        verifyNoInteractions(fileFeignClient);
    }

    @Test
    void rebuiltZipRejectsChildManifestEntryWithoutArtifactId() throws Exception {
        ResumeArtifact zipArtifact = readyZipArtifact();
        Map<String, Object> stored = objectMapper.readValue(zipArtifact.getManifestJson(), LinkedHashMap.class);
        Map<String, Object> manifest = (Map<String, Object>) stored.get("zipManifest");
        manifest.put("files", List.of(
                Map.of("name", "resume.pdf", "artifactId", 11L),
                Map.of("name", "resume.docx")));
        zipArtifact.setManifestJson(objectMapper.writeValueAsString(stored));
        when(artifactMapper.selectOne(any())).thenReturn(zipArtifact);

        assertThrows(BusinessException.class, () -> service.download(zipArtifact.getId()));
        verifyNoInteractions(fileFeignClient);
    }

    @Test
    void createPackageRejectsMissingApplicationPackageIdBeforeGeneratingChildren() {
        ResumeArtifactPackageCreateDTO request = packageRequest();
        request.setApplicationPackageId(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createPackageArtifact(request));

        assertTrue(error.getMessage().contains("Application package"));
        verifyNoInteractions(snapshotManager, templateMapper, packageMapper, artifactMapper, exportMapper,
                documentFactory, renderer, fileFeignClient);
    }

    @Test
    void createPackageRejectsApplicationPackageFromAnotherResumeVersion() {
        ResumeArtifactPackageCreateDTO request = packageRequest();
        ResumeVersion version = sourceVersion();
        ResumeAtsTemplate template = validTemplate();
        JobApplicationPackage applicationPackage = applicationPackage();
        applicationPackage.setResumeVersionId(99L);
        when(snapshotManager.ownedVersion(2L, USER_ID)).thenReturn(version);
        when(templateMapper.selectOne(any())).thenReturn(template);
        when(packageMapper.selectOne(any())).thenReturn(applicationPackage);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createPackageArtifact(request));

        assertTrue(error.getMessage().contains("resume version"));
        verify(artifactMapper, never()).insert(any(ResumeArtifact.class));
        verifyNoInteractions(documentFactory, renderer, fileFeignClient);
    }

    @Test
    void rebuiltZipRejectsChildWithWrongArtifactType() throws Exception {
        assertInvalidZipChild(child -> child.setArtifactType("APPLICATION_ZIP"));
    }

    @Test
    void rebuiltZipRejectsChildThatIsNotReady() throws Exception {
        assertInvalidZipChild(child -> child.setStatus("FAILED"));
    }

    @Test
    void downloadingArtifactBeforeReadyUsesSemanticValidationContract() {
        ResumeArtifact artifact = readyFileArtifact(
                "resume.pdf",
                "application/pdf",
                "%PDF".getBytes(StandardCharsets.UTF_8));
        artifact.setId(404L);
        artifact.setStatus("GENERATING");
        when(artifactMapper.selectOne(any())).thenReturn(artifact);

        BusinessException error = assertThrows(BusinessException.class, () -> service.download(404L));

        assertEquals(ErrorCode.SEMANTIC_VALIDATION_ERROR.getCode(), error.getCode());
        verifyNoInteractions(fileFeignClient);
    }

    @Test
    void rebuiltZipRejectsChildWithForgedExtensionOrMimeType() throws Exception {
        assertInvalidZipChild(child -> {
            child.setFileName("resume.docx");
            child.setMimeType("application/pdf");
        });
    }

    @Test
    void rebuiltZipRejectsChildFromAnotherResumeVersion() throws Exception {
        assertInvalidZipChild(child -> child.setSourceResumeVersionId(99L));
    }

    @Test
    void rebuiltZipRejectsChildFromAnotherTemplateVersion() throws Exception {
        assertInvalidZipChild(child -> child.setTemplateVersion(99));
    }

    @Test
    void rebuiltZipRejectsManifestThatSwapsInUnrelatedOwnedArtifact() throws Exception {
        byte[] pdfBytes = "%PDF-package".getBytes(StandardCharsets.UTF_8);
        byte[] docxBytes = "PK-package-docx".getBytes(StandardCharsets.UTF_8);
        ResumeArtifact expectedPdf = readyFileArtifact("resume.pdf", "application/pdf", pdfBytes);
        expectedPdf.setId(11L);
        ResumeArtifact docx = readyFileArtifact(
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes);
        docx.setId(12L);
        ResumeArtifact zip = readyZipArtifact(expectedPdf, docx);
        ResumeArtifact unrelatedPdf = readyFileArtifact("other.pdf", "application/pdf", pdfBytes);
        unrelatedPdf.setId(11L);
        unrelatedPdf.setSourceResumeId(88L);
        when(artifactMapper.selectOne(any())).thenReturn(zip, unrelatedPdf);

        assertThrows(BusinessException.class, () -> service.download(zip.getId()));
        verifyNoInteractions(fileFeignClient);
    }

    @Test
    void foreignArtifactCannotBeReadOrDownloaded() {
        when(artifactMapper.selectOne(any())).thenReturn(null);

        BusinessException detailError = assertThrows(BusinessException.class, () -> service.artifact(404L));
        BusinessException downloadError = assertThrows(BusinessException.class, () -> service.download(404L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), detailError.getCode());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), downloadError.getCode());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ResumeArtifact>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(artifactMapper, times(2)).selectOne(wrapperCaptor.capture());
        wrapperCaptor.getAllValues().forEach(wrapper -> assertOwnedArtifactQuery(wrapper, 404L));
        verifyNoInteractions(fileFeignClient);
    }

    @Test
    void foreignApplicationPackageCreationRemainsParameterError() {
        when(snapshotManager.ownedVersion(2L, USER_ID)).thenReturn(sourceVersion());
        when(templateMapper.selectOne(any())).thenReturn(validTemplate());
        when(packageMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createPackageArtifact(packageRequest()));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), error.getCode());
        verify(artifactMapper, never()).insert(any(ResumeArtifact.class));
    }

    private void stubExportSource(ResumeAtsTemplate template) {
        ResumeVersion version = new ResumeVersion();
        version.setId(2L);
        version.setResumeId(1L);
        version.setUserId(USER_ID);
        version.setSnapshotJson("{\"summary\":\"Built APIs\"}");
        when(snapshotManager.ownedVersion(2L, USER_ID)).thenReturn(version);
        when(templateMapper.selectOne(any())).thenReturn(template);
    }

    private void assertOwnedArtifactQuery(Wrapper<?> wrapper, Long artifactId) {
        String sql = wrapper.getSqlSegment().toLowerCase();
        assertTrue(sql.contains("id"));
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("deleted"));
        if (wrapper instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> query) {
            query.getSqlSegment();
            var values = query.getParamNameValuePairs().values();
            assertTrue(values.contains(artifactId));
            assertTrue(values.contains(USER_ID));
            assertTrue(values.contains(0));
        }
    }

    private void stubSuccessfulExportPreparation() throws Exception {
        stubExportSource(validTemplate());
        when(renderer.format()).thenReturn("PDF");
        doAnswer(invocation -> {
            ((java.io.OutputStream) invocation.getArgument(1))
                    .write("%PDF-generated".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(renderer).render(any(), any());
        doAnswer(invocation -> {
            ((ResumeArtifact) invocation.getArgument(0)).setId(30L);
            return 1;
        }).when(artifactMapper).insert(any(ResumeArtifact.class));
        doAnswer(invocation -> {
            ((ResumeExport) invocation.getArgument(0)).setId(31L);
            return 1;
        }).when(exportMapper).insert(any(ResumeExport.class));
    }

    private ResumeExportCreateDTO exportRequest() {
        ResumeExportCreateDTO request = new ResumeExportCreateDTO();
        request.setResumeVersionId(2L);
        request.setTemplateCode("ATS_SINGLE_COLUMN");
        request.setTemplateVersion(1);
        request.setFormat("PDF");
        return request;
    }

    private ResumeAtsTemplate template(String definitionJson) {
        ResumeAtsTemplate template = new ResumeAtsTemplate();
        template.setId(5L);
        template.setTemplateCode("ATS_SINGLE_COLUMN");
        template.setTemplateVersion(1);
        template.setDefinitionJson(definitionJson);
        template.setStatus("ACTIVE");
        return template;
    }

    private ResumeAtsTemplate validTemplate() {
        ResumeAtsTemplate template = template("{\"sectionOrder\":[\"SUMMARY\"]}");
        template.setDefinitionHash(ResumeArtifactHashes.sha256(template.getDefinitionJson()));
        return template;
    }

    private ResumeVersion sourceVersion() {
        ResumeVersion version = new ResumeVersion();
        version.setId(2L);
        version.setResumeId(1L);
        version.setUserId(USER_ID);
        version.setSnapshotJson("{\"summary\":\"Built APIs\"}");
        return version;
    }

    private ResumeArtifactPackageCreateDTO packageRequest() {
        ResumeArtifactPackageCreateDTO request = new ResumeArtifactPackageCreateDTO();
        request.setResumeVersionId(2L);
        request.setApplicationPackageId(40L);
        request.setTemplateCode("ATS_SINGLE_COLUMN");
        request.setTemplateVersion(1);
        return request;
    }

    private JobApplicationPackage applicationPackage() {
        JobApplicationPackage applicationPackage = new JobApplicationPackage();
        applicationPackage.setId(40L);
        applicationPackage.setUserId(USER_ID);
        applicationPackage.setResumeId(1L);
        applicationPackage.setResumeVersionId(2L);
        applicationPackage.setSnapshotJson("{\"companyName\":\"Example Co\"}");
        return applicationPackage;
    }

    private ResumeArtifact readyFileArtifact(String fileName, String mimeType, byte[] bytes) {
        ResumeArtifact artifact = new ResumeArtifact();
        artifact.setId(20L);
        artifact.setUserId(USER_ID);
        artifact.setArtifactType("RESUME_EXPORT");
        artifact.setSourceResumeId(1L);
        artifact.setSourceResumeVersionId(2L);
        artifact.setSourceHash("source-hash");
        artifact.setTemplateCode("ATS_SINGLE_COLUMN");
        artifact.setTemplateVersion(1);
        artifact.setFileId(70L);
        artifact.setFileName(fileName);
        artifact.setMimeType(mimeType);
        artifact.setFileSize((long) bytes.length);
        artifact.setSha256(sha256(bytes));
        artifact.setStatus("READY");
        return artifact;
    }

    private ResumeArtifact readyZipArtifact() throws Exception {
        ResumeArtifact pdf = readyFileArtifact(
                "resume.pdf", "application/pdf", "%PDF-package".getBytes(StandardCharsets.UTF_8));
        pdf.setId(11L);
        ResumeArtifact docx = readyFileArtifact(
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "PK-package-docx".getBytes(StandardCharsets.UTF_8));
        docx.setId(12L);
        return readyZipArtifact(pdf, docx);
    }

    private ResumeArtifact readyZipArtifact(ResumeArtifact pdf, ResumeArtifact docx) throws Exception {
        String packageSnapshot = "{\"companyName\":\"Example Co\"}";
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("sourceResumeId", 1L);
        manifest.put("sourceResumeVersionId", 2L);
        manifest.put("sourceHash", "source-hash");
        manifest.put("templateCode", "ATS_SINGLE_COLUMN");
        manifest.put("templateVersion", 1);
        manifest.put("applicationPackageId", 40L);
        manifest.put("applicationPackageSnapshotHash", ResumeArtifactHashes.sha256(packageSnapshot));
        manifest.put("files", List.of(
                childManifest(pdf),
                childManifest(docx),
                embeddedManifest("application-package.json", packageSnapshot)));
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("zipManifest", manifest);
        stored.put("applicationPackageSnapshot", packageSnapshot);
        stored.put("generatedMaterials", Map.of());

        ResumeArtifact artifact = new ResumeArtifact();
        artifact.setId(30L);
        artifact.setUserId(USER_ID);
        artifact.setArtifactType("APPLICATION_ZIP");
        artifact.setSourceResumeId(1L);
        artifact.setSourceResumeVersionId(2L);
        artifact.setSourceApplicationPackageId(40L);
        artifact.setSourceHash("source-hash");
        artifact.setTemplateCode("ATS_SINGLE_COLUMN");
        artifact.setTemplateVersion(1);
        artifact.setFileName("application-package.zip");
        artifact.setMimeType("application/zip");
        artifact.setFileSize(1L);
        artifact.setSha256(ResumeArtifactHashes.sha256("stale-zip"));
        artifact.setManifestJson(objectMapper.writeValueAsString(stored));
        artifact.setStatus("READY");
        return artifact;
    }

    private Map<String, Object> childManifest(ResumeArtifact artifact) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", artifact.getFileName());
        file.put("artifactId", artifact.getId());
        file.put("mimeType", artifact.getMimeType());
        file.put("size", artifact.getFileSize());
        file.put("sha256", artifact.getSha256());
        return file;
    }

    private Map<String, Object> embeddedManifest(String name, String content) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", name);
        file.put("embedded", true);
        file.put("mimeType", "text/plain; charset=UTF-8");
        file.put("size", content.getBytes(StandardCharsets.UTF_8).length);
        file.put("sha256", ResumeArtifactHashes.sha256(content));
        return file;
    }

    private void assertInvalidZipChild(java.util.function.Consumer<ResumeArtifact> mutation) throws Exception {
        byte[] pdfBytes = "%PDF-package".getBytes(StandardCharsets.UTF_8);
        byte[] docxBytes = "PK-package-docx".getBytes(StandardCharsets.UTF_8);
        ResumeArtifact pdf = readyFileArtifact("resume.pdf", "application/pdf", pdfBytes);
        pdf.setId(11L);
        ResumeArtifact docx = readyFileArtifact(
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes);
        docx.setId(12L);
        ResumeArtifact zip = readyZipArtifact(pdf, docx);
        mutation.accept(pdf);
        when(artifactMapper.selectOne(any())).thenReturn(zip, pdf);

        assertThrows(BusinessException.class, () -> service.download(zip.getId()));
        verifyNoInteractions(fileFeignClient);
    }

    private Map<String, byte[]> unzip(byte[] content) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
