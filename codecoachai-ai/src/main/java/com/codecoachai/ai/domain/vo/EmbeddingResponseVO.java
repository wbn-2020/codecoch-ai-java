package com.codecoachai.ai.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class EmbeddingResponseVO {

    private String provider;

    private String model;

    private Integer dimension;

    private List<List<Float>> vectors;

    private Integer totalTokens;

    private Long elapsedMs;
}
