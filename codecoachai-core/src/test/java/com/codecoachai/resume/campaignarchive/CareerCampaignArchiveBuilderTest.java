package com.codecoachai.resume.campaignarchive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class CareerCampaignArchiveBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void buildsManifestPlusFourteenUtf8EntriesWithHashes() throws Exception {
        CareerCampaignArchiveModels.ArchiveBundle bundle =
                new CareerCampaignArchiveModels.ArchiveBundle();
        CareerCampaignArchiveModels.CampaignRow campaign =
                new CareerCampaignArchiveModels.CampaignRow();
        campaign.setId(12L);
        campaign.setName("中文求职周期");
        campaign.setStatus("ACTIVE");
        bundle.setCampaign(campaign);
        bundle.setCampaignReviewMarkdown("## 周期复盘\n\n保持中文摘要。");
        bundle.setAgentPulses(objectMapper.readTree("[{\"summary\":\"继续跟进\"}]"));

        CareerCampaignArchiveProperties properties = new CareerCampaignArchiveProperties();
        CareerCampaignArchiveModels.ArchiveResult result =
                new CareerCampaignArchiveBuilder(objectMapper).build(
                        bundle, LocalDateTime.of(2026, 7, 22, 12, 0), "a".repeat(64), properties);

        Map<String, byte[]> entries = unzip(result.getZipBytes());
        assertEquals(15, entries.size());
        assertTrue(entries.containsKey("manifest.json"));
        assertTrue(entries.containsKey("evidence_usage.json"));
        assertTrue(entries.containsKey("evidence_usage_results.json"));
        assertEquals("中文求职周期",
                objectMapper.readTree(entries.get("campaign.json")).path("name").asText());
        assertTrue(new String(entries.get("README.md"), java.nio.charset.StandardCharsets.UTF_8)
                .contains("求职周期档案"));
        JsonNode manifest = objectMapper.readTree(entries.get("manifest.json"));
        assertEquals("v8.campaign-archive.v2", manifest.path("schemaVersion").asText());
        assertEquals(14, manifest.path("files").size());
        JsonNode usageSection = objectMapper.readTree(entries.get("evidence_usage.json"));
        assertEquals("V9_EVIDENCE_ARCHIVE_SECTION_V1",
                usageSection.path("schemaVersion").asText());
        assertEquals("LOW", usageSection.path("confidenceLevel").asText());
        assertTrue(usageSection.path("coverage").path("available").asBoolean());
        assertEquals(0, usageSection.path("coverage").path("itemCount").asInt());
        assertEquals(usageSection.path("contentHash").asText(),
                recomputeContentHash(usageSection));
        assertEquals(15, properties.effectiveMaxEntries());
        for (JsonNode file : manifest.path("files")) {
            byte[] content = entries.get(file.path("name").asText());
            assertEquals(content.length, file.path("size").asLong());
            assertEquals(ResumeArtifactHashes.sha256(content), file.path("sha256").asText());
        }
        assertEquals(result.getManifestHash(),
                ResumeArtifactHashes.sha256(entries.get("manifest.json")));

        CareerCampaignArchiveModels.ArchiveResult repeated =
                new CareerCampaignArchiveBuilder(objectMapper).build(
                        bundle, LocalDateTime.of(2026, 7, 22, 12, 0),
                        "a".repeat(64), properties);
        assertEquals(result.getManifestHash(), repeated.getManifestHash());
        assertArrayEquals(result.getZipBytes(), repeated.getZipBytes());
    }

    @Test
    void keepsEvidenceSectionAvailabilityAndWarningsIndependent() throws Exception {
        CareerCampaignArchiveModels.ArchiveBundle bundle = bundle(12L);
        CareerCampaignArchiveModels.SectionMetadata usageMetadata =
                bundle.getEvidenceUsageSection();
        usageMetadata.setAvailable(false);
        usageMetadata.getMissingSections().add("evidence_usage");
        usageMetadata.getWarnings().add("evidence_usage 区块暂不可用。");
        bundle.getMissingSections().add("evidence_usage");
        bundle.getWarnings().add("evidence_usage 区块暂不可用。");

        CareerCampaignArchiveModels.ArchiveResult result =
                new CareerCampaignArchiveBuilder(objectMapper).build(
                        bundle, LocalDateTime.of(2026, 7, 22, 12, 0),
                        "b".repeat(64), new CareerCampaignArchiveProperties());
        Map<String, byte[]> entries = unzip(result.getZipBytes());
        JsonNode usage = objectMapper.readTree(entries.get("evidence_usage.json"));
        JsonNode results = objectMapper.readTree(entries.get("evidence_usage_results.json"));

        assertFalse(usage.path("coverage").path("available").asBoolean());
        assertEquals(List.of("evidence_usage"),
                objectMapper.convertValue(usage.path("missingSections"), List.class));
        assertEquals(1, usage.path("warnings").size());
        assertTrue(results.path("coverage").path("available").asBoolean());
        assertEquals(0, results.path("missingSections").size());
        assertEquals(0, results.path("warnings").size());
        assertNotEquals(usage.path("contentHash").asText(),
                results.path("contentHash").asText());
        assertEquals(usage.path("contentHash").asText(), recomputeContentHash(usage));
        assertEquals(results.path("contentHash").asText(), recomputeContentHash(results));
    }

    @Test
    void capsEvidenceSectionConfidenceAtMedium() throws Exception {
        CareerCampaignArchiveModels.ArchiveBundle bundle = bundle(12L);
        List<CareerCampaignArchiveModels.EvidenceUsageRow> fourUsages = new ArrayList<>();
        for (long id = 1; id <= 4; id++) {
            CareerCampaignArchiveModels.EvidenceUsageRow row =
                    new CareerCampaignArchiveModels.EvidenceUsageRow();
            row.setId(id);
            fourUsages.add(row);
        }
        List<CareerCampaignArchiveModels.EvidenceUsageResultRow> fifteenResults =
                new ArrayList<>();
        for (long id = 1; id <= 15; id++) {
            CareerCampaignArchiveModels.EvidenceUsageResultRow row =
                    new CareerCampaignArchiveModels.EvidenceUsageResultRow();
            row.setId(id);
            row.setSnapshotId(100L + id);
            row.setStatus("RECORDED");
            fifteenResults.add(row);
        }
        bundle.setEvidenceUsages(fourUsages);
        bundle.setEvidenceUsageResults(fifteenResults);

        CareerCampaignArchiveModels.ArchiveResult result =
                new CareerCampaignArchiveBuilder(objectMapper).build(
                        bundle, LocalDateTime.of(2026, 7, 22, 12, 0),
                        "c".repeat(64), new CareerCampaignArchiveProperties());
        Map<String, byte[]> entries = unzip(result.getZipBytes());
        JsonNode usage = objectMapper.readTree(entries.get("evidence_usage.json"));
        JsonNode results = objectMapper.readTree(entries.get("evidence_usage_results.json"));

        assertEquals("LOW", usage.path("confidenceLevel").asText());
        assertEquals("MEDIUM", results.path("confidenceLevel").asText());
        assertNotEquals("HIGH", results.path("confidenceLevel").asText());
        assertTrue(usage.path("limits").get(0).asText().contains("少于 5 条"));
        assertTrue(results.path("limits").get(0).asText().contains("有限观察"));
    }

    @Test
    void rejectsUnsafeEntryNamesAndConfiguredSizeLimits() {
        assertThrows(java.io.IOException.class,
                () -> CareerCampaignArchiveBuilder.validateEntryName("../campaign.json"));

        CareerCampaignArchiveModels.ArchiveBundle bundle =
                new CareerCampaignArchiveModels.ArchiveBundle();
        CareerCampaignArchiveModels.CampaignRow campaign =
                new CareerCampaignArchiveModels.CampaignRow();
        campaign.setId(1L);
        campaign.setName("周期");
        bundle.setCampaign(campaign);
        CareerCampaignArchiveProperties properties = new CareerCampaignArchiveProperties();
        properties.setMaxEntryBytes(8);

        assertThrows(java.io.IOException.class,
                () -> new CareerCampaignArchiveBuilder(objectMapper).build(
                        bundle, LocalDateTime.of(2026, 7, 22, 12, 0), "b".repeat(64), properties));
    }

    private CareerCampaignArchiveModels.ArchiveBundle bundle(Long campaignId) {
        CareerCampaignArchiveModels.ArchiveBundle bundle =
                new CareerCampaignArchiveModels.ArchiveBundle();
        CareerCampaignArchiveModels.CampaignRow campaign =
                new CareerCampaignArchiveModels.CampaignRow();
        campaign.setId(campaignId);
        campaign.setName("周期");
        campaign.setStatus("ACTIVE");
        bundle.setCampaign(campaign);
        return bundle;
    }

    private String recomputeContentHash(JsonNode section) throws Exception {
        ObjectNode hashInput = ((ObjectNode) section).deepCopy();
        hashInput.remove("contentHash");
        return ResumeArtifactHashes.sha256(objectMapper.writeValueAsBytes(hashInput));
    }

    private Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                result.put(entry.getName(), input.readAllBytes());
            }
        }
        return result;
    }
}
