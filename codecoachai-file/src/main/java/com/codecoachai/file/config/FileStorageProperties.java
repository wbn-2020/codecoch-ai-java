package com.codecoachai.file.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "codecoachai.file.storage")
public class FileStorageProperties {

    private String provider = "LOCAL";

    private String rootPath = "./data/uploads";

    private long maxSizeMb = 10L;

    private List<String> allowedExtensions = List.of(
            "pdf", "doc", "docx", "md", "txt",
            "jpg", "jpeg", "png",
            "webm", "wav", "mp3", "m4a", "ogg");
}
