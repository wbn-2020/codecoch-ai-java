package com.codecoachai.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.question.convert.QuestionConvert;
import com.codecoachai.question.domain.dto.SaveQuestionCategoryDTO;
import com.codecoachai.question.domain.dto.SaveQuestionGroupDTO;
import com.codecoachai.question.domain.dto.SaveQuestionTagDTO;
import com.codecoachai.question.domain.entity.QuestionCategory;
import com.codecoachai.question.domain.entity.QuestionGroup;
import com.codecoachai.question.domain.entity.QuestionTag;
import com.codecoachai.question.domain.vo.QuestionCategoryVO;
import com.codecoachai.question.domain.vo.QuestionGroupVO;
import com.codecoachai.question.domain.vo.QuestionTagVO;
import com.codecoachai.question.mapper.QuestionCategoryMapper;
import com.codecoachai.question.mapper.QuestionGroupMapper;
import com.codecoachai.question.mapper.QuestionTagMapper;
import com.codecoachai.question.service.QuestionMetadataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionMetadataServiceImpl implements QuestionMetadataService {

    private final QuestionCategoryMapper categoryMapper;
    private final QuestionTagMapper tagMapper;
    private final QuestionGroupMapper groupMapper;

    @Override
    public List<QuestionCategoryVO> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<QuestionCategory>()
                        .orderByAsc(QuestionCategory::getSort)
                        .orderByDesc(QuestionCategory::getUpdatedAt))
                .stream()
                .map(QuestionConvert::toCategoryVO)
                .toList();
    }

    @Override
    public QuestionCategoryVO createCategory(SaveQuestionCategoryDTO dto) {
        QuestionCategory category = new QuestionCategory();
        category.setCategoryName(dto.getCategoryName());
        category.setSort(dto.getSort() == null ? 0 : dto.getSort());
        category.setStatus(dto.getStatus() == null ? CommonConstants.YES : dto.getStatus());
        categoryMapper.insert(category);
        return QuestionConvert.toCategoryVO(category);
    }

    @Override
    public QuestionCategoryVO updateCategory(Long id, SaveQuestionCategoryDTO dto) {
        QuestionCategory category = getCategory(id);
        category.setCategoryName(dto.getCategoryName());
        category.setSort(dto.getSort() == null ? category.getSort() : dto.getSort());
        category.setStatus(dto.getStatus() == null ? category.getStatus() : dto.getStatus());
        categoryMapper.updateById(category);
        return QuestionConvert.toCategoryVO(category);
    }

    @Override
    public void deleteCategory(Long id) {
        categoryMapper.deleteById(id);
    }

    @Override
    public List<QuestionTagVO> listTags() {
        return tagMapper.selectList(new LambdaQueryWrapper<QuestionTag>().orderByDesc(QuestionTag::getUpdatedAt))
                .stream()
                .map(QuestionConvert::toTagVO)
                .toList();
    }

    @Override
    public QuestionTagVO createTag(SaveQuestionTagDTO dto) {
        QuestionTag tag = new QuestionTag();
        tag.setTagName(dto.getTagName());
        tag.setStatus(dto.getStatus() == null ? CommonConstants.YES : dto.getStatus());
        tagMapper.insert(tag);
        return QuestionConvert.toTagVO(tag);
    }

    @Override
    public QuestionTagVO updateTag(Long id, SaveQuestionTagDTO dto) {
        QuestionTag tag = getTag(id);
        tag.setTagName(dto.getTagName());
        tag.setStatus(dto.getStatus() == null ? tag.getStatus() : dto.getStatus());
        tagMapper.updateById(tag);
        return QuestionConvert.toTagVO(tag);
    }

    @Override
    public void deleteTag(Long id) {
        tagMapper.deleteById(id);
    }

    @Override
    public List<QuestionGroupVO> listGroups() {
        return groupMapper.selectList(new LambdaQueryWrapper<QuestionGroup>().orderByDesc(QuestionGroup::getUpdatedAt))
                .stream()
                .map(QuestionConvert::toGroupVO)
                .toList();
    }

    @Override
    public QuestionGroupVO createGroup(SaveQuestionGroupDTO dto) {
        QuestionGroup group = new QuestionGroup();
        group.setGroupName(dto.getGroupName());
        group.setDescription(dto.getDescription());
        group.setCategoryId(dto.getCategoryId());
        group.setStatus(dto.getStatus() == null ? CommonConstants.YES : dto.getStatus());
        groupMapper.insert(group);
        return QuestionConvert.toGroupVO(group);
    }

    @Override
    public QuestionGroupVO updateGroup(Long id, SaveQuestionGroupDTO dto) {
        QuestionGroup group = getGroup(id);
        group.setGroupName(dto.getGroupName());
        group.setDescription(dto.getDescription());
        group.setCategoryId(dto.getCategoryId());
        group.setStatus(dto.getStatus() == null ? group.getStatus() : dto.getStatus());
        groupMapper.updateById(group);
        return QuestionConvert.toGroupVO(group);
    }

    @Override
    public void deleteGroup(Long id) {
        groupMapper.deleteById(id);
    }

    private QuestionCategory getCategory(Long id) {
        QuestionCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question category not found");
        }
        return category;
    }

    private QuestionTag getTag(Long id) {
        QuestionTag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question tag not found");
        }
        return tag;
    }

    private QuestionGroup getGroup(Long id) {
        QuestionGroup group = groupMapper.selectById(id);
        if (group == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question group not found");
        }
        return group;
    }
}
