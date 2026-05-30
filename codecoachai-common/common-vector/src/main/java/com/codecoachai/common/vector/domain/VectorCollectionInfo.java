package com.codecoachai.common.vector.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VectorCollectionInfo {

    private String collectionName;

    private Boolean exists;

    private String status;

    private Long pointCount;

    private Integer vectorSize;

    private String distance;

    private String errorMessage;
}
