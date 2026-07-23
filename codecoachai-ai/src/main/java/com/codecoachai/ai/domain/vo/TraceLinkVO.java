package com.codecoachai.ai.domain.vo;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

@Data
public class TraceLinkVO {

    private String label;
    private Map<String, Object> to = new LinkedHashMap<>();
}
