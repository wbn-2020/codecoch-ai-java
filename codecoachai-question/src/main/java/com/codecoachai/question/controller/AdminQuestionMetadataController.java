package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.domain.dto.SaveQuestionCategoryDTO;
import com.codecoachai.question.domain.dto.SaveQuestionGroupDTO;
import com.codecoachai.question.domain.dto.SaveQuestionTagDTO;
import com.codecoachai.question.domain.vo.QuestionCategoryVO;
import com.codecoachai.question.domain.vo.QuestionGroupVO;
import com.codecoachai.question.domain.vo.QuestionTagVO;
import com.codecoachai.question.service.QuestionMetadataService;
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

    private final QuestionMetadataService metadataService;

    @GetMapping("/admin/question-categories")
    public Result<List<QuestionCategoryVO>> listCategories() {
        return Result.success(metadataService.listCategories());
    }

    @PostMapping("/admin/question-categories")
    public Result<QuestionCategoryVO> createCategory(@Valid @RequestBody SaveQuestionCategoryDTO dto) {
        return Result.success(metadataService.createCategory(dto));
    }

    @PutMapping("/admin/question-categories/{id}")
    public Result<QuestionCategoryVO> updateCategory(@PathVariable Long id,
                                                     @Valid @RequestBody SaveQuestionCategoryDTO dto) {
        return Result.success(metadataService.updateCategory(id, dto));
    }

    @DeleteMapping("/admin/question-categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        metadataService.deleteCategory(id);
        return Result.success();
    }

    @GetMapping("/admin/question-tags")
    public Result<List<QuestionTagVO>> listTags() {
        return Result.success(metadataService.listTags());
    }

    @PostMapping("/admin/question-tags")
    public Result<QuestionTagVO> createTag(@Valid @RequestBody SaveQuestionTagDTO dto) {
        return Result.success(metadataService.createTag(dto));
    }

    @PutMapping("/admin/question-tags/{id}")
    public Result<QuestionTagVO> updateTag(@PathVariable Long id, @Valid @RequestBody SaveQuestionTagDTO dto) {
        return Result.success(metadataService.updateTag(id, dto));
    }

    @DeleteMapping("/admin/question-tags/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        metadataService.deleteTag(id);
        return Result.success();
    }

    @GetMapping("/admin/question-groups")
    public Result<List<QuestionGroupVO>> listGroups() {
        return Result.success(metadataService.listGroups());
    }

    @PostMapping("/admin/question-groups")
    public Result<QuestionGroupVO> createGroup(@Valid @RequestBody SaveQuestionGroupDTO dto) {
        return Result.success(metadataService.createGroup(dto));
    }

    @PutMapping("/admin/question-groups/{id}")
    public Result<QuestionGroupVO> updateGroup(@PathVariable Long id, @Valid @RequestBody SaveQuestionGroupDTO dto) {
        return Result.success(metadataService.updateGroup(id, dto));
    }

    @DeleteMapping("/admin/question-groups/{id}")
    public Result<Void> deleteGroup(@PathVariable Long id) {
        metadataService.deleteGroup(id);
        return Result.success();
    }
}
