package com.codecoachai.common.vector.service;

import com.codecoachai.common.vector.domain.VectorPoint;
import com.codecoachai.common.vector.domain.VectorSearchRequest;
import com.codecoachai.common.vector.domain.VectorSearchResult;
import java.util.List;

public class NoopVectorStoreClient implements VectorStoreClient {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void ensureCollection(String collectionName, int dimension) {
        // Vector store is disabled in this runtime.
    }

    @Override
    public void upsert(String collectionName, List<VectorPoint> points) {
        // Vector store is disabled in this runtime.
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        return List.of();
    }

    @Override
    public void delete(String collectionName, List<String> pointIds) {
        // Vector store is disabled in this runtime.
    }
}
