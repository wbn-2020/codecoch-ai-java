package com.codecoachai.question.feign.dto;

import java.util.List;
import lombok.Data;

@Data
public class EmbeddingRequestDTO {

    private List<String> texts;

    private String provider;

    private String model;
}
