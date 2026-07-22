package com.codecoachai.resume.campaignarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class CareerCampaignArchiveBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void buildsManifestPlusTwelveUtf8EntriesWithHashes() throws Exception {
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
        assertEquals(13, entries.size());
        assertTrue(entries.containsKey("manifest.json"));
        assertEquals("中文求职周期",
                objectMapper.readTree(entries.get("campaign.json")).path("name").asText());
        assertTrue(new String(entries.get("README.md"), java.nio.charset.StandardCharsets.UTF_8)
                .contains("求职周期档案"));
        JsonNode manifest = objectMapper.readTree(entries.get("manifest.json"));
        assertEquals(12, manifest.path("files").size());
        for (JsonNode file : manifest.path("files")) {
            byte[] content = entries.get(file.path("name").asText());
            assertEquals(content.length, file.path("size").asLong());
            assertEquals(ResumeArtifactHashes.sha256(content), file.path("sha256").asText());
        }
        assertEquals(result.getManifestHash(),
                ResumeArtifactHashes.sha256(entries.get("manifest.json")));
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
