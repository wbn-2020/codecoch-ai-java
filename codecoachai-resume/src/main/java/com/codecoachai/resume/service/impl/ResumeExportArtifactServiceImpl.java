package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.config.ResumeExportProperties;
import com.codecoachai.resume.domain.dto.ResumeArtifactPackageCreateDTO;
import com.codecoachai.resume.domain.dto.ResumeExportCreateDTO;
import com.codecoachai.resume.domain.entity.JobApplicationPackage;
import com.codecoachai.resume.domain.entity.ResumeArtifact;
import com.codecoachai.resume.domain.entity.ResumeAtsTemplate;
import com.codecoachai.resume.domain.entity.ResumeExport;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.vo.ResumeArtifactVO;
import com.codecoachai.resume.domain.vo.ResumeAtsTemplateVO;
import com.codecoachai.resume.domain.vo.ResumeExportVO;
import com.codecoachai.resume.export.AtsResumeDocument;
import com.codecoachai.resume.export.AtsResumeDocumentFactory;
import com.codecoachai.resume.export.LimitedOutputStream;
import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.codecoachai.resume.export.ResumeDocumentRenderer;
import com.codecoachai.resume.export.ResumeUploadAdmissionGuard;
import com.codecoachai.resume.export.ResumeZipBuilder;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.feign.PathMultipartFile;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import com.codecoachai.resume.mapper.JobApplicationPackageMapper;
import com.codecoachai.resume.mapper.ResumeArtifactMapper;
import com.codecoachai.resume.mapper.ResumeAtsTemplateMapper;
import com.codecoachai.resume.mapper.ResumeExportMapper;
import com.codecoachai.resume.service.ResumeExportArtifactService;
import com.codecoachai.resume.service.support.ResumeVersionSnapshotManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeExportArtifactServiceImpl implements ResumeExportArtifactService {

    private static final String DEFAULT_TEMPLATE = "ATS_SINGLE_COLUMN";
    private static final String BIZ_TYPE = "RESUME";
    private static final String STATUS_GENERATING = "GENERATING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";

    private final ResumeAtsTemplateMapper templateMapper;
    private final ResumeExportMapper exportMapper;
    private final ResumeArtifactMapper artifactMapper;
    private final JobApplicationPackageMapper packageMapper;
    private final ResumeVersionSnapshotManager snapshotManager;
    private final AtsResumeDocumentFactory documentFactory;
    private final List<ResumeDocumentRenderer> renderers;
    private final ResumeZipBuilder zipBuilder;
    private final FileFeignClient fileFeignClient;
    private final ResumeExportProperties properties;
    private final ResumeUploadAdmissionGuard uploadAdmissionGuard;
    private final ObjectMapper objectMapper;

    @Override
    public List<ResumeAtsTemplateVO> listTemplates() {
        SecurityAssert.requireLoginUserId();
        return templateMapper.selectList(new LambdaQueryWrapper<ResumeAtsTemplate>()
                        .eq(ResumeAtsTemplate::getStatus, "ACTIVE")
                        .eq(ResumeAtsTemplate::getDeleted, CommonConstants.NO)
                        .orderByAsc(ResumeAtsTemplate::getTemplateCode)
                        .orderByDesc(ResumeAtsTemplate::getTemplateVersion))
                .stream().map(this::toTemplateVO).toList();
    }

    @Override
    public ResumeExportVO createExport(ResumeExportCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        ResumeVersion version = snapshotManager.ownedVersion(dto.getResumeVersionId(), userId);
        ResumeAtsTemplate template = template(dto.getTemplateCode(), dto.getTemplateVersion());
        validateTemplateDefinition(template);
        String format = normalizeFormat(dto.getFormat());
        String sourceHash = ResumeArtifactHashes.sha256(version.getSnapshotJson());
        validateSourceSize(version.getSnapshotJson());
        String extension = format.toLowerCase(Locale.ROOT);
        String fileName = "resume-v" + version.getId() + "." + extension;
        String mimeType = "PDF".equals(format) ? MediaType.APPLICATION_PDF_VALUE
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        ResumeArtifact artifact = new ResumeArtifact();
        artifact.setUserId(userId);
        artifact.setArtifactType("RESUME_EXPORT");
        artifact.setSourceResumeId(version.getResumeId());
        artifact.setSourceResumeVersionId(version.getId());
        artifact.setSourceHash(sourceHash);
        artifact.setTemplateCode(template.getTemplateCode());
        artifact.setTemplateVersion(template.getTemplateVersion());
        artifact.setFileName(fileName);
        artifact.setMimeType(mimeType);
        artifact.setStatus(STATUS_GENERATING);
        ResumeExport export = new ResumeExport();
        export.setUserId(userId);
        export.setResumeId(version.getResumeId());
        export.setResumeVersionId(version.getId());
        export.setSourceHash(sourceHash);
        export.setTemplateId(template.getId());
        export.setTemplateCode(template.getTemplateCode());
        export.setTemplateVersion(template.getTemplateVersion());
        export.setExportFormat(format);
        export.setStatus(STATUS_GENERATING);

        Path temp = null;
        Long uploadedFileId = null;
        try {
            requireWrite(artifactMapper.insert(artifact), artifact.getId(), "Resume artifact record was not created");
            export.setArtifactId(artifact.getId());
            requireWrite(exportMapper.insert(export), export.getId(), "Resume export record was not created");
            temp = Files.createTempFile("resume-export-", "." + extension);
            try (OutputStream fileOutput = Files.newOutputStream(temp);
                 LimitedOutputStream limited = new LimitedOutputStream(fileOutput, properties.effectiveMaxArtifactBytes())) {
                renderer(format).render(
                        documentFactory.fromSnapshot(version.getSnapshotJson(), template.getDefinitionJson()),
                        limited);
            }
            long size = Files.size(temp);
            String hash = ResumeArtifactHashes.sha256(temp);
            InnerFileUploadVO uploaded;
            long uploadStartedAt = System.nanoTime();
            try {
                Path uploadPath = temp;
                uploaded = uploadAdmissionGuard.execute(uploadPath,
                        validated -> FeignResultUtils.unwrap(fileFeignClient.upload(
                                new PathMultipartFile(
                                        uploadPath,
                                        fileName,
                                        mimeType,
                                        validated.maxBytes(),
                                        validated.fileKey()),
                                BIZ_TYPE,
                                userId)));
                log.debug("Resume export upload completed artifactId={} exportId={} artifactSize={} durationMs={}",
                        artifact.getId(), export.getId(), size, elapsedMillis(uploadStartedAt));
            } catch (Exception ex) {
                logUploadFailure(artifact.getId(), export.getId(), size, uploadStartedAt, ex);
                throw ex;
            }
            if (uploaded == null || uploaded.getFileId() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Generated resume upload failed");
            }
            uploadedFileId = uploaded.getFileId();
            artifact.setFileId(uploadedFileId);
            artifact.setFileSize(size);
            artifact.setSha256(hash);
            artifact.setStatus(STATUS_READY);
            requireWrite(artifactMapper.updateById(artifact), artifact.getId(),
                    "Resume artifact record was not finalized");
            export.setContentHash(hash);
            export.setStatus(STATUS_READY);
            requireWrite(exportMapper.updateById(export), export.getId(),
                    "Resume export record was not finalized");
            return toExportVO(export, artifact);
        } catch (Exception ex) {
            deleteUploadedFileQuietly(uploadedFileId, userId);
            markFailed(artifact, export, ex);
            throw generationException(ex);
        } finally {
            deleteQuietly(temp);
        }
    }

    @Override
    public ResumeArtifactVO createPackageArtifact(ResumeArtifactPackageCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        if (dto.getApplicationPackageId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Application package is required");
        }
        ResumeVersion version = snapshotManager.ownedVersion(dto.getResumeVersionId(), userId);
        ResumeAtsTemplate template = template(dto.getTemplateCode(), dto.getTemplateVersion());
        validateTemplateDefinition(template);
        JobApplicationPackage applicationPackage = ownedPackage(dto.getApplicationPackageId(), userId);
        if (!Objects.equals(applicationPackage.getResumeId(), version.getResumeId())
                || !Objects.equals(applicationPackage.getResumeVersionId(), version.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Application package does not match the requested resume version");
        }

        List<ResumeExportVO> generatedChildren = new ArrayList<>(2);
        List<Path> temps = new ArrayList<>();
        ResumeArtifact zipArtifact = null;
        Long uploadedZipFileId = null;
        try {
            ResumeExportVO pdf = createExport(exportRequest(version.getId(), template, "PDF"));
            generatedChildren.add(pdf);
            ResumeExportVO docx = createExport(exportRequest(version.getId(), template, "DOCX"));
            generatedChildren.add(docx);
            ResumeArtifact pdfArtifact = ownedArtifact(pdf.getArtifact().getId(), userId);
            ResumeArtifact docxArtifact = ownedArtifact(docx.getArtifact().getId(), userId);

            String sourceHash = ResumeArtifactHashes.sha256(version.getSnapshotJson());
            zipArtifact = new ResumeArtifact();
            zipArtifact.setUserId(userId);
            zipArtifact.setArtifactType("APPLICATION_ZIP");
            zipArtifact.setSourceResumeId(version.getResumeId());
            zipArtifact.setSourceResumeVersionId(version.getId());
            zipArtifact.setSourceApplicationPackageId(applicationPackage.getId());
            zipArtifact.setSourceHash(sourceHash);
            zipArtifact.setTemplateCode(template.getTemplateCode());
            zipArtifact.setTemplateVersion(template.getTemplateVersion());
            zipArtifact.setFileName("application-package-v" + version.getId() + ".zip");
            zipArtifact.setMimeType("application/zip");
            zipArtifact.setStatus(STATUS_GENERATING);
            requireWrite(artifactMapper.insert(zipArtifact), zipArtifact.getId(),
                    "Application package artifact record was not created");

            List<ResumeZipBuilder.SourceEntry> entries = new ArrayList<>();
            entries.add(downloadToTemp(pdfArtifact, userId, temps));
            entries.add(downloadToTemp(docxArtifact, userId, temps));
            String packageSnapshot = applicationPackage.getSnapshotJson();
            if (StringUtils.hasText(packageSnapshot)) {
                Path packageFile = Files.createTempFile("application-package-", ".json");
                temps.add(packageFile);
                writeLimited(packageFile, output -> writeUtf8(output, packageSnapshot));
                entries.add(new ResumeZipBuilder.SourceEntry("application-package.json", packageFile));
            }
            Map<String, String> generatedMaterials = applicationMaterials(packageSnapshot);
            for (Map.Entry<String, String> material : generatedMaterials.entrySet()) {
                Path materialFile = Files.createTempFile("application-material-", ".txt");
                temps.add(materialFile);
                writeLimited(materialFile, output -> writeUtf8(output, material.getValue()));
                entries.add(new ResumeZipBuilder.SourceEntry(material.getKey(), materialFile));
            }

            Map<String, Object> zipManifest = zipManifest(version, template, pdfArtifact, docxArtifact,
                    applicationPackage, packageSnapshot, generatedMaterials);
            Map<String, Object> storedManifest = new LinkedHashMap<>();
            storedManifest.put("zipManifest", zipManifest);
            storedManifest.put("applicationPackageSnapshot", packageSnapshot);
            storedManifest.put("generatedMaterials", generatedMaterials);
            zipArtifact.setManifestJson(writeJson(storedManifest));

            Path zip = Files.createTempFile("resume-package-", ".zip");
            temps.add(zip);
            try (OutputStream fileOutput = Files.newOutputStream(zip);
                 LimitedOutputStream limited = new LimitedOutputStream(fileOutput, properties.effectiveMaxArtifactBytes())) {
                zipBuilder.write(limited, entries, zipManifest, properties.effectiveMaxZipEntries());
            }
            long size = Files.size(zip);
            String hash = ResumeArtifactHashes.sha256(zip);
            InnerFileUploadVO uploaded;
            long uploadStartedAt = System.nanoTime();
            try {
                Path uploadPath = zip;
                String fileName = zipArtifact.getFileName();
                String mimeType = zipArtifact.getMimeType();
                uploaded = uploadAdmissionGuard.execute(uploadPath,
                        validated -> FeignResultUtils.unwrap(fileFeignClient.upload(
                                new PathMultipartFile(
                                        uploadPath,
                                        fileName,
                                        mimeType,
                                        validated.maxBytes(),
                                        validated.fileKey()),
                                BIZ_TYPE,
                                userId)));
                log.debug("Application package upload completed artifactId={} artifactSize={} durationMs={}",
                        zipArtifact.getId(), size, elapsedMillis(uploadStartedAt));
            } catch (Exception ex) {
                logUploadFailure(zipArtifact.getId(), null, size, uploadStartedAt, ex);
                throw ex;
            }
            if (uploaded == null || uploaded.getFileId() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Generated application package upload failed");
            }
            uploadedZipFileId = uploaded.getFileId();
            zipArtifact.setFileId(uploadedZipFileId);
            zipArtifact.setFileSize(size);
            zipArtifact.setSha256(hash);
            zipArtifact.setStatus(STATUS_READY);
            requireWrite(artifactMapper.updateById(zipArtifact), zipArtifact.getId(),
                    "Application package artifact record was not finalized");
            return toArtifactVO(zipArtifact);
        } catch (Exception ex) {
            deleteUploadedFileQuietly(uploadedZipFileId, userId);
            markPackageArtifactFailed(zipArtifact, ex);
            compensatePackageChildren(generatedChildren, userId, ex);
            throw generationException(ex);
        } finally {
            temps.forEach(this::deleteQuietly);
        }
    }

    @Override
    public ResumeArtifactVO artifact(Long artifactId) {
        return toArtifactVO(ownedArtifactForRead(artifactId, SecurityAssert.requireLoginUserId()));
    }

    @Override
    public List<ResumeArtifactVO> listArtifacts(Long resumeVersionId) {
        Long userId = SecurityAssert.requireLoginUserId();
        return artifactMapper.selectList(new LambdaQueryWrapper<ResumeArtifact>()
                        .eq(ResumeArtifact::getUserId, userId)
                        .eq(resumeVersionId != null, ResumeArtifact::getSourceResumeVersionId, resumeVersionId)
                        .eq(ResumeArtifact::getDeleted, CommonConstants.NO)
                        .orderByDesc(ResumeArtifact::getCreatedAt)
                        .orderByDesc(ResumeArtifact::getId)
                        .last("LIMIT 100"))
                .stream().map(this::toArtifactVO).toList();
    }

    @Override
    public ResponseEntity<StreamingResponseBody> download(Long artifactId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ResumeArtifact artifact = ownedArtifactForRead(artifactId, userId);
        if (!STATUS_READY.equals(artifact.getStatus())) {
            throw new BusinessException(ErrorCode.SEMANTIC_VALIDATION_ERROR, "Artifact is not ready");
        }
        DownloadPayload payload = "APPLICATION_ZIP".equals(artifact.getArtifactType())
                ? applicationZipDownloadPayload(artifact, userId)
                : fileDownloadPayload(artifact, userId);
        StreamingResponseBody body = output -> output.write(payload.bytes());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                        + URLEncoder.encode(artifact.getFileName(), StandardCharsets.UTF_8).replace("+", "%20"))
                .contentLength(payload.bytes().length)
                .header("X-Artifact-SHA256", payload.sha256())
                .body(body);
    }

    private DownloadPayload applicationZipDownloadPayload(ResumeArtifact artifact, Long userId) {
        return artifact.getFileId() == null
                ? rebuildZipPayload(artifact, userId)
                : fileDownloadPayload(artifact, userId);
    }

    private DownloadPayload fileDownloadPayload(ResumeArtifact artifact, Long userId) {
        if (artifact.getFileId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Artifact file is missing");
        }
        try {
            ResponseEntity<Resource> response = fileFeignClient.download(artifact.getFileId(), userId, BIZ_TYPE);
            Resource resource = response == null ? null : response.getBody();
            if (resource == null) {
                throw new IOException("Artifact file is unavailable");
            }
            byte[] bytes = readLimited(resource);
            validateArtifactBytes(artifact, bytes);
            return new DownloadPayload(bytes, ResumeArtifactHashes.sha256(bytes));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Artifact file integrity validation failed");
        }
    }

    private DownloadPayload rebuildZipPayload(ResumeArtifact artifact, Long userId) {
        Map<String, Object> stored = readMap(artifact.getManifestJson());
        Map<String, Object> storedManifest = mapValue(stored.get("zipManifest"));
        String packageSnapshot = Objects.toString(stored.get("applicationPackageSnapshot"), null);
        Map<String, String> generatedMaterials = stringMap(stored.get("generatedMaterials"));
        List<Map<String, Object>> storedFiles = listOfMaps(storedManifest.get("files"));
        List<Map<String, Object>> childFiles = validateStoredZipManifest(
                artifact, storedManifest, storedFiles, packageSnapshot, generatedMaterials);
        List<Path> temps = new ArrayList<>();
        try {
            List<ResumeZipBuilder.SourceEntry> entries = new ArrayList<>();
            Map<String, ResumeArtifact> childArtifacts = new LinkedHashMap<>();
            for (Map<String, Object> file : childFiles) {
                Long childArtifactId = longValue(file.get("artifactId"));
                ResumeArtifact child = ownedArtifact(childArtifactId, userId);
                validateZipChildArtifact(artifact, file, child);
                ResumeZipBuilder.SourceEntry entry = downloadToTemp(child, userId, temps);
                entries.add(entry);
                childArtifacts.put(entry.name(), child);
            }
            if (StringUtils.hasText(packageSnapshot)) {
                entries.add(textEntry("application-package.json", packageSnapshot, ".json", temps));
            }
            for (Map.Entry<String, String> material : generatedMaterials.entrySet()) {
                entries.add(textEntry(material.getKey(), material.getValue(), ".txt", temps));
            }

            Map<String, Object> manifest = rebuiltManifest(storedManifest, entries, childArtifacts);
            Path zip = Files.createTempFile("resume-package-download-", ".zip");
            temps.add(zip);
            try (OutputStream fileOutput = Files.newOutputStream(zip);
                 LimitedOutputStream limited = new LimitedOutputStream(
                         fileOutput, properties.effectiveMaxArtifactBytes())) {
                zipBuilder.write(limited, entries, manifest, properties.effectiveMaxZipEntries());
            }
            byte[] bytes = Files.readAllBytes(zip);
            return new DownloadPayload(bytes, ResumeArtifactHashes.sha256(bytes));
        } catch (Exception ex) {
            throw zipRebuildException(ex);
        } finally {
            temps.forEach(this::deleteQuietly);
        }
    }

    private List<Map<String, Object>> validateStoredZipManifest(
            ResumeArtifact parent, Map<String, Object> manifest, List<Map<String, Object>> storedFiles,
            String packageSnapshot, Map<String, String> generatedMaterials) {
        requireManifestValue(manifest, "sourceResumeId", parent.getSourceResumeId());
        requireManifestValue(manifest, "sourceResumeVersionId", parent.getSourceResumeVersionId());
        requireManifestValue(manifest, "sourceHash", parent.getSourceHash());
        requireManifestValue(manifest, "templateCode", parent.getTemplateCode());
        requireManifestValue(manifest, "templateVersion", parent.getTemplateVersion());
        requireManifestValue(manifest, "applicationPackageId", parent.getSourceApplicationPackageId());
        if (parent.getSourceApplicationPackageId() == null || !StringUtils.hasText(packageSnapshot)
                || !Objects.equals(Objects.toString(manifest.get("applicationPackageSnapshotHash"), null),
                ResumeArtifactHashes.sha256(packageSnapshot))) {
            invalidManifest();
        }

        Map<String, Map<String, Object>> expectedEmbedded = new LinkedHashMap<>();
        expectedEmbedded.put("application-package.json",
                embeddedFileManifest("application-package.json", packageSnapshot));
        generatedMaterials.forEach((name, content) ->
                expectedEmbedded.put(name, embeddedFileManifest(name, content)));

        List<Map<String, Object>> childFiles = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<Long> artifactIds = new HashSet<>();
        boolean hasPdf = false;
        boolean hasDocx = false;
        for (Map<String, Object> file : storedFiles) {
            String name = Objects.toString(file.get("name"), "");
            if (!StringUtils.hasText(name) || !Objects.equals(name, safeZipName(name)) || !names.add(name)) {
                invalidManifest();
            }
            if (Boolean.TRUE.equals(file.get("embedded"))) {
                Map<String, Object> expected = expectedEmbedded.remove(name);
                if (expected == null || !sameFileManifest(file, expected)) {
                    invalidManifest();
                }
                continue;
            }
            Long artifactId = longValue(file.get("artifactId"));
            if (artifactId == null || !artifactIds.add(artifactId)) {
                invalidManifest();
            }
            String lowerName = name.toLowerCase(Locale.ROOT);
            hasPdf |= lowerName.endsWith(".pdf");
            hasDocx |= lowerName.endsWith(".docx");
            childFiles.add(file);
        }
        if (childFiles.size() != 2 || !hasPdf || !hasDocx || !expectedEmbedded.isEmpty()) {
            invalidManifest();
        }
        return childFiles;
    }

    private void validateZipChildArtifact(
            ResumeArtifact parent, Map<String, Object> storedFile, ResumeArtifact child) {
        String name = Objects.toString(storedFile.get("name"), "");
        String lowerName = name.toLowerCase(Locale.ROOT);
        String expectedMime;
        if (lowerName.endsWith(".pdf")) {
            expectedMime = MediaType.APPLICATION_PDF_VALUE;
        } else if (lowerName.endsWith(".docx")) {
            expectedMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else {
            invalidManifest();
            return;
        }
        if (!"RESUME_EXPORT".equals(child.getArtifactType())
                || !STATUS_READY.equals(child.getStatus())
                || child.getFileId() == null
                || !Objects.equals(name, safeZipName(child.getFileName()))
                || !Objects.equals(expectedMime, child.getMimeType())
                || !Objects.equals(parent.getSourceResumeId(), child.getSourceResumeId())
                || !Objects.equals(parent.getSourceResumeVersionId(), child.getSourceResumeVersionId())
                || !Objects.equals(parent.getSourceHash(), child.getSourceHash())
                || !Objects.equals(parent.getTemplateCode(), child.getTemplateCode())
                || !Objects.equals(parent.getTemplateVersion(), child.getTemplateVersion())
                || child.getSourceApplicationPackageId() != null
                || !Objects.equals(storedFile.get("mimeType"), child.getMimeType())
                || !Objects.equals(longValue(storedFile.get("size")), child.getFileSize())
                || !Objects.equals(Objects.toString(storedFile.get("sha256"), null), child.getSha256())) {
            invalidManifest();
        }
    }

    private boolean sameFileManifest(Map<String, Object> actual, Map<String, Object> expected) {
        return Objects.equals(actual.get("name"), expected.get("name"))
                && Objects.equals(actual.get("embedded"), expected.get("embedded"))
                && Objects.equals(actual.get("mimeType"), expected.get("mimeType"))
                && Objects.equals(longValue(actual.get("size")), longValue(expected.get("size")))
                && Objects.equals(actual.get("sha256"), expected.get("sha256"))
                && actual.get("artifactId") == null;
    }

    private void requireManifestValue(Map<String, Object> manifest, String key, Object expected) {
        Object actual = manifest.get(key);
        boolean matches = expected instanceof Number
                ? Objects.equals(longValue(actual), ((Number) expected).longValue())
                : Objects.equals(actual, expected);
        if (!matches) {
            invalidManifest();
        }
    }

    private void invalidManifest() {
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Artifact manifest is invalid");
    }

    private ResumeZipBuilder.SourceEntry downloadToTemp(ResumeArtifact artifact, Long userId, List<Path> temps)
            throws IOException {
        Path temp = Files.createTempFile("resume-artifact-", extension(artifact.getFileName()));
        temps.add(temp);
        ResponseEntity<Resource> response;
        try {
            response = fileFeignClient.download(artifact.getFileId(), userId, BIZ_TYPE);
        } catch (RuntimeException ex) {
            throw new IOException("Generated child artifact cannot be downloaded", ex);
        }
        Resource resource = response == null ? null : response.getBody();
        if (resource == null) {
            throw new IOException("Generated child artifact cannot be downloaded");
        }
        try (InputStream input = resource.getInputStream();
             OutputStream fileOutput = Files.newOutputStream(temp);
             LimitedOutputStream limited = new LimitedOutputStream(fileOutput, properties.effectiveMaxArtifactBytes())) {
            input.transferTo(limited);
        }
        long actualSize = Files.size(temp);
        String actualHash = ResumeArtifactHashes.sha256(temp);
        if (!Objects.equals(artifact.getFileSize(), actualSize)
                || !Objects.equals(artifact.getSha256(), actualHash)) {
            throw new IOException("Generated child artifact integrity mismatch");
        }
        return new ResumeZipBuilder.SourceEntry(safeZipName(artifact.getFileName()), temp);
    }

    private Map<String, Object> zipManifest(ResumeVersion version, ResumeAtsTemplate template,
                                            ResumeArtifact pdf, ResumeArtifact docx,
                                            JobApplicationPackage applicationPackage, String packageSnapshot,
                                            Map<String, String> generatedMaterials) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("sourceResumeId", version.getResumeId());
        manifest.put("sourceResumeVersionId", version.getId());
        manifest.put("sourceHash", ResumeArtifactHashes.sha256(version.getSnapshotJson()));
        manifest.put("templateCode", template.getTemplateCode());
        manifest.put("templateVersion", template.getTemplateVersion());
        List<Map<String, Object>> files = new ArrayList<>();
        files.add(fileManifest(pdf));
        files.add(fileManifest(docx));
        if (StringUtils.hasText(packageSnapshot)) {
            files.add(embeddedFileManifest("application-package.json", packageSnapshot));
        }
        generatedMaterials.forEach((name, content) -> files.add(embeddedFileManifest(name, content)));
        manifest.put("files", files);
        if (applicationPackage != null) {
            manifest.put("applicationPackageId", applicationPackage.getId());
            manifest.put("applicationPackageSnapshotHash", ResumeArtifactHashes.sha256(packageSnapshot));
        }
        return manifest;
    }

    private Map<String, Object> rebuiltManifest(Map<String, Object> storedManifest,
                                                List<ResumeZipBuilder.SourceEntry> entries,
                                                Map<String, ResumeArtifact> childArtifacts) {
        Map<String, Object> manifest = new LinkedHashMap<>(storedManifest);
        List<Map<String, Object>> files = new ArrayList<>();
        for (ResumeZipBuilder.SourceEntry entry : entries) {
            ResumeArtifact child = childArtifacts.get(entry.name());
            Map<String, Object> file = child == null
                    ? embeddedFileManifest(entry.name(), readUtf8(entry.path()))
                    : fileManifest(child, entry.name(), entry.path());
            files.add(file);
        }
        manifest.put("files", files);
        return manifest;
    }

    private Map<String, Object> fileManifest(ResumeArtifact artifact, String actualName, Path actualPath) {
        Map<String, Object> file = fileManifest(artifact);
        file.put("name", actualName);
        try {
            file.put("size", Files.size(actualPath));
            file.put("sha256", ResumeArtifactHashes.sha256(actualPath));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Artifact manifest cannot be built");
        }
        return file;
    }

    private ResumeZipBuilder.SourceEntry textEntry(String name, String value, String suffix, List<Path> temps)
            throws IOException {
        Path path = Files.createTempFile("resume-package-entry-", suffix);
        temps.add(path);
        writeLimited(path, output -> writeUtf8(output, value));
        return new ResumeZipBuilder.SourceEntry(name, path);
    }

    private byte[] readLimited(Resource resource) throws IOException {
        try (InputStream input = resource.getInputStream();
             ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             LimitedOutputStream limited = new LimitedOutputStream(
                     bytes, properties.effectiveMaxArtifactBytes())) {
            input.transferTo(limited);
            return bytes.toByteArray();
        }
    }

    private void validateArtifactBytes(ResumeArtifact artifact, byte[] bytes) throws IOException {
        if (!Objects.equals(artifact.getFileSize(), (long) bytes.length)
                || !Objects.equals(artifact.getSha256(), ResumeArtifactHashes.sha256(bytes))) {
            throw new IOException("Artifact integrity mismatch");
        }
    }

    private String readUtf8(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Artifact manifest cannot be read");
        }
    }

    private void validateTemplateDefinition(ResumeAtsTemplate template) {
        try {
            JsonNode definition = objectMapper.readTree(template.getDefinitionJson());
            String actualHash = ResumeArtifactHashes.sha256(template.getDefinitionJson());
            if (definition == null || !definition.isObject()
                    || !Objects.equals(actualHash, template.getDefinitionHash())) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Template definition is invalid or hash does not match");
        }
    }

    private Map<String, Object> embeddedFileManifest(String name, String content) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", name);
        file.put("embedded", true);
        file.put("mimeType", "text/plain; charset=UTF-8");
        file.put("size", content.getBytes(StandardCharsets.UTF_8).length);
        file.put("sha256", ResumeArtifactHashes.sha256(content));
        return file;
    }

    private Map<String, String> applicationMaterials(String packageSnapshot) {
        if (!StringUtils.hasText(packageSnapshot)) {
            return Map.of();
        }
        Map<String, Object> snapshot = readMap(packageSnapshot);
        String company = text(snapshot.get("companyName"), "目标公司");
        String jobTitle = text(snapshot.get("jobTitle"), "目标岗位");
        String readiness = text(snapshot.get("readinessLevel"), "待确认");
        Map<String, Object> match = mapValue(snapshot.get("matchSummary"));
        Map<String, Object> projectCoverage = mapValue(snapshot.get("projectEvidenceCoverage"));
        Map<String, Object> interview = mapValue(snapshot.get("interviewPreparation"));
        List<Map<String, Object>> projects = listOfMaps(projectCoverage.get("selectedEvidence"));
        List<Map<String, Object>> checklist = listOfMaps(snapshot.get("checklist"));
        List<String> gaps = stringList(match.get("gaps"));
        List<String> topics = stringList(interview.get("topics"));

        Map<String, String> materials = new LinkedHashMap<>();
        materials.put("cover-letter-draft.txt", String.join("\n",
                "求职信草稿",
                "",
                "您好，" + company + " 招聘团队：",
                "",
                "我希望申请 " + jobTitle + "。随附简历与项目材料仅引用本次投递包中已确认的经历和证据。",
                "当前岗位就绪度：" + readiness + "。请在发送前结合岗位描述补充称呼、动机和最相关项目，不要添加无法证明的指标。",
                "",
                "感谢您的时间，期待进一步沟通。"));
        materials.put("email-draft.txt", String.join("\n",
                "主题：申请 " + jobTitle + " - [姓名]",
                "",
                "您好，",
                "附件为本次申请的简历与项目材料。我的经历与岗位的相关证据已在投递前检查清单中列出。",
                "如需补充项目细节或作品材料，我会及时提供。",
                "",
                "谢谢。"));
        materials.put("project-case-study.txt", projectCaseStudy(projects));
        materials.put("interview-cheatsheet.txt", interviewCheatsheet(jobTitle, topics, gaps));
        materials.put("preflight-checklist.txt", preflightChecklist(checklist));
        return materials;
    }

    private String projectCaseStudy(List<Map<String, Object>> projects) {
        List<String> lines = new ArrayList<>();
        lines.add("项目案例页");
        lines.add("");
        if (projects.isEmpty()) {
            lines.add("当前投递包没有已确认的项目证据，请在发送前补充。");
            return String.join("\n", lines);
        }
        for (int i = 0; i < projects.size(); i++) {
            Map<String, Object> project = projects.get(i);
            lines.add((i + 1) + ". " + text(project.get("title"), "未命名项目"));
            lines.add("   角色：" + text(project.get("role"), "待补充"));
            lines.add("   技术栈：" + text(project.get("techStack"), "待补充"));
            lines.add("   完整度：" + text(project.get("completenessStatus"), "待确认"));
        }
        return String.join("\n", lines);
    }

    private String interviewCheatsheet(String jobTitle, List<String> topics, List<String> gaps) {
        List<String> lines = new ArrayList<>();
        lines.add("面试速查页 - " + jobTitle);
        lines.add("");
        lines.add("重点准备：");
        lines.addAll(topics.isEmpty() ? List.of("- 暂无已确认主题，请从岗位要求矩阵补充。")
                : topics.stream().map(item -> "- " + item).toList());
        lines.add("");
        lines.add("需要补证据的缺口：");
        lines.addAll(gaps.isEmpty() ? List.of("- 暂无已记录缺口。")
                : gaps.stream().map(item -> "- " + item).toList());
        lines.add("");
        lines.add("边界：本页仅整理已有快照，不代表已掌握或已通过真实面试验证。");
        return String.join("\n", lines);
    }

    private String preflightChecklist(List<Map<String, Object>> checklist) {
        List<String> lines = new ArrayList<>();
        lines.add("投递前检查清单");
        lines.add("");
        if (checklist.isEmpty()) {
            lines.add("[ ] 当前没有可用检查项，请人工核对简历版本、岗位、附件和联系方式。");
            return String.join("\n", lines);
        }
        checklist.forEach(item -> lines.add(
                (Boolean.TRUE.equals(item.get("passed")) ? "[x] " : "[ ] ")
                        + text(item.get("label"), text(item.get("key"), "检查项"))
                        + " - " + text(item.get("reason"), "无补充说明")));
        return String.join("\n", lines);
    }

    private Map<String, String> stringMap(Object value) {
        Map<String, Object> raw = mapValue(value);
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(safeZipName(key), Objects.toString(item, "")));
        return result;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(item -> Objects.toString(item, "").trim())
                .filter(StringUtils::hasText).limit(20).toList();
    }

    private String text(Object value, String fallback) {
        String text = Objects.toString(value, "").trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private Map<String, Object> fileManifest(ResumeArtifact artifact) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", safeZipName(artifact.getFileName()));
        file.put("artifactId", artifact.getId());
        file.put("mimeType", artifact.getMimeType());
        file.put("size", artifact.getFileSize());
        file.put("sha256", artifact.getSha256());
        return file;
    }

    private ResumeExportCreateDTO exportRequest(Long versionId, ResumeAtsTemplate template, String format) {
        ResumeExportCreateDTO dto = new ResumeExportCreateDTO();
        dto.setResumeVersionId(versionId);
        dto.setTemplateCode(template.getTemplateCode());
        dto.setTemplateVersion(template.getTemplateVersion());
        dto.setFormat(format);
        return dto;
    }

    private ResumeAtsTemplate template(String templateCode, Integer templateVersion) {
        String code = StringUtils.hasText(templateCode) ? templateCode.trim().toUpperCase(Locale.ROOT) : DEFAULT_TEMPLATE;
        LambdaQueryWrapper<ResumeAtsTemplate> query = new LambdaQueryWrapper<ResumeAtsTemplate>()
                .eq(ResumeAtsTemplate::getTemplateCode, code)
                .eq(templateVersion != null, ResumeAtsTemplate::getTemplateVersion, templateVersion)
                .eq(ResumeAtsTemplate::getStatus, "ACTIVE")
                .eq(ResumeAtsTemplate::getDeleted, CommonConstants.NO)
                .orderByDesc(ResumeAtsTemplate::getTemplateVersion)
                .last("LIMIT 1");
        ResumeAtsTemplate template = templateMapper.selectOne(query);
        if (template == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "ATS template version does not exist");
        }
        return template;
    }

    private ResumeDocumentRenderer renderer(String format) {
        return renderers.stream().filter(item -> format.equals(item.format())).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "Export format is not supported"));
    }

    private JobApplicationPackage ownedPackage(Long packageId, Long userId) {
        if (packageId == null) {
            return null;
        }
        JobApplicationPackage item = packageMapper.selectOne(new LambdaQueryWrapper<JobApplicationPackage>()
                .eq(JobApplicationPackage::getId, packageId)
                .eq(JobApplicationPackage::getUserId, userId)
                .eq(JobApplicationPackage::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (item == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Application package does not exist");
        }
        return item;
    }

    private ResumeArtifact ownedArtifact(Long artifactId, Long userId) {
        return requireOwnedArtifact(artifactId, userId, ErrorCode.PARAM_ERROR);
    }

    private ResumeArtifact ownedArtifactForRead(Long artifactId, Long userId) {
        return requireOwnedArtifact(artifactId, userId, ErrorCode.RESOURCE_NOT_FOUND);
    }

    private ResumeArtifact requireOwnedArtifact(Long artifactId, Long userId, ErrorCode missingErrorCode) {
        if (artifactId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume artifact id is required");
        }
        ResumeArtifact artifact = artifactMapper.selectOne(new LambdaQueryWrapper<ResumeArtifact>()
                .eq(ResumeArtifact::getId, artifactId)
                .eq(ResumeArtifact::getUserId, userId)
                .eq(ResumeArtifact::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (artifact == null) {
            throw new BusinessException(missingErrorCode, "Resume artifact does not exist");
        }
        return artifact;
    }

    private void markPackageArtifactFailed(ResumeArtifact artifact, Exception ex) {
        if (artifact == null || artifact.getId() == null) {
            return;
        }
        artifact.setStatus(STATUS_FAILED);
        artifact.setErrorMessage(packageFailureMessage(ex));
        try {
            artifactMapper.updateById(artifact);
        } catch (RuntimeException updateException) {
            log.warn("Failed to persist application package failure artifactId={} exceptionType={}",
                    artifact.getId(), updateException.getClass().getSimpleName());
        }
    }

    private void compensatePackageChildren(
            List<ResumeExportVO> generatedChildren,
            Long userId,
            Exception failure) {
        String error = packageFailureMessage(failure);
        for (ResumeExportVO generated : generatedChildren) {
            Long artifactId = generated.getArtifact() == null ? null : generated.getArtifact().getId();
            if (artifactId != null) {
                try {
                    ResumeArtifact child = ownedArtifact(artifactId, userId);
                    deleteUploadedFileQuietly(child.getFileId(), userId);
                    child.setStatus(STATUS_FAILED);
                    child.setErrorMessage(error);
                    artifactMapper.updateById(child);
                } catch (RuntimeException compensationException) {
                    log.warn("Failed to compensate application package child artifactId={} exceptionType={}",
                            artifactId, compensationException.getClass().getSimpleName());
                }
            }
            if (generated.getId() == null) {
                continue;
            }
            try {
                ResumeExport childExport = exportMapper.selectById(generated.getId());
                if (childExport != null
                        && userId.equals(childExport.getUserId())
                        && Objects.equals(artifactId, childExport.getArtifactId())) {
                    childExport.setStatus(STATUS_FAILED);
                    childExport.setErrorMessage(error);
                    exportMapper.updateById(childExport);
                }
            } catch (RuntimeException compensationException) {
                log.warn("Failed to compensate application package child exportId={} exceptionType={}",
                        generated.getId(), compensationException.getClass().getSimpleName());
            }
        }
    }

    private void markFailed(ResumeArtifact artifact, ResumeExport export, Exception ex) {
        String error = safeError(ex);
        if (artifact.getId() != null) {
            artifact.setStatus(STATUS_FAILED);
            artifact.setErrorMessage(error);
            try {
                artifactMapper.updateById(artifact);
            } catch (RuntimeException ignored) {
            }
        }
        if (export.getId() != null) {
            export.setStatus(STATUS_FAILED);
            export.setErrorMessage(error);
            try {
                exportMapper.updateById(export);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private String packageFailureMessage(Exception ex) {
        return "Application package generation failed; child artifact compensation was attempted: "
                + safeError(ex);
    }

    private void deleteUploadedFileQuietly(Long fileId, Long userId) {
        if (fileId == null) {
            return;
        }
        try {
            fileFeignClient.delete(fileId, userId, BIZ_TYPE);
        } catch (RuntimeException ignored) {
        }
    }

    private void requireWrite(int affectedRows, Long id, String message) {
        if (affectedRows != 1 || id == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, message);
        }
    }

    private void validateSourceSize(String snapshotJson) {
        if (snapshotJson == null
                || snapshotJson.getBytes(StandardCharsets.UTF_8).length > properties.effectiveMaxSourceTextBytes()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume version is too large to export");
        }
    }

    private String normalizeFormat(String format) {
        String value = format == null ? "" : format.trim().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("PDF", "DOCX").contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Export format must be PDF or DOCX");
        }
        return value;
    }

    private ResumeAtsTemplateVO toTemplateVO(ResumeAtsTemplate item) {
        ResumeAtsTemplateVO vo = new ResumeAtsTemplateVO();
        vo.setId(item.getId());
        vo.setTemplateCode(item.getTemplateCode());
        vo.setTemplateVersion(item.getTemplateVersion());
        vo.setTemplateName(item.getTemplateName());
        vo.setLayoutType(item.getLayoutType());
        vo.setDefinition(readTree(item.getDefinitionJson()));
        vo.setDefinitionHash(item.getDefinitionHash());
        vo.setStatus(item.getStatus());
        return vo;
    }

    private ResumeExportVO toExportVO(ResumeExport export, ResumeArtifact artifact) {
        ResumeExportVO vo = new ResumeExportVO();
        vo.setId(export.getId());
        vo.setResumeId(export.getResumeId());
        vo.setResumeVersionId(export.getResumeVersionId());
        vo.setSourceHash(export.getSourceHash());
        vo.setTemplateId(export.getTemplateId());
        vo.setTemplateCode(export.getTemplateCode());
        vo.setTemplateVersion(export.getTemplateVersion());
        vo.setExportFormat(export.getExportFormat());
        vo.setStatus(export.getStatus());
        vo.setContentHash(export.getContentHash());
        vo.setErrorMessage(export.getErrorMessage());
        vo.setArtifact(toArtifactVO(artifact));
        vo.setCreatedAt(export.getCreatedAt());
        return vo;
    }

    private ResumeArtifactVO toArtifactVO(ResumeArtifact item) {
        ResumeArtifactVO vo = new ResumeArtifactVO();
        vo.setId(item.getId());
        vo.setArtifactType(item.getArtifactType());
        vo.setSourceResumeId(item.getSourceResumeId());
        vo.setSourceResumeVersionId(item.getSourceResumeVersionId());
        vo.setSourceApplicationPackageId(item.getSourceApplicationPackageId());
        vo.setSourceHash(item.getSourceHash());
        vo.setTemplateCode(item.getTemplateCode());
        vo.setTemplateVersion(item.getTemplateVersion());
        vo.setFileName(item.getFileName());
        vo.setMimeType(item.getMimeType());
        vo.setFileSize(item.getFileSize());
        vo.setSha256(item.getSha256());
        vo.setStatus(item.getStatus());
        vo.setManifest(readTree(item.getManifestJson()));
        vo.setErrorMessage(item.getErrorMessage());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private JsonNode readTree(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Artifact manifest is invalid");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Artifact manifest serialization failed");
        }
    }

    private void writeLimited(Path path, IoConsumer<OutputStream> writer) throws IOException {
        try (OutputStream fileOutput = Files.newOutputStream(path);
             LimitedOutputStream limited = new LimitedOutputStream(fileOutput, properties.effectiveMaxArtifactBytes())) {
            writer.accept(limited);
        }
    }

    private void writeUtf8(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private String extension(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        return index < 0 ? ".bin" : fileName.substring(index);
    }

    private String safeZipName(String name) {
        String safe = name == null ? "artifact.bin" : name.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1);
        return safe.isBlank() || safe.contains("..") ? "artifact.bin" : safe;
    }

    private String safeError(Exception ex) {
        return ex instanceof BusinessException && StringUtils.hasText(ex.getMessage())
                ? ex.getMessage()
                : "Resume artifact generation failed";
    }

    private BusinessException generationException(Exception ex) {
        return ex instanceof BusinessException businessException
                ? businessException
                : new BusinessException(ErrorCode.SYSTEM_ERROR, "Resume artifact generation failed");
    }

    private BusinessException zipRebuildException(Exception ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException;
        }
        if (StringUtils.hasText(ex.getMessage())
                && ex.getMessage().toLowerCase(Locale.ROOT).contains("child artifact")) {
            return new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "Application ZIP child artifact is unavailable or invalid; regenerate the application package");
        }
        return generationException(ex);
    }

    private void logUploadFailure(
            Long artifactId,
            Long exportId,
            long size,
            long startedAt,
            Exception ex) {
        long durationMs = elapsedMillis(startedAt);
        if (ex instanceof BusinessException businessException
                && Objects.equals(businessException.getCode(), ErrorCode.RESUME_UPLOAD_BUSY.getCode())) {
            log.debug("Resume export upload busy artifactId={} exportId={} artifactSize={} durationMs={} exceptionType={}",
                    artifactId, exportId, size, durationMs, ex.getClass().getSimpleName());
            return;
        }
        log.warn("Resume export upload failed artifactId={} exportId={} artifactSize={} durationMs={} exceptionType={}",
                artifactId, exportId, size, durationMs, ex.getClass().getSimpleName());
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    @FunctionalInterface
    private interface IoConsumer<T> {
        void accept(T value) throws IOException;
    }

    private record DownloadPayload(byte[] bytes, String sha256) {
    }
}
