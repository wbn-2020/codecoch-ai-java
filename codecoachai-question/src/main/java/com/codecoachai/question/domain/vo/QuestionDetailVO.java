package com.codecoachai.question.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class QuestionDetailVO {

    private Long id;
    private String title;
    private String content;
    private String referenceAnswer;
    private String analysis;
    private Long categoryId;
    private String categoryName;
    private Long groupId;
    private String groupName;
    private String difficulty;
    private Integer status;
    private List<QuestionTagVO> tags;
    private List<Long> tagIds;
    private List<String> tagNames;
    private Boolean favorite;
    private String masteryStatus;
}
