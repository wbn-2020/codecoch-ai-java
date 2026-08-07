package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.question.domain.dto.QuestionMetadataQueryDTO;
import com.codecoachai.question.domain.dto.SaveQuestionCategoryDTO;
import com.codecoachai.question.domain.dto.SaveQuestionGroupDTO;
import com.codecoachai.question.domain.dto.SaveQuestionTagDTO;
import com.codecoachai.question.domain.vo.QuestionCategoryVO;
import com.codecoachai.question.domain.vo.QuestionGroupVO;
import com.codecoachai.question.domain.vo.QuestionTagVO;
import com.codecoachai.question.service.QuestionMetadataService;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.web.log.OperationLog;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminQuestionMetadataController {

    private static final String PERM_QUESTION_CATEGORY = "admin:question:category";
    private static final String PERM_QUESTION_TAG = "admin:question:tag";
    private static final String PERM_QUESTION_GROUP = "admin:question:group";

    private final QuestionMetadataService metadataService;
    private final AdminPermissionGuard adminPermissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @GetMapping("/admin/question-categories")
    public Result<List<QuestionCategoryVO>> listCategories() {
        adminPermissionGuard.require(PERM_QUESTION_CATEGORY);
        return Result.success(metadataService.listCategories());
    }

    @GetMapping({"/admin/question-categories/list", "/admin/question-categories/tree"})
    public Result<List<QuestionCategoryVO>> listOrTreeCategories() {
        adminPermissionGuard.require(PERM_QUESTION_CATEGORY);
        return Result.success(metadataService.treeCategories());
    }

    @PostMapping("/admin/question-categories")
    @OperationLog(module = "question-metadata", action = "CREATE_CATEGORY", description = "Create question category", logArgs = false, logResponse = false)
    public Result<QuestionCategoryVO> createCategory(@Valid @RequestBody SaveQuestionCategoryDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_CATEGORY);
        return runConfirmedOperation("question-category-create:" + dto.getCategoryName(),
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> Result.success(metadataService.createCategory(dto)));
    }

    @PutMapping("/admin/question-categories/{id}")
    @OperationLog(module = "question-metadata", action = "UPDATE_CATEGORY", description = "Update question category", logArgs = false, logResponse = false)
    public Result<QuestionCategoryVO> updateCategory(@PathVariable Long id,
                                                     @Valid @RequestBody SaveQuestionCategoryDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_CATEGORY);
        return runConfirmedOperation("question-category-update:" + id,
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> Result.success(metadataService.updateCategory(id, dto)));
    }

    @DeleteMapping("/admin/question-categories/{id}")
    @OperationLog(module = "question-metadata", action = "DELETE_CATEGORY", description = "Delete question category", logArgs = false, logResponse = false)
    public Result<Void> deleteCategory(@PathVariable Long id,
                                       @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_CATEGORY);
        return runConfirmedOperation("question-category-delete:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    metadataService.deleteCategory(id);
                    return Result.success();
                });
    }

    @PutMapping("/admin/question-categories/{id}/status")
    @OperationLog(module = "question-metadata", action = "UPDATE_CATEGORY_STATUS", description = "Update question category status", logArgs = false, logResponse = false)
    public Result<Void> updateCategoryStatus(@PathVariable Long id, @Valid @RequestBody com.codecoachai.question.domain.dto.UpdateStatusDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_CATEGORY);
        return runConfirmedOperation("question-category-status:" + id,
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> {
                    metadataService.updateCategoryStatus(id, dto.getStatus());
                    return Result.success();
                });
    }

    @GetMapping("/admin/question-tags")
    public Result<List<QuestionTagVO>> listTags() {
        adminPermissionGuard.require(PERM_QUESTION_TAG);
        return Result.success(metadataService.listTags());
    }

    @GetMapping({"/admin/question-tags/list"})
    public Result<List<QuestionTagVO>> listTagsAlias() {
        adminPermissionGuard.require(PERM_QUESTION_TAG);
        return Result.success(metadataService.listTags());
    }

    @GetMapping({"/admin/question-tags/page"})
    public Result<PageResult<QuestionTagVO>> pageTags(QuestionMetadataQueryDTO query) {
        adminPermissionGuard.require(PERM_QUESTION_TAG);
        return Result.success(metadataService.pageTags(query));
    }

    @PostMapping("/admin/question-tags")
    @OperationLog(module = "question-metadata", action = "CREATE_TAG", description = "Create question tag", logArgs = false, logResponse = false)
    public Result<QuestionTagVO> createTag(@Valid @RequestBody SaveQuestionTagDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_TAG);
        return runConfirmedOperation("question-tag-create:" + dto.getTagName(),
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> Result.success(metadataService.createTag(dto)));
    }

    @PutMapping("/admin/question-tags/{id}")
    @OperationLog(module = "question-metadata", action = "UPDATE_TAG", description = "Update question tag", logArgs = false, logResponse = false)
    public Result<QuestionTagVO> updateTag(@PathVariable Long id, @Valid @RequestBody SaveQuestionTagDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_TAG);
        return runConfirmedOperation("question-tag-update:" + id,
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> Result.success(metadataService.updateTag(id, dto)));
    }

    @DeleteMapping("/admin/question-tags/{id}")
    @OperationLog(module = "question-metadata", action = "DELETE_TAG", description = "Delete question tag", logArgs = false, logResponse = false)
    public Result<Void> deleteTag(@PathVariable Long id,
                                  @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_TAG);
        return runConfirmedOperation("question-tag-delete:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    metadataService.deleteTag(id);
                    return Result.success();
                });
    }

    @PutMapping("/admin/question-tags/{id}/status")
    @OperationLog(module = "question-metadata", action = "UPDATE_TAG_STATUS", description = "Update question tag status", logArgs = false, logResponse = false)
    public Result<Void> updateTagStatus(@PathVariable Long id, @Valid @RequestBody com.codecoachai.question.domain.dto.UpdateStatusDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_TAG);
        return runConfirmedOperation("question-tag-status:" + id,
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> {
                    metadataService.updateTagStatus(id, dto.getStatus());
                    return Result.success();
                });
    }

    @GetMapping("/admin/question-groups")
    public Result<List<QuestionGroupVO>> listGroups() {
        adminPermissionGuard.require(PERM_QUESTION_GROUP);
        return Result.success(metadataService.listGroups());
    }

    @GetMapping("/admin/question-groups/list")
    public Result<List<QuestionGroupVO>> listGroupsAlias() {
        adminPermissionGuard.require(PERM_QUESTION_GROUP);
        return Result.success(metadataService.listGroups());
    }

    @GetMapping("/admin/question-groups/page")
    public Result<PageResult<QuestionGroupVO>> pageGroups(QuestionMetadataQueryDTO query) {
        adminPermissionGuard.require(PERM_QUESTION_GROUP);
        return Result.success(metadataService.pageGroups(query));
    }

    @GetMapping("/admin/question-groups/{id}")
    public Result<QuestionGroupVO> getGroup(@PathVariable Long id) {
        adminPermissionGuard.require(PERM_QUESTION_GROUP);
        return Result.success(metadataService.getGroup(id));
    }

    @PostMapping("/admin/question-groups")
    @OperationLog(module = "question-metadata", action = "CREATE_GROUP", description = "Create question group", logArgs = false, logResponse = false)
    public Result<QuestionGroupVO> createGroup(@Valid @RequestBody SaveQuestionGroupDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_GROUP);
        return runConfirmedOperation("question-group-create:" + dto.getGroupName(),
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> Result.success(metadataService.createGroup(dto)));
    }

    @PutMapping("/admin/question-groups/{id}")
    @OperationLog(module = "question-metadata", action = "UPDATE_GROUP", description = "Update question group", logArgs = false, logResponse = false)
    public Result<QuestionGroupVO> updateGroup(@PathVariable Long id, @Valid @RequestBody SaveQuestionGroupDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_GROUP);
        return runConfirmedOperation("question-group-update:" + id,
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> Result.success(metadataService.updateGroup(id, dto)));
    }

    @DeleteMapping("/admin/question-groups/{id}")
    @OperationLog(module = "question-metadata", action = "DELETE_GROUP", description = "Delete question group", logArgs = false, logResponse = false)
    public Result<Void> deleteGroup(@PathVariable Long id,
                                    @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_GROUP);
        return runConfirmedOperation("question-group-delete:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    metadataService.deleteGroup(id);
                    return Result.success();
                });
    }

    @PutMapping("/admin/question-groups/{id}/status")
    @OperationLog(module = "question-metadata", action = "UPDATE_GROUP_STATUS", description = "Update question group status", logArgs = false, logResponse = false)
    public Result<Void> updateGroupStatus(@PathVariable Long id, @Valid @RequestBody com.codecoachai.question.domain.dto.UpdateStatusDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_GROUP);
        return runConfirmedOperation("question-group-status:" + id,
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> {
                    metadataService.updateGroupStatus(id, dto.getStatus());
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
