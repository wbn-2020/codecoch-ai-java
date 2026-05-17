package com.codecoachai.interview.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.domain.vo.CreateInterviewVO;
import com.codecoachai.interview.domain.vo.CurrentInterviewVO;
import com.codecoachai.interview.domain.vo.FinishInterviewVO;
import com.codecoachai.interview.domain.vo.InterviewDetailVO;
import com.codecoachai.interview.domain.vo.InterviewListVO;
import com.codecoachai.interview.domain.vo.InterviewMessageVO;
import com.codecoachai.interview.domain.vo.InterviewReportGenerateResultVO;
import com.codecoachai.interview.domain.vo.InterviewReportVO;
import com.codecoachai.interview.domain.vo.StartInterviewVO;
import com.codecoachai.interview.domain.vo.SubmitInterviewAnswerVO;

public interface InterviewService {

    CreateInterviewVO create(CreateInterviewDTO dto);

    StartInterviewVO start(Long id);

    CurrentInterviewVO current(Long id);

    com.codecoachai.interview.domain.vo.CurrentQuestionVO currentQuestion(Long id);

    SubmitInterviewAnswerVO answer(Long id, SubmitInterviewAnswerDTO dto);

    SubmitInterviewAnswerVO answerForSse(Long id, SubmitInterviewAnswerDTO dto,
                                         java.util.function.Consumer<String> progressConsumer);

    FinishInterviewVO finish(Long id);

    FinishInterviewVO retryReport(Long id);

    PageResult<InterviewListVO> list(Long pageNo, Long pageSize);

    InterviewDetailVO detail(Long id);

    java.util.List<InterviewMessageVO> messages(Long id);

    InterviewReportVO report(Long id);

    InterviewReportGenerateResultVO generateReportForSse(Long id, Long reportId, Boolean forceRegenerate,
                                                         java.util.function.Consumer<String> progressConsumer);
}
