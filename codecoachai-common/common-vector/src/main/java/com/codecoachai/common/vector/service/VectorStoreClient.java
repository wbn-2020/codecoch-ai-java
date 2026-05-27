package com.codecoachai.common.vector.service;

import com.codecoachai.common.vector.domain.VectorPoint;
import com.codecoachai.common.vector.domain.VectorSearchRequest;
import com.codecoachai.common.vector.domain.VectorSearchResult;
import java.util.List;

public interface VectorStoreClient {

    boolean isEnabled();

    void ensureCollection(String collectionName, int dimension);

    void upsert(String collectionName, List<VectorPoint> points);

    List<VectorSearchResult> search(VectorSearchRequest request);

    void delete(String collectionName, List<String> pointIds);
}
