package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class EvidenceAssetEnvelopeVO<T> {

    private List<T> items = new ArrayList<>();
    private Long total = 0L;
    private Long pageNo;
    private Long pageSize;
    private LocalDateTime dataCutoffAt;
    private String sourceSetHash;
    private Map<String, Object> coverage = new LinkedHashMap<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> unknowns = new ArrayList<>();
    private List<String> limits = new ArrayList<>();
    private String confidenceLevel = "LOW";
    private Boolean fallback = false;
    private String fallbackReason;
    private List<CareerEvidenceUsageVO.SourceRef> sources = new ArrayList<>();
}
