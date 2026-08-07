package com.codecoachai.interview.feign.vo;

import java.util.List;
import lombok.Data;

@Data
public class InnerResumeDetailVO {

    private Long id;
    private Long userId;
    private String title;
    private String realName;
    private String summary;
    private List<InnerResumeProjectVO> projects;
}
