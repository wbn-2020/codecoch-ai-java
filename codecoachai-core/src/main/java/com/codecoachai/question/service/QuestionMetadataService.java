package com.codecoachai.question.service;

import com.codecoachai.question.domain.dto.SaveQuestionCategoryDTO;
import com.codecoachai.question.domain.dto.SaveQuestionGroupDTO;
import com.codecoachai.question.domain.dto.SaveQuestionTagDTO;
import com.codecoachai.question.domain.dto.QuestionMetadataQueryDTO;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.question.domain.vo.QuestionCategoryVO;
import com.codecoachai.question.domain.vo.QuestionGroupVO;
import com.codecoachai.question.domain.vo.QuestionTagVO;
import java.util.List;

public interface QuestionMetadataService {

    List<QuestionCategoryVO> listCategories();

    List<QuestionCategoryVO> treeCategories();

    QuestionCategoryVO createCategory(SaveQuestionCategoryDTO dto);

    QuestionCategoryVO updateCategory(Long id, SaveQuestionCategoryDTO dto);

    void deleteCategory(Long id);

    void updateCategoryStatus(Long id, Integer status);

    List<QuestionTagVO> listTags();

    PageResult<QuestionTagVO> pageTags(QuestionMetadataQueryDTO query);

    QuestionTagVO createTag(SaveQuestionTagDTO dto);

    QuestionTagVO updateTag(Long id, SaveQuestionTagDTO dto);

    void deleteTag(Long id);

    void updateTagStatus(Long id, Integer status);

    List<QuestionGroupVO> listGroups();

    PageResult<QuestionGroupVO> pageGroups(QuestionMetadataQueryDTO query);

    QuestionGroupVO getGroup(Long id);

    QuestionGroupVO createGroup(SaveQuestionGroupDTO dto);

    QuestionGroupVO updateGroup(Long id, SaveQuestionGroupDTO dto);

    void deleteGroup(Long id);

    void updateGroupStatus(Long id, Integer status);
}
