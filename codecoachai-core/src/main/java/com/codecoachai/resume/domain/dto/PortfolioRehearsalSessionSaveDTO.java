package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class PortfolioRehearsalSessionSaveDTO {

    @NotBlank(message = "排练路线不能为空")
    private String activeRouteKey;

    @NotNull(message = "当前节点序号不能为空")
    @Min(value = 0, message = "当前节点序号不能为负")
    private Integer activeNodeIndex;

    @NotNull(message = "已计时秒数不能为空")
    @Min(value = 0, message = "已计时秒数不能为负")
    private Integer elapsedSeconds;

    private List<String> completedNodeIds = new ArrayList<>();
}
