package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeListVO {

    private Long id;
    private String title;
    private String realName;
    private String targetPosition;
    private String skillStack;
    private String summary;
    private Long projectCount;
    private Integer isDefault;
    private Integer status;
    private LocalDateTime updatedAt;
}
