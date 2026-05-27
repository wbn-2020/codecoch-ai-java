package com.codecoachai.common.vector.domain;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VectorPoint {

    private String id;

    private List<Float> vector;

    private Map<String, Object> payload;
}
