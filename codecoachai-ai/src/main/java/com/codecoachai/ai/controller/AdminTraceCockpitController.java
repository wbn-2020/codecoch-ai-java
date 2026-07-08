package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.TraceCockpitQueryDTO;
import com.codecoachai.ai.domain.vo.TraceCockpitResultVO;
import com.codecoachai.ai.service.TraceCockpitService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.web.log.OperationLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/trace-cockpit")
public class AdminTraceCockpitController {

    private static final String PERM_TRACE_VIEW = "admin:trace:cockpit:view";
    private static final String PERM_RAW_VIEW = "admin:ai:log:raw:view";

    private final TraceCockpitService traceCockpitService;
    private final AdminPermissionGuard permissionGuard;

    @GetMapping
    @OperationLog(module = "trace", action = "QUERY_TRACE_COCKPIT",
            description = "Query Trace Cockpit backend aggregation",
            logArgs = true, logResponse = false)
    public Result<TraceCockpitResultVO> getTraceCockpit(@Valid @ModelAttribute TraceCockpitQueryDTO query) {
        permissionGuard.require(PERM_TRACE_VIEW);
        if (Boolean.TRUE.equals(query.getIncludeSensitive())) {
            permissionGuard.require(PERM_RAW_VIEW);
        }
        return Result.success(traceCockpitService.getTraceCockpit(query));
    }
}
