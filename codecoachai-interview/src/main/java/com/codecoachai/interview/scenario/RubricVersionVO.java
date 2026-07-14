package com.codecoachai.interview.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RubricVersionVO {

    private Long rubricVersionId;
    private String rubricCode;
    private Integer versionNo;
    private String rubricName;
    private String description;
    private String locale;
    private JsonNode dimensions;
    private String versionStatus;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
