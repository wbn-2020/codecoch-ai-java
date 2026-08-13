package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TargetJobSaveDTO {

    @NotBlank(message = "请填写目标岗位")
    @Size(max = 128, message = "目标岗位不能超过 128 字")
    private String jobTitle;

    @Size(max = 128, message = "公司名称不能超过 128 字")
    private String companyName;

    @Size(max = 64, message = "岗位级别不能超过 64 字")
    private String jobLevel;

    @Size(max = 20000, message = "岗位描述内容不能超过 20000 字")
    private String jdText;

    @Size(max = 64, message = "岗位描述来源不能超过 64 字")
    private String jdSource;
}
