package com.codecoachai.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.system.domain.entity.SysAnnouncement;
import com.codecoachai.system.mapper.SysAnnouncementMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统公告管理 Controller。
 * 管理端：CRUD + 发布/下线
 * 用户端：查询已发布公告
 */
@Tag(name = "系统公告")
@RestController
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private static final String PERM_ANNOUNCEMENT_LIST = "admin:announcement:list";
    private static final String PERM_ANNOUNCEMENT_WRITE = "admin:announcement:write";
    private static final String PERM_ANNOUNCEMENT_PUBLISH = "admin:announcement:publish";

    private final SysAnnouncementMapper announcementMapper;
    private final AdminPermissionGuard adminPermissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    // ==================== 管理端 ====================

    @Operation(summary = "分页查询公告（管理端）")
    @GetMapping("/admin/announcements")
    public Result<PageResult<SysAnnouncement>> page(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        adminPermissionGuard.require(PERM_ANNOUNCEMENT_LIST);
        Page<SysAnnouncement> page = announcementMapper.selectPage(
                Page.of(defaultPage(pageNo), defaultSize(pageSize)),
                new LambdaQueryWrapper<SysAnnouncement>()
                        .eq(status != null, SysAnnouncement::getStatus, status)
                        .like(StringUtils.hasText(keyword), SysAnnouncement::getTitle, keyword)
                        .orderByDesc(SysAnnouncement::getCreatedAt));
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    private long defaultPage(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long defaultSize(Long pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 100L);
    }

    @Operation(summary = "公告详情（管理端）")
    @GetMapping("/admin/announcements/{id}")
    public Result<SysAnnouncement> detail(@PathVariable Long id) {
        adminPermissionGuard.require(PERM_ANNOUNCEMENT_LIST);
        SysAnnouncement a = announcementMapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "公告不存在");
        return Result.success(a);
    }

    @Operation(summary = "新增公告")
    @OperationLog(module = "system", action = "CREATE_ANNOUNCEMENT", description = "新增公告", logArgs = false)
    @PostMapping("/admin/announcements")
    public Result<Long> create(@Valid @RequestBody AnnouncementSaveDTO dto) {
        adminPermissionGuard.require(PERM_ANNOUNCEMENT_WRITE);
        return runConfirmedOperation("announcement-create:" + (dto == null ? "new" : dto.getTitle()),
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
        SysAnnouncement a = new SysAnnouncement();
        a.setTitle(dto.getTitle());
        a.setContent(dto.getContent());
        a.setType(dto.getType() != null ? dto.getType() : "NORMAL");
        a.setStatus(0); // 草稿
        a.setTargetUsers(dto.getTargetUsers());
        a.setCreatedBy(SecurityAssert.requireLoginUserId());
        a.setExpiredAt(dto.getExpiredAt());
        a.setDeleted(0);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        announcementMapper.insert(a);
                    return Result.success(a.getId());
                });
    }

    @Operation(summary = "编辑公告")
    @OperationLog(module = "system", action = "UPDATE_ANNOUNCEMENT", description = "编辑公告", logArgs = false)
    @PutMapping("/admin/announcements/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AnnouncementSaveDTO dto) {
        adminPermissionGuard.require(PERM_ANNOUNCEMENT_WRITE);
        return runConfirmedOperation("announcement-update:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
        SysAnnouncement a = announcementMapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "公告不存在");
        a.setTitle(dto.getTitle());
        a.setContent(dto.getContent());
        a.setType(dto.getType());
        a.setTargetUsers(dto.getTargetUsers());
        a.setExpiredAt(dto.getExpiredAt());
        a.setUpdatedAt(LocalDateTime.now());
        announcementMapper.updateById(a);
                    return Result.success();
                });
    }

    @Operation(summary = "发布公告")
    @OperationLog(module = "system", action = "PUBLISH_ANNOUNCEMENT", description = "发布公告")
    @PostMapping("/admin/announcements/{id}/publish")
    public Result<Void> publish(@PathVariable Long id,
                                @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        adminPermissionGuard.require(PERM_ANNOUNCEMENT_PUBLISH);
        return runConfirmedOperation("announcement-publish:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
        SysAnnouncement a = announcementMapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "公告不存在");
        a.setStatus(1);
        a.setPublishedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        announcementMapper.updateById(a);
                    return Result.success();
                });
    }

    @Operation(summary = "下线公告")
    @OperationLog(module = "system", action = "OFFLINE_ANNOUNCEMENT", description = "下线公告")
    @PostMapping("/admin/announcements/{id}/offline")
    public Result<Void> offline(@PathVariable Long id,
                                @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        adminPermissionGuard.require(PERM_ANNOUNCEMENT_PUBLISH);
        return runConfirmedOperation("announcement-offline:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
        SysAnnouncement a = announcementMapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "公告不存在");
        a.setStatus(2);
        a.setUpdatedAt(LocalDateTime.now());
        announcementMapper.updateById(a);
                    return Result.success();
                });
    }

    @Operation(summary = "删除公告")
    @OperationLog(module = "system", action = "DELETE_ANNOUNCEMENT", description = "删除公告")
    @DeleteMapping("/admin/announcements/{id}")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        adminPermissionGuard.require(PERM_ANNOUNCEMENT_WRITE);
        return runConfirmedOperation("announcement-delete:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
        announcementMapper.deleteById(id);
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
    // ==================== 用户端 ====================

    @Operation(summary = "查询已发布公告（用户端）")
    @GetMapping("/announcements")
    public Result<List<SysAnnouncement>> listPublished() {
        List<SysAnnouncement> list = announcementMapper.selectList(
                new LambdaQueryWrapper<SysAnnouncement>()
                        .eq(SysAnnouncement::getStatus, 1)
                        .and(w -> w.isNull(SysAnnouncement::getExpiredAt)
                                .or().gt(SysAnnouncement::getExpiredAt, LocalDateTime.now()))
                        .orderByDesc(SysAnnouncement::getPublishedAt)
                        .last("limit 20"));
        return Result.success(list);
    }

    @Data
    public static class AnnouncementSaveDTO {
        @NotBlank(message = "标题不能为空")
        private String title;
        @NotBlank(message = "内容不能为空")
        private String content;
        private String type;
        private String targetUsers;
        private LocalDateTime expiredAt;
        private Boolean confirm;
        private Boolean dryRun;
        private String reason;
        private String idempotencyKey;
    }

    @Data
    public static class AdminOperationConfirmDTO {
        private Boolean confirm;
        private Boolean dryRun;
        private String reason;
        private String idempotencyKey;
    }
}
