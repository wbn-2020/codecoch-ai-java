package com.codecoachai.question.convert;

import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionCategory;
import com.codecoachai.question.domain.entity.QuestionGroup;
import com.codecoachai.question.domain.entity.QuestionTag;
import com.codecoachai.question.domain.entity.UserQuestionRecord;
import com.codecoachai.question.domain.vo.InnerQuestionVO;
import com.codecoachai.question.domain.vo.QuestionCategoryVO;
import com.codecoachai.question.domain.vo.QuestionDetailVO;
import com.codecoachai.question.domain.vo.QuestionGroupVO;
import com.codecoachai.question.domain.vo.QuestionListVO;
import com.codecoachai.question.domain.vo.QuestionTagVO;
import com.codecoachai.question.domain.vo.WrongQuestionVO;
import java.util.List;

public final class QuestionConvert {

    private QuestionConvert() {
    }

    public static QuestionListVO toListVO(Question question, String categoryName, List<QuestionTagVO> tags,
                                          UserQuestionRecord record) {
        QuestionListVO vo = new QuestionListVO();
        vo.setId(question.getId());
        vo.setTitle(question.getTitle());
        vo.setCategoryId(question.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setGroupId(question.getGroupId());
        vo.setDifficulty(question.getDifficulty());
        vo.setQuestionType(question.getQuestionType());
        vo.setExperienceLevel(question.getExperienceLevel());
        vo.setIsHighFrequency(question.getIsHighFrequency());
        vo.setStatus(question.getStatus());
        vo.setTags(tags);
        vo.setTagIds(tags.stream().map(QuestionTagVO::getId).toList());
        vo.setTagNames(tags.stream().map(QuestionTagVO::getName).toList());
        vo.setFavorite(record != null && Integer.valueOf(1).equals(record.getFavorite()));
        vo.setMasteryStatus(record == null ? null : record.getMasteryStatus());
        vo.setCreatedAt(question.getCreatedAt());
        return vo;
    }

    public static QuestionDetailVO toDetailVO(Question question, String categoryName, String groupName,
                                              List<QuestionTagVO> tags, UserQuestionRecord record) {
        QuestionDetailVO vo = new QuestionDetailVO();
        vo.setId(question.getId());
        vo.setTitle(question.getTitle());
        vo.setContent(question.getContent());
        vo.setReferenceAnswer(question.getReferenceAnswer());
        vo.setAnalysis(question.getAnalysis());
        vo.setCategoryId(question.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setGroupId(question.getGroupId());
        vo.setGroupName(groupName);
        vo.setDifficulty(question.getDifficulty());
        vo.setQuestionType(question.getQuestionType());
        vo.setExperienceLevel(question.getExperienceLevel());
        vo.setIsHighFrequency(question.getIsHighFrequency());
        vo.setStatus(question.getStatus());
        vo.setTags(tags);
        vo.setTagIds(tags.stream().map(QuestionTagVO::getId).toList());
        vo.setTagNames(tags.stream().map(QuestionTagVO::getName).toList());
        vo.setFavorite(record != null && Integer.valueOf(1).equals(record.getFavorite()));
        vo.setMasteryStatus(record == null ? null : record.getMasteryStatus());
        return vo;
    }

    public static InnerQuestionVO toInnerVO(Question question) {
        InnerQuestionVO vo = new InnerQuestionVO();
        vo.setId(question.getId());
        vo.setGroupId(question.getGroupId());
        vo.setTitle(question.getTitle());
        vo.setContent(question.getContent());
        vo.setReferenceAnswer(question.getReferenceAnswer());
        vo.setAnalysis(question.getAnalysis());
        vo.setDifficulty(question.getDifficulty());
        vo.setQuestionType(question.getQuestionType());
        vo.setExperienceLevel(question.getExperienceLevel());
        return vo;
    }

    public static QuestionCategoryVO toCategoryVO(QuestionCategory category) {
        QuestionCategoryVO vo = new QuestionCategoryVO();
        vo.setId(category.getId());
        vo.setParentId(category.getParentId());
        vo.setCategoryName(category.getCategoryName());
        vo.setSort(category.getSort());
        vo.setSortOrder(category.getSortOrder());
        vo.setStatus(category.getStatus());
        return vo;
    }

    public static QuestionTagVO toTagVO(QuestionTag tag) {
        QuestionTagVO vo = new QuestionTagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getTagName());
        vo.setTagName(tag.getTagName());
        vo.setStatus(tag.getStatus());
        return vo;
    }

    public static QuestionGroupVO toGroupVO(QuestionGroup group) {
        QuestionGroupVO vo = new QuestionGroupVO();
        vo.setId(group.getId());
        vo.setGroupName(group.getGroupName());
        vo.setCanonicalTitle(group.getCanonicalTitle());
        vo.setCanonicalAnswer(group.getCanonicalAnswer());
        vo.setMainKnowledgePoint(group.getMainKnowledgePoint());
        vo.setDifficulty(group.getDifficulty());
        vo.setDescription(group.getDescription());
        vo.setCategoryId(group.getCategoryId());
        vo.setStatus(group.getStatus());
        return vo;
    }

    public static WrongQuestionVO toWrongVO(UserQuestionRecord record, Question question) {
        WrongQuestionVO vo = new WrongQuestionVO();
        vo.setRecordId(record.getId());
        vo.setQuestionId(record.getQuestionId());
        vo.setTitle(question == null ? null : question.getTitle());
        vo.setMasteryStatus(record.getMasteryStatus());
        vo.setLastAnswerAt(record.getLastAnswerAt());
        return vo;
    }
}
