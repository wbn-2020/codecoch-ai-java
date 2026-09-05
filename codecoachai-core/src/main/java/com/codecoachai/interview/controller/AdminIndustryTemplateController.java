package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.interview.domain.dto.IndustryTemplateCreateDTO;
import com.codecoachai.interview.domain.dto.IndustryTemplateQueryDTO;
import com.codecoachai.interview.domain.dto.IndustryTemplateUpdateDTO;
import com.codecoachai.interview.domain.vo.IndustryTemplateVO;
import com.codecoachai.interview.service.IndustryTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.function.Supplier;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminIndustryTemplateController {

    private static final String PERM_INDUSTRY_TEMPLATE_LIST = "admin:industry-template:list";
    private static final String PERM_INDUSTRY_TEMPLATE_WRITE = "admin:industry-template:write";

    private final IndustryTemplateService industryTemplateService;
    private final AdminPermissionGuard adminPermissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @GetMapping("/admin/industry-templates")
    public Result<List<IndustryTemplateVO>> list(IndustryTemplateQueryDTO query) {
        adminPermissionGuard.require(PERM_INDUSTRY_TEMPLATE_LIST);
        return Result.success(industryTemplateService.adminList(query));
    }

    @GetMapping("/admin/industry-templates/{id}")
    public Result<IndustryTemplateVO> detail(@PathVariable Long id) {
        adminPermissionGuard.require(PERM_INDUSTRY_TEMPLATE_LIST);
        return Result.success(industryTemplateService.adminDetail(id));
    }

    @PostMapping("/admin/industry-templates")
    @OperationLog(module = "interview", action = "CREATE_INDUSTRY_TEMPLATE", description = "创建行业模板", logArgs = false)
    public Result<IndustryTemplateVO> create(@Valid @RequestBody IndustryTemplateCreateDTO dto) {
        adminPermissionGuard.require(PERM_INDUSTRY_TEMPLATE_WRITE);
        return runConfirmedOperation("industry-template-create:" + (dto == null ? "new" : dto.getIndustryCode()),
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> Result.success(industryTemplateService.create(dto)));
    }

    @PutMapping("/admin/industry-templates/{id}")
    @OperationLog(module = "interview", action = "UPDATE_INDUSTRY_TEMPLATE", description = "编辑行业模板", logArgs = false)
    public Result<IndustryTemplateVO> update(@PathVariable Long id,
                                             @Valid @RequestBody IndustryTemplateUpdateDTO dto) {
        adminPermissionGuard.require(PERM_INDUSTRY_TEMPLATE_WRITE);
        return runConfirmedOperation("industry-template-update:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> Result.success(industryTemplateService.update(id, dto)));
    }

    @PostMapping("/admin/industry-templates/{id}/enable")
    @OperationLog(module = "interview", action = "ENABLE_INDUSTRY_TEMPLATE", description = "启用行业模板")
    public Result<Void> enable(@PathVariable Long id,
                               @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        adminPermissionGuard.require(PERM_INDUSTRY_TEMPLATE_WRITE);
        return runConfirmedOperation("industry-template-enable:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    industryTemplateService.enable(id);
                    return Result.success();
                });
    }

    @PostMapping("/admin/industry-templates/{id}/disable")
    @OperationLog(module = "interview", action = "DISABLE_INDUSTRY_TEMPLATE", description = "停用行业模板")
    public Result<Void> disable(@PathVariable Long id,
                                @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        adminPermissionGuard.require(PERM_INDUSTRY_TEMPLATE_WRITE);
        return runConfirmedOperation("industry-template-disable:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    industryTemplateService.disable(id);
                    return Result.success();
                });
    }

    @DeleteMapping("/admin/industry-templates/{id}")
    @OperationLog(module = "interview", action = "DELETE_INDUSTRY_TEMPLATE", description = "删除行业模板")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        adminPermissionGuard.require(PERM_INDUSTRY_TEMPLATE_WRITE);
        return runConfirmedOperation("industry-template-delete:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    industryTemplateService.delete(id);
                    return Result.success();
                });
    }

    private <T> Result<T> runConfirmedOperation(String operation, Boolean confirm, Boolean dryRun,
                                                String reason, String idempotencyKey,
                                                Supplier<Result<T>> action) {
        String lockKey = operationConfirmationGuard.requireConfirmed(operation, confirm, dryRun, reason, idempotencyKey);
        try {
            return action.get();
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }
    @Data
    public static class AdminOperationConfirmDTO {
        private Boolean confirm;
        private Boolean dryRun;
        private String reason;
        private String idempotencyKey;
    }
}
