package com.codecoachai.question.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.question.domain.dto.AdminQuestionSaveDTO;
import com.codecoachai.question.domain.dto.InnerSelectQuestionDTO;
import com.codecoachai.question.domain.dto.QuestionQueryDTO;
import com.codecoachai.question.domain.dto.RecommendQuestionDTO;
import com.codecoachai.question.domain.dto.SubmitQuestionAnswerDTO;
import com.codecoachai.question.domain.dto.UpdateMasteryDTO;
import com.codecoachai.question.domain.dto.UpdateStatusDTO;
import com.codecoachai.question.domain.vo.InnerQuestionVO;
import com.codecoachai.question.domain.vo.QuestionDetailVO;
import com.codecoachai.question.domain.vo.QuestionListVO;
import com.codecoachai.question.domain.vo.SubmitQuestionAnswerVO;
import com.codecoachai.question.domain.vo.WrongQuestionVO;
import java.util.List;

public interface QuestionService {

    PageResult<QuestionListVO> pageQuestions(QuestionQueryDTO query);

    QuestionDetailVO getQuestion(Long id);

    SubmitQuestionAnswerVO submitAnswer(Long id, SubmitQuestionAnswerDTO dto);

    void favorite(Long id);

    void cancelFavorite(Long id);

    PageResult<QuestionListVO> pageFavorites(QuestionQueryDTO query);

    PageResult<WrongQuestionVO> pageWrongRecords(QuestionQueryDTO query);

    void updateMastery(Long id, UpdateMasteryDTO dto);

    PageResult<QuestionListVO> pageAdminQuestions(QuestionQueryDTO query);

    QuestionDetailVO createQuestion(AdminQuestionSaveDTO dto);

    QuestionDetailVO updateQuestion(Long id, AdminQuestionSaveDTO dto);

    void deleteQuestion(Long id);

    void updateStatus(Long id, UpdateStatusDTO dto);

    InnerQuestionVO selectForInterview(InnerSelectQuestionDTO dto);

    InnerQuestionVO getInnerQuestion(Long id);

    List<InnerQuestionVO> recommend(RecommendQuestionDTO dto);
}
