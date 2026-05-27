package com.codecoachai.common.vector.domain;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VectorSearchResult {

    private String id;

    private double score;

    private Map<String, Object> payload;
}
