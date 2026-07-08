package com.codecoachai.ai.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class TraceNodeVO {

    private String id;
    private String nodeType;
    private String title;
    private String status;
    private String sourceModule;
    private String sourceType;
    private String sourceId;
    private String traceId;
    private String requestId;
    private String businessId;
    private String businessType;
    private String bizType;
    private String bizId;
    private String messageId;
    private Long userId;
    private LocalDateTime occurredAt;
    private String summary;
    private String preview;
    private String contentHash;
    private Integer contentLength;
    private List<TracePreviewItemVO> previews = new ArrayList<>();
    private TraceRawAccessStatusVO rawAccess;
    private String associationType;
    private String associationConfidence;
    private String associationReason;
    private List<TraceLinkVO> links = new ArrayList<>();
    private Map<String, Object> meta = new LinkedHashMap<>();
}
