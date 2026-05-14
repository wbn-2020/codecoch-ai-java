package com.codecoachai.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.question.convert.QuestionConvert;
import com.codecoachai.question.domain.dto.QuestionMetadataQueryDTO;
import com.codecoachai.question.domain.dto.SaveQuestionCategoryDTO;
import com.codecoachai.question.domain.dto.SaveQuestionGroupDTO;
import com.codecoachai.question.domain.dto.SaveQuestionTagDTO;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionCategory;
import com.codecoachai.question.domain.entity.QuestionGroup;
import com.codecoachai.question.domain.entity.QuestionTag;
import com.codecoachai.question.domain.entity.QuestionTagRelation;
import com.codecoachai.question.domain.vo.QuestionCategoryVO;
import com.codecoachai.question.domain.vo.QuestionGroupVO;
import com.codecoachai.question.domain.vo.QuestionTagVO;
import com.codecoachai.question.mapper.QuestionCategoryMapper;
import com.codecoachai.question.mapper.QuestionGroupMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionTagMapper;
import com.codecoachai.question.mapper.QuestionTagRelationMapper;
import com.codecoachai.question.service.QuestionMetadataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuestionMetadataServiceImpl implements QuestionMetadataService {

    private final QuestionCategoryMapper categoryMapper;
    private final QuestionTagMapper tagMapper;
    private final QuestionGroupMapper groupMapper;
    private final QuestionMapper questionMapper;
    private final QuestionTagRelationMapper tagRelationMapper;

    @Override
    public List<QuestionCategoryVO> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<QuestionCategory>()
                        .orderByAsc(QuestionCategory::getSort)
                        .orderByAsc(QuestionCategory::getSortOrder)
                        .orderByDesc(QuestionCategory::getUpdatedAt))
                .stream()
                .map(QuestionConvert::toCategoryVO)
                .toList();
    }

    @Override
    public List<QuestionCategoryVO> treeCategories() {
        return listCategories();
    }

    @Override
    public QuestionCategoryVO createCategory(SaveQuestionCategoryDTO dto) {
        QuestionCategory category = new QuestionCategory();
        category.setParentId(dto.getParentId());
        category.setCategoryName(dto.getCategoryName());
        category.setSort(dto.getSort() == null ? 0 : dto.getSort());
        category.setSortOrder(dto.getSortOrder() == null ? category.getSort() : dto.getSortOrder());
        category.setStatus(dto.getStatus() == null ? CommonConstants.YES : dto.getStatus());
        categoryMapper.insert(category);
        return QuestionConvert.toCategoryVO(category);
    }

    @Override
    public QuestionCategoryVO updateCategory(Long id, SaveQuestionCategoryDTO dto) {
        QuestionCategory category = getCategory(id);
        category.setParentId(dto.getParentId());
        category.setCategoryName(dto.getCategoryName());
        category.setSort(dto.getSort() == null ? category.getSort() : dto.getSort());
        category.setSortOrder(dto.getSortOrder() == null ? category.getSortOrder() : dto.getSortOrder());
        category.setStatus(dto.getStatus() == null ? category.getStatus() : dto.getStatus());
        categoryMapper.updateById(category);
        return QuestionConvert.toCategoryVO(category);
    }

    @Override
    public void deleteCategory(Long id) {
        Long count = questionMapper.selectCount(new LambdaQueryWrapper<Question>().eq(Question::getCategoryId, id));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question category has related questions");
        }
        categoryMapper.deleteById(id);
    }

    @Override
    public void updateCategoryStatus(Long id, Integer status) {
        QuestionCategory category = getCategory(id);
        category.setStatus(status);
        categoryMapper.updateById(category);
    }

    @Override
    public List<QuestionTagVO> listTags() {
        return tagMapper.selectList(new LambdaQueryWrapper<QuestionTag>().orderByDesc(QuestionTag::getUpdatedAt))
                .stream()
                .map(QuestionConvert::toTagVO)
                .toList();
    }

    @Override
    public PageResult<QuestionTagVO> pageTags(QuestionMetadataQueryDTO query) {
        Page<QuestionTag> page = tagMapper.selectPage(Page.of(defaultPage(query.getPageNo()), defaultSize(query.getPageSize())),
                new LambdaQueryWrapper<QuestionTag>()
                        .like(StringUtils.hasText(query.getKeyword()), QuestionTag::getTagName, query.getKeyword())
                        .eq(query.getStatus() != null, QuestionTag::getStatus, query.getStatus())
                        .orderByDesc(QuestionTag::getUpdatedAt));
        return PageResult.of(page.getRecords().stream().map(QuestionConvert::toTagVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
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
        Long count = tagRelationMapper.selectCount(new LambdaQueryWrapper<QuestionTagRelation>().eq(QuestionTagRelation::getTagId, id));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question tag has related questions");
        }
        tagMapper.deleteById(id);
    }

    @Override
    public void updateTagStatus(Long id, Integer status) {
        QuestionTag tag = getTag(id);
        tag.setStatus(status);
        tagMapper.updateById(tag);
    }

    @Override
    public List<QuestionGroupVO> listGroups() {
        return groupMapper.selectList(new LambdaQueryWrapper<QuestionGroup>().orderByDesc(QuestionGroup::getUpdatedAt))
                .stream()
                .map(this::toGroupVO)
                .toList();
    }

    @Override
    public PageResult<QuestionGroupVO> pageGroups(QuestionMetadataQueryDTO query) {
        Page<QuestionGroup> page = groupMapper.selectPage(Page.of(defaultPage(query.getPageNo()), defaultSize(query.getPageSize())),
                new LambdaQueryWrapper<QuestionGroup>()
                        .and(StringUtils.hasText(query.getKeyword()), condition -> condition
                                .like(QuestionGroup::getGroupName, query.getKeyword())
                                .or()
                                .like(QuestionGroup::getCanonicalTitle, query.getKeyword())
                                .or()
                                .like(QuestionGroup::getMainKnowledgePoint, query.getKeyword()))
                        .eq(query.getCategoryId() != null, QuestionGroup::getCategoryId, query.getCategoryId())
                        .eq(query.getStatus() != null, QuestionGroup::getStatus, query.getStatus())
                        .orderByDesc(QuestionGroup::getUpdatedAt));
        return PageResult.of(page.getRecords().stream().map(this::toGroupVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public QuestionGroupVO getGroup(Long id) {
        return toGroupVO(getGroupEntity(id));
    }

    @Override
    public QuestionGroupVO createGroup(SaveQuestionGroupDTO dto) {
        QuestionGroup group = new QuestionGroup();
        applyGroup(group, dto);
        group.setStatus(dto.getStatus() == null ? CommonConstants.YES : dto.getStatus());
        groupMapper.insert(group);
        return toGroupVO(group);
    }

    @Override
    public QuestionGroupVO updateGroup(Long id, SaveQuestionGroupDTO dto) {
        QuestionGroup group = getGroupEntity(id);
        applyGroup(group, dto);
        group.setStatus(dto.getStatus() == null ? group.getStatus() : dto.getStatus());
        groupMapper.updateById(group);
        return toGroupVO(group);
    }

    @Override
    public void deleteGroup(Long id) {
        Long count = questionMapper.selectCount(new LambdaQueryWrapper<Question>().eq(Question::getGroupId, id));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question group has related questions");
        }
        groupMapper.deleteById(id);
    }

    @Override
    public void updateGroupStatus(Long id, Integer status) {
        QuestionGroup group = getGroupEntity(id);
        group.setStatus(status);
        groupMapper.updateById(group);
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

    private QuestionGroup getGroupEntity(Long id) {
        QuestionGroup group = groupMapper.selectById(id);
        if (group == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question group not found");
        }
        return group;
    }

    private void applyGroup(QuestionGroup group, SaveQuestionGroupDTO dto) {
        group.setGroupName(dto.getGroupName());
        group.setCanonicalTitle(StringUtils.hasText(dto.getCanonicalTitle()) ? dto.getCanonicalTitle() : dto.getGroupName());
        group.setCanonicalAnswer(dto.getCanonicalAnswer());
        group.setMainKnowledgePoint(dto.getMainKnowledgePoint());
        group.setDifficulty(dto.getDifficulty());
        group.setDescription(dto.getDescription());
        group.setCategoryId(dto.getCategoryId());
    }

    private QuestionGroupVO toGroupVO(QuestionGroup group) {
        QuestionGroupVO vo = QuestionConvert.toGroupVO(group);
        vo.setQuestionCount(questionMapper.selectCount(new LambdaQueryWrapper<Question>().eq(Question::getGroupId, group.getId())));
        return vo;
    }

    private long defaultPage(Long pageNo) {
        return pageNo == null || pageNo <= 0 ? 1L : pageNo;
    }

    private long defaultSize(Long pageSize) {
        return pageSize == null || pageSize <= 0 ? 10L : pageSize;
    }
}
