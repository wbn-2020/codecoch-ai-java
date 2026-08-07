package com.codecoachai.question.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class QuestionListVO {

    private Long id;
    private String title;
    private Long categoryId;
    private String categoryName;
    private Long groupId;
    private String difficulty;
    private String questionType;
    private String experienceLevel;
    private Integer isHighFrequency;
    private Integer status;
    private List<QuestionTagVO> tags;
    private List<Long> tagIds;
    private List<String> tagNames;
    private Boolean favorite;
    private String masteryStatus;
    private LocalDateTime createdAt;
}
