package com.codecoachai.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.convert.QuestionConvert;
import com.codecoachai.question.domain.dto.AdminQuestionSaveDTO;
import com.codecoachai.question.domain.dto.InnerSelectQuestionDTO;
import com.codecoachai.question.domain.dto.QuestionQueryDTO;
import com.codecoachai.question.domain.dto.RecommendQuestionDTO;
import com.codecoachai.question.domain.dto.SubmitQuestionAnswerDTO;
import com.codecoachai.question.domain.dto.UpdateMasteryDTO;
import com.codecoachai.question.domain.dto.UpdateStatusDTO;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionCategory;
import com.codecoachai.question.domain.entity.QuestionGroup;
import com.codecoachai.question.domain.entity.QuestionTag;
import com.codecoachai.question.domain.entity.QuestionTagRelation;
import com.codecoachai.question.domain.entity.UserQuestionRecord;
import com.codecoachai.question.domain.enums.MasteryStatusEnum;
import com.codecoachai.question.domain.vo.InnerQuestionVO;
import com.codecoachai.question.domain.vo.QuestionDetailVO;
import com.codecoachai.question.domain.vo.QuestionListVO;
import com.codecoachai.question.domain.vo.QuestionTagVO;
import com.codecoachai.question.domain.vo.SubmitQuestionAnswerVO;
import com.codecoachai.question.domain.vo.WrongQuestionVO;
import com.codecoachai.question.mapper.QuestionCategoryMapper;
import com.codecoachai.question.mapper.QuestionGroupMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionTagMapper;
import com.codecoachai.question.mapper.QuestionTagRelationMapper;
import com.codecoachai.question.mapper.UserQuestionRecordMapper;
import com.codecoachai.question.service.QuestionService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private static final Long NO_MATCH_QUESTION_ID = -1L;

    private final QuestionMapper questionMapper;
    private final QuestionCategoryMapper categoryMapper;
    private final QuestionGroupMapper groupMapper;
    private final QuestionTagMapper tagMapper;
    private final QuestionTagRelationMapper tagRelationMapper;
    private final UserQuestionRecordMapper recordMapper;

    @Override
    public PageResult<QuestionListVO> pageQuestions(QuestionQueryDTO query) {
        Page<Question> page = questionMapper.selectPage(Page.of(query.getPageNo(), query.getPageSize()),
                buildQuestionWrapper(query, false).eq(Question::getStatus, CommonConstants.YES));
        return PageResult.of(page.getRecords().stream().map(this::toListVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public QuestionDetailVO getQuestion(Long id) {
        return toDetailVO(getQuestionOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitQuestionAnswerVO submitAnswer(Long id, SubmitQuestionAnswerDTO dto) {
        Question question = getQuestionOrThrow(id);
        UserQuestionRecord record = getOrCreateRecord(requireCurrentUserId(), id);
        String mastery = StringUtils.hasText(dto.getMasteryStatus())
                ? dto.getMasteryStatus()
                : inferMastery(dto.getAnswerContent());
        record.setAnswerContent(dto.getAnswerContent());
        record.setMasteryStatus(mastery);
        record.setWrong(MasteryStatusEnum.NOT_MASTERED.name().equals(mastery) ? CommonConstants.YES : CommonConstants.NO);
        record.setLastAnswerAt(LocalDateTime.now());
        saveRecord(record);

        SubmitQuestionAnswerVO vo = new SubmitQuestionAnswerVO();
        vo.setRecordId(record.getId());
        vo.setQuestionId(question.getId());
        vo.setReferenceAnswer(question.getReferenceAnswer());
        vo.setAnalysis(question.getAnalysis());
        vo.setMasteryStatus(record.getMasteryStatus());
        vo.setWrong(CommonConstants.YES.equals(record.getWrong()));
        return vo;
    }

    @Override
    public void favorite(Long id) {
        getQuestionOrThrow(id);
        UserQuestionRecord record = getOrCreateRecord(requireCurrentUserId(), id);
        record.setFavorite(CommonConstants.YES);
        saveRecord(record);
    }

    @Override
    public void cancelFavorite(Long id) {
        getQuestionOrThrow(id);
        UserQuestionRecord record = getOrCreateRecord(requireCurrentUserId(), id);
        record.setFavorite(CommonConstants.NO);
        saveRecord(record);
    }

    @Override
    public PageResult<QuestionListVO> pageFavorites(QuestionQueryDTO query) {
        Long userId = requireCurrentUserId();
        Page<UserQuestionRecord> page = recordMapper.selectPage(Page.of(query.getPageNo(), query.getPageSize()),
                new LambdaQueryWrapper<UserQuestionRecord>()
                        .eq(UserQuestionRecord::getUserId, userId)
                        .eq(UserQuestionRecord::getFavorite, CommonConstants.YES)
                        .orderByDesc(UserQuestionRecord::getUpdatedAt));
        List<QuestionListVO> records = page.getRecords().stream()
                .map(record -> questionMapper.selectById(record.getQuestionId()))
                .filter(question -> question != null && CommonConstants.YES.equals(question.getStatus()))
                .map(this::toListVO)
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<WrongQuestionVO> pageWrongRecords(QuestionQueryDTO query) {
        Long userId = requireCurrentUserId();
        Page<UserQuestionRecord> page = recordMapper.selectPage(Page.of(query.getPageNo(), query.getPageSize()),
                new LambdaQueryWrapper<UserQuestionRecord>()
                        .eq(UserQuestionRecord::getUserId, userId)
                        .eq(UserQuestionRecord::getWrong, CommonConstants.YES)
                        .orderByDesc(UserQuestionRecord::getLastAnswerAt));
        List<WrongQuestionVO> records = page.getRecords().stream()
                .map(record -> QuestionConvert.toWrongVO(record, questionMapper.selectById(record.getQuestionId())))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void updateMastery(Long id, UpdateMasteryDTO dto) {
        getQuestionOrThrow(id);
        UserQuestionRecord record = getOrCreateRecord(requireCurrentUserId(), id);
        record.setMasteryStatus(dto.getMasteryStatus());
        if (MasteryStatusEnum.MASTERED.name().equals(dto.getMasteryStatus())) {
            record.setWrong(CommonConstants.NO);
        }
        saveRecord(record);
    }

    @Override
    public PageResult<QuestionListVO> pageAdminQuestions(QuestionQueryDTO query) {
        Page<Question> page = questionMapper.selectPage(Page.of(query.getPageNo(), query.getPageSize()),
                buildQuestionWrapper(query, true));
        return PageResult.of(page.getRecords().stream().map(this::toListVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionDetailVO createQuestion(AdminQuestionSaveDTO dto) {
        Question question = new Question();
        applyQuestion(question, dto);
        questionMapper.insert(question);
        replaceTags(question.getId(), dto.getTagIds());
        return toDetailVO(question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionDetailVO updateQuestion(Long id, AdminQuestionSaveDTO dto) {
        Question question = getQuestionOrThrow(id);
        applyQuestion(question, dto);
        questionMapper.updateById(question);
        replaceTags(question.getId(), dto.getTagIds());
        return toDetailVO(getQuestionOrThrow(id));
    }

    @Override
    public void deleteQuestion(Long id) {
        questionMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, UpdateStatusDTO dto) {
        Question question = getQuestionOrThrow(id);
        question.setStatus(dto.getStatus());
        questionMapper.updateById(question);
    }

    @Override
    public InnerQuestionVO selectForInterview(InnerSelectQuestionDTO dto) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, CommonConstants.YES)
                .eq(dto.getCategoryId() != null, Question::getCategoryId, dto.getCategoryId())
                .eq(StringUtils.hasText(dto.getDifficulty()), Question::getDifficulty, dto.getDifficulty())
                .eq(StringUtils.hasText(dto.getExperienceLevel()), Question::getExperienceLevel, dto.getExperienceLevel())
                .notIn(dto.getExcludeGroupIds() != null && !dto.getExcludeGroupIds().isEmpty(),
                        Question::getGroupId, dto.getExcludeGroupIds())
                .orderByAsc(Question::getId)
                .last("limit 1");
        Question question = questionMapper.selectOne(wrapper);
        if (question == null) {
            question = questionMapper.selectOne(new LambdaQueryWrapper<Question>()
                    .eq(Question::getStatus, CommonConstants.YES)
                    .notIn(dto.getExcludeGroupIds() != null && !dto.getExcludeGroupIds().isEmpty(),
                            Question::getGroupId, dto.getExcludeGroupIds())
                    .orderByAsc(Question::getId)
                    .last("limit 1"));
        }
        if (question == null) {
            InnerQuestionVO fallback = new InnerQuestionVO();
            fallback.setTitle("AI_GENERATED_FALLBACK");
            fallback.setContent("请结合当前面试阶段生成一道 V1 Java 后端面试问题。");
            fallback.setDifficulty(StringUtils.hasText(dto.getDifficulty()) ? dto.getDifficulty() : "MEDIUM");
            fallback.setExperienceLevel(dto.getExperienceLevel());
            fallback.setQuestionType("AI_GENERATED");
            return fallback;
        }
        return QuestionConvert.toInnerVO(question);
    }

    @Override
    public InnerQuestionVO getInnerQuestion(Long id) {
        return QuestionConvert.toInnerVO(getQuestionOrThrow(id));
    }

    @Override
    public List<InnerQuestionVO> recommend(RecommendQuestionDTO dto) {
        long limit = dto == null || dto.getLimit() == null ? 5L : dto.getLimit();
        List<String> weakTags = dto == null || dto.getWeakTags() == null
                ? Collections.emptyList()
                : dto.getWeakTags().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(8)
                .toList();
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, CommonConstants.YES);
        if (!weakTags.isEmpty()) {
            wrapper.and(condition -> {
                boolean first = true;
                for (String keyword : weakTags) {
                    if (first) {
                        condition.like(Question::getTitle, keyword)
                                .or()
                                .like(Question::getContent, keyword)
                                .or()
                                .like(Question::getAnalysis, keyword)
                                .or()
                                .like(Question::getReferenceAnswer, keyword);
                        first = false;
                    } else {
                        condition.or(group -> group.like(Question::getTitle, keyword)
                                .or()
                                .like(Question::getContent, keyword)
                                .or()
                                .like(Question::getAnalysis, keyword)
                                .or()
                                .like(Question::getReferenceAnswer, keyword));
                    }
                }
            });
        }
        List<Question> records = questionMapper.selectPage(Page.of(1, limit), wrapper
                        .orderByDesc(Question::getIsHighFrequency)
                        .orderByDesc(Question::getUpdatedAt))
                .getRecords();
        if (records.isEmpty() && !weakTags.isEmpty()) {
            records = questionMapper.selectPage(Page.of(1, limit), new LambdaQueryWrapper<Question>()
                            .eq(Question::getStatus, CommonConstants.YES)
                            .orderByDesc(Question::getIsHighFrequency)
                            .orderByDesc(Question::getUpdatedAt))
                    .getRecords();
        }
        return new ArrayList<>(records).stream()
                .map(QuestionConvert::toInnerVO)
                .toList();
    }

    private LambdaQueryWrapper<Question> buildQuestionWrapper(QuestionQueryDTO query, boolean includeStatusFilter) {
        List<Long> questionIds = findQuestionIdsByTag(query.getTagId());
        return new LambdaQueryWrapper<Question>()
                .eq(query.getCategoryId() != null, Question::getCategoryId, query.getCategoryId())
                .eq(StringUtils.hasText(query.getDifficulty()), Question::getDifficulty, query.getDifficulty())
                .eq(StringUtils.hasText(query.getQuestionType()), Question::getQuestionType, query.getQuestionType())
                .eq(StringUtils.hasText(query.getExperienceLevel()), Question::getExperienceLevel, query.getExperienceLevel())
                .eq(query.getIsHighFrequency() != null, Question::getIsHighFrequency, query.getIsHighFrequency())
                .eq(includeStatusFilter && query.getStatus() != null, Question::getStatus, query.getStatus())
                .in(query.getTagId() != null && !questionIds.isEmpty(), Question::getId, questionIds)
                .eq(query.getTagId() != null && questionIds.isEmpty(), Question::getId, NO_MATCH_QUESTION_ID)
                .and(StringUtils.hasText(query.getKeyword()), condition -> condition
                        .like(Question::getTitle, query.getKeyword())
                        .or()
                        .like(Question::getContent, query.getKeyword()))
                .orderByDesc(Question::getUpdatedAt);
    }

    private List<Long> findQuestionIdsByTag(Long tagId) {
        if (tagId == null) {
            return Collections.emptyList();
        }
        return tagRelationMapper.selectList(new LambdaQueryWrapper<QuestionTagRelation>()
                        .eq(QuestionTagRelation::getTagId, tagId))
                .stream()
                .map(QuestionTagRelation::getQuestionId)
                .filter(questionId -> questionId != null)
                .distinct()
                .toList();
    }

    private void applyQuestion(Question question, AdminQuestionSaveDTO dto) {
        validateQuestionRefs(dto);
        question.setTitle(dto.getTitle());
        question.setContent(dto.getContent());
        question.setReferenceAnswer(StringUtils.hasText(dto.getReferenceAnswer()) ? dto.getReferenceAnswer() : dto.getAnswer());
        question.setAnalysis(dto.getAnalysis());
        question.setCategoryId(dto.getCategoryId());
        question.setGroupId(dto.getGroupId());
        question.setDifficulty(StringUtils.hasText(dto.getDifficulty()) ? dto.getDifficulty() : "MEDIUM");
        question.setQuestionType(StringUtils.hasText(dto.getQuestionType()) ? dto.getQuestionType() : "SHORT_ANSWER");
        question.setExperienceLevel(dto.getExperienceLevel());
        question.setIsHighFrequency(dto.getIsHighFrequency() == null ? CommonConstants.NO : dto.getIsHighFrequency());
        question.setStatus(dto.getStatus() == null ? CommonConstants.YES : dto.getStatus());
    }

    private void replaceTags(Long questionId, List<Long> tagIds) {
        tagRelationMapper.delete(new LambdaQueryWrapper<QuestionTagRelation>()
                .eq(QuestionTagRelation::getQuestionId, questionId));
        if (tagIds == null) {
            return;
        }
        for (Long tagId : tagIds) {
            QuestionTag tag = tagMapper.selectById(tagId);
            if (tag == null || !CommonConstants.YES.equals(tag.getStatus())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Question tag unavailable");
            }
            QuestionTagRelation relation = new QuestionTagRelation();
            relation.setQuestionId(questionId);
            relation.setTagId(tagId);
            tagRelationMapper.insert(relation);
        }
    }

    private QuestionListVO toListVO(Question question) {
        return QuestionConvert.toListVO(question, categoryName(question.getCategoryId()), questionTags(question.getId()),
                currentRecord(question.getId()));
    }

    private QuestionDetailVO toDetailVO(Question question) {
        return QuestionConvert.toDetailVO(question, categoryName(question.getCategoryId()), groupName(question.getGroupId()),
                questionTags(question.getId()), currentRecord(question.getId()));
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        QuestionCategory category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getCategoryName();
    }

    private String groupName(Long groupId) {
        if (groupId == null) {
            return null;
        }
        QuestionGroup group = groupMapper.selectById(groupId);
        return group == null ? null : group.getGroupName();
    }

    private List<QuestionTagVO> questionTags(Long questionId) {
        List<QuestionTagRelation> relations = tagRelationMapper.selectList(new LambdaQueryWrapper<QuestionTagRelation>()
                .eq(QuestionTagRelation::getQuestionId, questionId));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        return relations.stream()
                .map(QuestionTagRelation::getTagId)
                .map(tagMapper::selectById)
                .filter(tag -> tag != null)
                .map(QuestionConvert::toTagVO)
                .toList();
    }

    private void validateQuestionRefs(AdminQuestionSaveDTO dto) {
        if (dto.getCategoryId() != null) {
            QuestionCategory category = categoryMapper.selectById(dto.getCategoryId());
            if (category == null || !CommonConstants.YES.equals(category.getStatus())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Question category unavailable");
            }
        }
        if (dto.getGroupId() != null) {
            QuestionGroup group = groupMapper.selectById(dto.getGroupId());
            if (group == null || !CommonConstants.YES.equals(group.getStatus())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Question group unavailable");
            }
        }
    }

    private Question getQuestionOrThrow(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question not found");
        }
        return question;
    }

    private UserQuestionRecord currentRecord(Long questionId) {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            return null;
        }
        return recordMapper.selectOne(new LambdaQueryWrapper<UserQuestionRecord>()
                .eq(UserQuestionRecord::getUserId, userId)
                .eq(UserQuestionRecord::getQuestionId, questionId)
                .last("limit 1"));
    }

    private UserQuestionRecord getOrCreateRecord(Long userId, Long questionId) {
        UserQuestionRecord record = recordMapper.selectOne(new LambdaQueryWrapper<UserQuestionRecord>()
                .eq(UserQuestionRecord::getUserId, userId)
                .eq(UserQuestionRecord::getQuestionId, questionId)
                .last("limit 1"));
        if (record != null) {
            return record;
        }
        record = new UserQuestionRecord();
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setMasteryStatus(MasteryStatusEnum.UNKNOWN.name());
        record.setWrong(CommonConstants.NO);
        record.setFavorite(CommonConstants.NO);
        return record;
    }

    private void saveRecord(UserQuestionRecord record) {
        if (record.getId() == null) {
            recordMapper.insert(record);
        } else {
            recordMapper.updateById(record);
        }
    }

    private Long requireCurrentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private String inferMastery(String answer) {
        return StringUtils.hasText(answer) && answer.trim().length() >= 20
                ? MasteryStatusEnum.MASTERED.name()
                : MasteryStatusEnum.NOT_MASTERED.name();
    }
}
