package com.codecoachai.ai.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PromptTemplateDetailVO extends PromptTemplateVO {

    private PromptTemplateVersionVO activeVersion;
}
