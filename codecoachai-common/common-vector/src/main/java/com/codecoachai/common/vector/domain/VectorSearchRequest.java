package com.codecoachai.common.vector.domain;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VectorSearchRequest {

    private String collectionName;

    private List<Float> vector;

    private Map<String, Object> mustMatchPayload;

    private Integer limit;

    private Double scoreThreshold;
}
