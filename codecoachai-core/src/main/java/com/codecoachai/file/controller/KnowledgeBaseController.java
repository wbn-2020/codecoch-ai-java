package com.codecoachai.file.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy V3 knowledge endpoints are intentionally sealed.
 *
 * <p>The active personal knowledge base lives in codecoachai-ai under
 * /agent/knowledge and stores business rows in personal_knowledge_* with
 * Qdrant-backed vector indexes. Keeping this controller as a hard fail avoids
 * new writes into the old knowledge_document / knowledge_chunk tables while
 * preserving a clear response for stale clients.</p>
 */
@Deprecated(since = "4.0", forRemoval = false)
@RestController
@RequestMapping("/knowledge")
public class KnowledgeBaseController {

    private static final String MESSAGE = "Legacy /knowledge APIs are disabled. Use /agent/knowledge APIs instead.";

    @RequestMapping({"", "/**"})
    public Result<Map<String, Object>> legacyKnowledgeDisabled() {
        SecurityAssert.requireLoginUserId();
        throw new BusinessException(ErrorCode.PARAM_ERROR, MESSAGE);
    }
}
