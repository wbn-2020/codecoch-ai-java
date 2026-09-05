package com.codecoachai.resume.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

@Component
public class ResumeZipBuilder {

    private final ObjectMapper objectMapper;

    public ResumeZipBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(OutputStream output, List<SourceEntry> entries, Map<String, Object> manifest,
                      int maxEntries) throws IOException {
        if (entries.size() + 1 > maxEntries) {
            throw new IOException("ZIP entry limit exceeded");
        }
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (SourceEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(safeName(entry.name()));
                zipEntry.setTime(0L);
                zip.putNextEntry(zipEntry);
                try (InputStream input = Files.newInputStream(entry.path())) {
                    input.transferTo(zip);
                }
                zip.closeEntry();
            }
            ZipEntry manifestEntry = new ZipEntry("manifest.json");
            manifestEntry.setTime(0L);
            zip.putNextEntry(manifestEntry);
            zip.write(objectMapper.writeValueAsBytes(manifest));
            zip.closeEntry();
            zip.finish();
        }
    }

    private String safeName(String name) {
        String safe = name == null ? "artifact.bin" : name.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1);
        return safe.isBlank() || safe.contains("..") ? "artifact.bin" : safe;
    }

    public record SourceEntry(String name, Path path) {
    }
}
