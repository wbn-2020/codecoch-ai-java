package com.codecoachai.resume.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InnerResumeDetailVO {

    private Long id;
    private Long userId;
    private String title;
    private String realName;
    private String summary;
    private List<ResumeProjectVO> projects;
}
