package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.domain.dto.QuestionMetadataQueryDTO;
import com.codecoachai.question.domain.dto.SaveQuestionCategoryDTO;
import com.codecoachai.question.domain.dto.SaveQuestionGroupDTO;
import com.codecoachai.question.domain.dto.SaveQuestionTagDTO;
import com.codecoachai.question.domain.vo.QuestionCategoryVO;
import com.codecoachai.question.domain.vo.QuestionGroupVO;
import com.codecoachai.question.domain.vo.QuestionTagVO;
import com.codecoachai.question.service.QuestionMetadataService;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import jakarta.validation.Valid;
import java.util.List;
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
public class AdminQuestionMetadataController {

    private static final String PERM_QUESTION_CATEGORY = "admin:question:category";
    private static final String PERM_QUESTION_TAG = "admin:question:tag";
    private static final String PERM_QUESTION_GROUP = "admin:question:group";

    private final QuestionMetadataService metadataService;
    private final AdminPermissionGuard adminPermissionGuard;

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
    public Result<QuestionCategoryVO> createCategory(@Valid @RequestBody SaveQuestionCategoryDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_CATEGORY);
        return Result.success(metadataService.createCategory(dto));
    }

    @PutMapping("/admin/question-categories/{id}")
    public Result<QuestionCategoryVO> updateCategory(@PathVariable Long id,
                                                     @Valid @RequestBody SaveQuestionCategoryDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_CATEGORY);
        return Result.success(metadataService.updateCategory(id, dto));
    }

    @DeleteMapping("/admin/question-categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        adminPermissionGuard.require(PERM_QUESTION_CATEGORY);
        metadataService.deleteCategory(id);
        return Result.success();
    }

    @PutMapping("/admin/question-categories/{id}/status")
    public Result<Void> updateCategoryStatus(@PathVariable Long id, @Valid @RequestBody com.codecoachai.question.domain.dto.UpdateStatusDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_CATEGORY);
        metadataService.updateCategoryStatus(id, dto.getStatus());
        return Result.success();
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
    public Result<QuestionTagVO> createTag(@Valid @RequestBody SaveQuestionTagDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_TAG);
        return Result.success(metadataService.createTag(dto));
    }

    @PutMapping("/admin/question-tags/{id}")
    public Result<QuestionTagVO> updateTag(@PathVariable Long id, @Valid @RequestBody SaveQuestionTagDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_TAG);
        return Result.success(metadataService.updateTag(id, dto));
    }

    @DeleteMapping("/admin/question-tags/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        adminPermissionGuard.require(PERM_QUESTION_TAG);
        metadataService.deleteTag(id);
        return Result.success();
    }

    @PutMapping("/admin/question-tags/{id}/status")
    public Result<Void> updateTagStatus(@PathVariable Long id, @Valid @RequestBody com.codecoachai.question.domain.dto.UpdateStatusDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_TAG);
        metadataService.updateTagStatus(id, dto.getStatus());
        return Result.success();
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
    public Result<QuestionGroupVO> createGroup(@Valid @RequestBody SaveQuestionGroupDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_GROUP);
        return Result.success(metadataService.createGroup(dto));
    }

    @PutMapping("/admin/question-groups/{id}")
    public Result<QuestionGroupVO> updateGroup(@PathVariable Long id, @Valid @RequestBody SaveQuestionGroupDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_GROUP);
        return Result.success(metadataService.updateGroup(id, dto));
    }

    @DeleteMapping("/admin/question-groups/{id}")
    public Result<Void> deleteGroup(@PathVariable Long id) {
        adminPermissionGuard.require(PERM_QUESTION_GROUP);
        metadataService.deleteGroup(id);
        return Result.success();
    }

    @PutMapping("/admin/question-groups/{id}/status")
    public Result<Void> updateGroupStatus(@PathVariable Long id, @Valid @RequestBody com.codecoachai.question.domain.dto.UpdateStatusDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_GROUP);
        metadataService.updateGroupStatus(id, dto.getStatus());
        return Result.success();
    }
}
