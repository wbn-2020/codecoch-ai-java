package com.codecoachai.question.service;

import com.codecoachai.question.domain.dto.SaveQuestionCategoryDTO;
import com.codecoachai.question.domain.dto.SaveQuestionGroupDTO;
import com.codecoachai.question.domain.dto.SaveQuestionTagDTO;
import com.codecoachai.question.domain.vo.QuestionCategoryVO;
import com.codecoachai.question.domain.vo.QuestionGroupVO;
import com.codecoachai.question.domain.vo.QuestionTagVO;
import java.util.List;

public interface QuestionMetadataService {

    List<QuestionCategoryVO> listCategories();

    QuestionCategoryVO createCategory(SaveQuestionCategoryDTO dto);

    QuestionCategoryVO updateCategory(Long id, SaveQuestionCategoryDTO dto);

    void deleteCategory(Long id);

    List<QuestionTagVO> listTags();

    QuestionTagVO createTag(SaveQuestionTagDTO dto);

    QuestionTagVO updateTag(Long id, SaveQuestionTagDTO dto);

    void deleteTag(Long id);

    List<QuestionGroupVO> listGroups();

    QuestionGroupVO createGroup(SaveQuestionGroupDTO dto);

    QuestionGroupVO updateGroup(Long id, SaveQuestionGroupDTO dto);

    void deleteGroup(Long id);
}
