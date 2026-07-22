package com.codecoachai.resume.campaignarchive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.config.V8FeatureGate;
import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.codecoachai.resume.feign.CampaignArchiveAiFeignClient;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.feign.vo.CampaignArchiveAiSourceVO;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
@RequiredArgsConstructor
@Slf4j
public class CareerCampaignArchiveServiceImpl implements CareerCampaignArchiveService {

    private static final String STATUS_GENERATING = "GENERATING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final String FORMAT_ZIP = "ZIP";
    private static final String BIZ_TYPE = "CAREER_CAMPAIGN_ARCHIVE";

    private final CareerCampaignArchiveMapper archiveMapper;
    private final CareerCampaignArchiveBuilder builder;
    private final CareerCampaignArchiveProperties properties;
    private final CampaignArchiveAiFeignClient aiFeignClient;
    private final FileFeignClient fileFeignClient;
    private final V8FeatureGate featureGate;
    private final ObjectMapper objectMapper;

    @Override
    public CareerCampaignArchiveModels.View create(
            Long campaignId, CareerCampaignArchiveModels.CreateRequest request) {
        featureGate.requireCampaignExport();
        Long userId = SecurityAssert.requireLoginUserId();
        if (campaignId == null || request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "周期和档案导出请求不能为空。");
        }
        String format = normalizeFormat(request.getExportFormat());
        String idempotencyHash = ResumeArtifactHashes.sha256(requireIdempotencyKey(request));
        LocalDateTime cutoff = normalizeCutoff(request.getDataCutoffAt());
        CareerCampaignArchiveExport byKey = archiveMapper.selectByIdempotency(userId, idempotencyHash);
        if (byKey != null) {
            assertSameRequest(byKey, campaignId, cutoff, format);
            if (STATUS_READY.equals(byKey.getStatus())
                    || STATUS_GENERATING.equals(byKey.getStatus())
                    || !Boolean.TRUE.equals(request.getRetryFailed())) {
                return toView(byKey);
            }
        }

        CareerCampaignArchiveModels.ArchiveBundle bundle = collectBundle(userId, campaignId, cutoff);
        String sourceHash = sourceHash(bundle, cutoff);
        CareerCampaignArchiveExport record = byKey;
        if (record == null) {
            record = archiveMapper.selectBySource(userId, campaignId, cutoff, format, sourceHash);
        }
        if (record != null) {
            if (STATUS_READY.equals(record.getStatus())
                    || STATUS_GENERATING.equals(record.getStatus())
                    || !Boolean.TRUE.equals(request.getRetryFailed())) {
                return toView(record);
            }
            if (archiveMapper.claimRetry(
                    userId, record.getId(), sourceHash, idempotencyHash) != 1) {
                CareerCampaignArchiveExport winner = archiveMapper.selectOwned(userId, record.getId());
                return winner == null ? toView(record) : toView(winner);
            }
            record = archiveMapper.selectOwned(userId, record.getId());
        } else {
            record = new CareerCampaignArchiveExport();
            record.setUserId(userId);
            record.setCampaignId(campaignId);
            record.setDataCutoffAt(cutoff);
            record.setExportFormat(format);
            record.setStatus(STATUS_GENERATING);
            record.setSourceHash(sourceHash);
            record.setIdempotencyKeyHash(idempotencyHash);
            try {
                if (archiveMapper.insert(record) != 1) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                            "求职周期档案生成记录创建失败。");
                }
            } catch (RuntimeException ex) {
                CareerCampaignArchiveExport winner = archiveMapper.selectByIdempotency(userId, idempotencyHash);
                if (winner == null) {
                    winner = archiveMapper.selectBySource(userId, campaignId, cutoff, format, sourceHash);
                }
                if (winner != null) {
                    return toView(winner);
                }
                throw ex;
            }
        }

        try {
            CareerCampaignArchiveModels.ArchiveResult result =
                    builder.build(bundle, cutoff, sourceHash, properties);
            InnerFileUploadVO uploaded = FeignResultUtils.unwrap(fileFeignClient.upload(
                    new ByteArrayMultipartFile(
                            result.getZipBytes(),
                            "career-campaign-" + campaignId + "-archive.zip",
                            "application/zip"),
                    BIZ_TYPE,
                    userId));
            if (uploaded == null || uploaded.getFileId() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "求职周期档案上传后未返回文件。");
            }
            record.setFileId(uploaded.getFileId());
            record.setFileSize(result.getFileSize());
            record.setManifestHash(result.getManifestHash());
            record.setStatus(STATUS_READY);
            record.setErrorCode(null);
            record.setErrorMessage(null);
            if (archiveMapper.updateById(record) != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "求职周期档案生成记录未能完成。");
            }
            return toView(record);
        } catch (Exception ex) {
            markFailed(record, ex);
            throw generationException(ex);
        }
    }

    @Override
    public List<CareerCampaignArchiveModels.View> list(Long campaignId) {
        featureGate.requireCampaignExport();
        Long userId = SecurityAssert.requireLoginUserId();
        if (campaignId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "周期不能为空。");
        }
        if (archiveMapper.selectCampaign(
                userId, campaignId, LocalDateTime.now().withNano(0)) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "求职周期不存在。");
        }
        return archiveMapper.selectList(new LambdaQueryWrapper<CareerCampaignArchiveExport>()
                        .eq(CareerCampaignArchiveExport::getUserId, userId)
                        .eq(CareerCampaignArchiveExport::getCampaignId, campaignId)
                        .eq(CareerCampaignArchiveExport::getDeleted, CommonConstants.NO)
                        .orderByDesc(CareerCampaignArchiveExport::getCreatedAt)
                        .orderByDesc(CareerCampaignArchiveExport::getId)
                        .last("LIMIT 100"))
                .stream().map(this::toView).toList();
    }

    @Override
    public CareerCampaignArchiveModels.View get(Long exportId) {
        featureGate.requireCampaignExport();
        Long userId = SecurityAssert.requireLoginUserId();
        CareerCampaignArchiveExport record = archiveMapper.selectOwned(userId, exportId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "求职周期档案导出记录不存在。");
        }
        return toView(record);
    }

    @Override
    public ResponseEntity<StreamingResponseBody> download(Long exportId) {
        featureGate.requireCampaignExport();
        Long userId = SecurityAssert.requireLoginUserId();
        CareerCampaignArchiveExport record = archiveMapper.selectOwned(userId, exportId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "求职周期档案导出记录不存在。");
        }
        if (!STATUS_READY.equals(record.getStatus()) || record.getFileId() == null) {
            throw new BusinessException(ErrorCode.SEMANTIC_VALIDATION_ERROR,
                    "求职周期档案尚未生成完成。");
        }
        ResponseEntity<Resource> response = fileFeignClient.download(record.getFileId(), userId, BIZ_TYPE);
        Resource resource = response == null ? null : response.getBody();
        if (resource == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "求职周期档案文件暂不可用。");
        }
        StreamingResponseBody body = output -> {
            try (var input = resource.getInputStream()) {
                input.transferTo(output);
            }
        };
        String fileName = "career-campaign-" + record.getCampaignId() + "-archive.zip";
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                        + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"))
                .header("X-Archive-Manifest-SHA256", record.getManifestHash());
        if (record.getFileSize() != null && record.getFileSize() >= 0L) {
            responseBuilder.contentLength(record.getFileSize());
        }
        return responseBuilder.body(body);
    }

    private CareerCampaignArchiveModels.ArchiveBundle collectBundle(
            Long userId, Long campaignId, LocalDateTime cutoff) {
        int limit = properties.effectiveMaxRowsPerSection();
        CareerCampaignArchiveModels.CampaignRow campaign =
                archiveMapper.selectCampaign(userId, campaignId, cutoff);
        if (campaign == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "求职周期不存在。");
        }
        String status = normalizeStatus(campaign.getStatus());
        if (!Set.of("ACTIVE", "PAUSED", "COMPLETED", "ARCHIVED").contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "当前周期状态不允许导出档案。");
        }
        CareerCampaignArchiveModels.ArchiveBundle bundle = new CareerCampaignArchiveModels.ArchiveBundle();
        bundle.setCampaign(campaign);
        int queryLimit = limit + 1;
        bundle.setApplications(loadSection(bundle, "applications", limit,
                () -> archiveMapper.selectApplications(userId, campaignId, cutoff, queryLimit)));
        bundle.setTimeline(loadSection(bundle, "timeline", limit,
                () -> archiveMapper.selectTimeline(userId, campaignId, cutoff, queryLimit)));
        bundle.setCalendar(loadSection(bundle, "calendar", limit,
                () -> archiveMapper.selectCalendar(userId, campaignId, cutoff, queryLimit)));
        bundle.setInterviews(loadSection(bundle, "interviews", limit,
                () -> archiveMapper.selectInterviews(userId, campaignId, cutoff, queryLimit)));
        bundle.setOffers(loadSection(bundle, "offers", limit,
                () -> archiveMapper.selectOffers(userId, campaignId, cutoff, queryLimit)));
        bundle.setContacts(loadSection(bundle, "contacts", limit,
                () -> archiveMapper.selectContacts(userId, campaignId, cutoff, queryLimit)));
        bundle.setActivities(loadSection(bundle, "activities", limit,
                () -> archiveMapper.selectActivities(userId, campaignId, cutoff, queryLimit)));
        bundle.setResearchSnapshots(loadSection(bundle, "research-snapshots", limit,
                () -> archiveMapper.selectResearchSnapshots(
                        userId, campaignId, cutoff, queryLimit)));
        try {
            CampaignArchiveAiSourceVO ai = FeignResultUtils.unwrap(
                    aiFeignClient.getSource(userId, campaignId, cutoff));
            if (ai == null) {
                bundle.getMissingSections().add("agent-pulses");
                bundle.getMissingSections().add("campaign-review");
            } else {
                bundle.setAgentPulses(objectMapper.valueToTree(
                        ai.getPulses() == null ? List.of() : ai.getPulses()));
                bundle.setCampaignReviewMarkdown(reviewMarkdown(ai.getReview()));
                bundle.setAiSourceHash(ai.getSourceHash());
                append(bundle.getMissingSections(), ai.getMissingSections());
                if (!"READY".equalsIgnoreCase(ai.getSourceStatus())) {
                    bundle.getWarnings().add("AI 周期来源为部分可用状态，缺失区块已写入 manifest。");
                }
            }
        } catch (Exception ex) {
            bundle.getMissingSections().add("agent-pulses");
            bundle.getMissingSections().add("campaign-review");
            bundle.getWarnings().add("AI 周期来源暂不可用，已保留 Resume 侧事实并完成部分导出。");
            log.warn("Campaign archive AI source unavailable campaignId={} cutoff={}",
                    campaignId, cutoff, ex);
        }
        return bundle;
    }

    private <T> List<T> loadSection(
            CareerCampaignArchiveModels.ArchiveBundle bundle,
            String section,
            int limit,
            Supplier<List<T>> loader) {
        try {
            List<T> values = loader.get();
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            if (values.size() > limit) {
                bundle.getWarnings().add(section + " 区块超过条目上限，已截断为 " + limit + " 条。");
                return List.copyOf(values.subList(0, limit));
            }
            return values;
        } catch (RuntimeException ex) {
            bundle.getMissingSections().add(section);
            bundle.getWarnings().add(section + " 区块暂不可用，档案已按部分来源生成。");
            log.warn("Campaign archive section unavailable section={} campaignId={}",
                    section, bundle.getCampaign().getId(), ex);
            return List.of();
        }
    }

    private String sourceHash(CareerCampaignArchiveModels.ArchiveBundle bundle, LocalDateTime cutoff) {
        try {
            String canonical = objectMapper.writeValueAsString(new Object[] {
                    bundle.getCampaign(), bundle.getApplications(), bundle.getTimeline(),
                    bundle.getCalendar(), bundle.getInterviews(), bundle.getOffers(),
                    bundle.getContacts(), bundle.getActivities(), bundle.getResearchSnapshots(),
                    bundle.getAgentPulses(), bundle.getCampaignReviewMarkdown(),
                    bundle.getAiSourceHash(), cutoff, bundle.getMissingSections(),
                    bundle.getWarnings(),
                    bundle.getResearchSnapshots().stream()
                            .map(CareerCampaignArchiveModels.ResearchSnapshotRow::getSnapshotJson)
                            .toList()
            });
            return ResumeArtifactHashes.sha256(canonical);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "求职周期档案来源哈希计算失败。");
        }
    }

    private void markFailed(CareerCampaignArchiveExport record, Exception ex) {
        record.setStatus(STATUS_FAILED);
        record.setErrorCode("ARCHIVE_EXPORT_FAILED");
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        record.setErrorMessage(message.substring(0,
                Math.min(message.length(), properties.effectiveMaxErrorMessageChars())));
        try {
            archiveMapper.updateById(record);
        } catch (RuntimeException updateError) {
            log.error("Could not mark campaign archive export failed exportId={}",
                    record.getId(), updateError);
        }
    }

    private BusinessException generationException(Exception ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException;
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR, "求职周期档案导出失败。");
    }

    private void assertSameRequest(CareerCampaignArchiveExport record, Long campaignId,
                                   LocalDateTime cutoff, String format) {
        if (!Objects.equals(record.getCampaignId(), campaignId)
                || !Objects.equals(record.getDataCutoffAt(), cutoff)
                || !Objects.equals(record.getExportFormat(), format)) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "同一幂等键不能用于不同的档案导出请求。");
        }
    }

    private CareerCampaignArchiveModels.View toView(CareerCampaignArchiveExport record) {
        CareerCampaignArchiveModels.View view = new CareerCampaignArchiveModels.View();
        view.setId(record.getId());
        view.setUserId(record.getUserId());
        view.setCampaignId(record.getCampaignId());
        view.setDataCutoffAt(record.getDataCutoffAt());
        view.setExportFormat(record.getExportFormat());
        view.setStatus(record.getStatus());
        view.setSourceHash(record.getSourceHash());
        view.setManifestHash(record.getManifestHash());
        view.setFileId(record.getFileId());
        view.setFileSize(record.getFileSize());
        view.setErrorCode(record.getErrorCode());
        view.setErrorMessage(record.getErrorMessage());
        view.setIdempotencyKeyHash(record.getIdempotencyKeyHash());
        view.setCreatedAt(record.getCreatedAt());
        view.setUpdatedAt(record.getUpdatedAt());
        return view;
    }

    private LocalDateTime normalizeCutoff(LocalDateTime value) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (value == null) {
            return now;
        }
        LocalDateTime normalized = value.withNano(0);
        if (normalized.isAfter(now)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "数据截点不能晚于当前时间。");
        }
        return normalized;
    }

    private String normalizeFormat(String value) {
        String format = value == null ? FORMAT_ZIP : value.trim().toUpperCase(Locale.ROOT);
        if (!FORMAT_ZIP.equals(format)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前仅支持 ZIP 档案导出。");
        }
        return format;
    }

    private String requireIdempotencyKey(CareerCampaignArchiveModels.CreateRequest request) {
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "幂等键不能为空。");
        }
        return request.getIdempotencyKey().trim();
    }

    private String reviewMarkdown(CampaignArchiveAiSourceVO.Review review) {
        if (review == null) {
            return "## 周期复盘\n\n当前没有可导出的周期复盘摘要。\n";
        }
        StringBuilder markdown = new StringBuilder("## 周期复盘\n\n");
        markdown.append("- 快照版本：").append(nullText(review.getSnapshotVersion())).append('\n')
                .append("- 数据截点：").append(nullText(review.getDataCutoffAt())).append('\n')
                .append("- 置信度：").append(nullText(review.getConfidenceLevel())).append('\n')
                .append("- 结果来源：").append(nullText(review.getResultSource())).append('\n')
                .append("- 规则降级：").append(Boolean.TRUE.equals(review.getFallback()) ? "是" : "否")
                .append("\n\n");
        if (review.getSummary() != null && !review.getSummary().isBlank()) {
            markdown.append(review.getSummary().trim()).append("\n\n");
        }
        appendJsonSection(markdown, "事实", review.getFacts());
        appendJsonSection(markdown, "覆盖与限制", review.getCoverage());
        appendJsonSection(markdown, "边界", review.getLimits());
        appendJsonSection(markdown, "信号", review.getSignals());
        appendJsonSection(markdown, "下一周期行动", review.getNextCycleActions());
        return markdown.toString();
    }

    private void appendJsonSection(StringBuilder markdown, String title, Object value) {
        if (value == null) {
            return;
        }
        try {
            markdown.append("### ").append(title).append("\n\n```json\n")
                    .append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value))
                    .append("\n```\n\n");
        } catch (IOException ex) {
            markdown.append("### ").append(title).append("\n\n该区块无法序列化。\n\n");
        }
    }

    private String nullText(Object value) {
        return value == null ? "未提供" : String.valueOf(value);
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private void append(List<String> target, List<String> values) {
        if (values != null) {
            values.stream().filter(Objects::nonNull).filter(item -> !item.isBlank())
                    .forEach(item -> {
                        if (!target.contains(item)) {
                            target.add(item);
                        }
                    });
        }
    }

    private static final class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String filename;
        private final String contentType;

        private ByteArrayMultipartFile(byte[] content, String filename, String contentType) {
            this.content = content;
            this.filename = filename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content.clone();
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
