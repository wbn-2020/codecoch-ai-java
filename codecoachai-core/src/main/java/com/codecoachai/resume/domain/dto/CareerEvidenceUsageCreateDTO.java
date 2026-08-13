package com.codecoachai.resume.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerEvidenceUsageCreateDTO {

    @NotBlank(message = "资产类型不能为空")
    private String assetType;
    @NotNull(message = "资产 ID 不能为空")
    private Long assetId;
    @NotBlank(message = "资产版本不能为空")
    private String assetVersion;
    private Long packageSnapshotId;
    @NotBlank(message = "使用场景不能为空")
    private String usageScene;
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime usedAt;
    private Long hypothesisId;
    private Long variantId;
    private Long assignmentId;
    @NotBlank(message = "幂等键不能为空")
    private String idempotencyKey;
}
