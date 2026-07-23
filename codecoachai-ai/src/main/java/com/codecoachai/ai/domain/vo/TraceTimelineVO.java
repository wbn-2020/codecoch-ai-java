package com.codecoachai.ai.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TraceTimelineVO {

    private List<TraceNodeVO> nodes = new ArrayList<>();
    private List<TraceNodeVO> unplacedNodes = new ArrayList<>();
}
