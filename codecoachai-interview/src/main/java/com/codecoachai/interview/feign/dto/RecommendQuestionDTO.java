package com.codecoachai.interview.feign.dto;

import java.util.List;
import lombok.Data;

@Data
public class RecommendQuestionDTO {

    private List<String> weakTags;
    private Long limit = 5L;
}
