package com.codecoachai.task.feign.vo;

import java.util.List;
import lombok.Data;

@Data
public class SaveQuestionDraftsVO {

    private String batchId;
    private Integer savedCount;
    private List<Long> reviewIds;
}
