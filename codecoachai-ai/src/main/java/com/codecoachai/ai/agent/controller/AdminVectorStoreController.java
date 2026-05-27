package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.vector.domain.VectorCollectionInfo;
import com.codecoachai.common.vector.service.VectorStoreClient;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/vector-store")
public class AdminVectorStoreController {

    private static final List<String> CORE_COLLECTIONS = List.of(
            "question_embedding",
            "personal_knowledge_chunk"
    );

    private final VectorStoreClient vectorStoreClient;
    private final V4AdminPermissionGuard permissionGuard;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        permissionGuard.require("admin:analytics:agent");
        List<VectorCollectionInfo> collections = CORE_COLLECTIONS.stream()
                .map(vectorStoreClient::collectionInfo)
                .toList();
        return Result.success(Map.of(
                "enabled", vectorStoreClient.isEnabled(),
                "collections", collections
        ));
    }
}
