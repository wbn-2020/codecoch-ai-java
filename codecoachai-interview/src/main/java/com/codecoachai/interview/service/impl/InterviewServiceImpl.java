package com.codecoachai.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.convert.InterviewConvert;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.InterviewStage;
import com.codecoachai.interview.domain.enums.InterviewModeEnum;
import com.codecoachai.interview.domain.enums.InterviewStatusEnum;
import com.codecoachai.interview.domain.enums.NextActionEnum;
import com.codecoachai.interview.domain.enums.ReportStatusEnum;
import com.codecoachai.interview.domain.vo.CreateInterviewVO;
import com.codecoachai.interview.domain.vo.CurrentInterviewVO;
import com.codecoachai.interview.domain.vo.CurrentQuestionVO;
import com.codecoachai.interview.domain.vo.FinishInterviewVO;
import com.codecoachai.interview.domain.vo.InterviewDetailVO;
import com.codecoachai.interview.domain.vo.InterviewListVO;
import com.codecoachai.interview.domain.vo.InterviewReportVO;
import com.codecoachai.interview.domain.vo.InterviewStageVO;
import com.codecoachai.interview.domain.vo.StartInterviewVO;
import com.codecoachai.interview.domain.vo.SubmitInterviewAnswerVO;
import com.codecoachai.interview.feign.AiFeignClient;
import com.codecoachai.interview.feign.QuestionFeignClient;
import com.codecoachai.interview.feign.ResumeFeignClient;
import com.codecoachai.interview.feign.dto.EvaluateAnswerDTO;
import com.codecoachai.interview.feign.dto.GenerateFollowUpDTO;
import com.codecoachai.interview.feign.dto.GenerateInterviewQuestionDTO;
import com.codecoachai.interview.feign.dto.GenerateReportDTO;
import com.codecoachai.interview.feign.dto.InnerSelectQuestionDTO;
import com.codecoachai.interview.feign.vo.EvaluateAnswerVO;
import com.codecoachai.interview.feign.vo.GenerateFollowUpVO;
import com.codecoachai.interview.feign.vo.GenerateInterviewQuestionVO;
import com.codecoachai.interview.feign.vo.GenerateReportVO;
import com.codecoachai.interview.feign.vo.InnerQuestionVO;
import com.codecoachai.interview.feign.vo.InnerResumeDetailVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mapper.InterviewStageMapper;
import com.codecoachai.interview.service.InterviewService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private static final int MAX_FOLLOW_UP_COUNT = 2;
    private static final int QUESTIONS_PER_STAGE = 2;

    private final InterviewSessionMapper sessionMapper;
    private final InterviewStageMapper stageMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewReportMapper reportMapper;
    private final QuestionFeignClient questionFeignClient;
    private final ResumeFeignClient resumeFeignClient;
    private final AiFeignClient aiFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateInterviewVO create(CreateInterviewDTO dto) {
        Long userId = requireCurrentUserId();
        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setResumeId(dto.getResumeId());
        session.setMode(normalizeMode(dto.getMode()));
        session.setTitle(StringUtils.hasText(dto.getTitle()) ? dto.getTitle() : "Mock Interview");
        session.setTargetPosition(dto.getTargetPosition());
        session.setExperienceLevel(dto.getExperienceLevel());
        session.setIndustryDirection(dto.getIndustryDirection());
        session.setDifficulty(dto.getDifficulty());
        session.setInterviewerStyle(dto.getInterviewerStyle());
        session.setBasedOnResume(Boolean.TRUE.equals(dto.getBasedOnResume()));
        session.setStatus(InterviewStatusEnum.NOT_STARTED.name());
        session.setReportStatus(ReportStatusEnum.NOT_GENERATED.name());
        session.setAnsweredQuestionCount(0);
        session.setMaxQuestionCount(dto.getMaxQuestionCount() == null ? 5 : dto.getMaxQuestionCount());
        session.setCurrentFollowUpCount(0);
        sessionMapper.insert(session);
        List<InterviewStage> stages = createStages(session);
        CreateInterviewVO vo = new CreateInterviewVO();
        vo.setId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setMode(session.getMode());
        vo.setTargetPosition(session.getTargetPosition());
        vo.setExperienceLevel(session.getExperienceLevel());
        vo.setIndustryDirection(session.getIndustryDirection());
        vo.setDifficulty(session.getDifficulty());
        vo.setInterviewerStyle(session.getInterviewerStyle());
        vo.setBasedOnResume(session.getBasedOnResume());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setStages(stages.stream().map(InterviewConvert::toStageVO).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StartInterviewVO start(Long id) {
        InterviewSession session = getOwnedSession(id);
        if (!InterviewStatusEnum.NOT_STARTED.name().equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview has already started");
        }
        InterviewStage stage = firstStage(session.getId());
        CurrentQuestionVO question = generateNextQuestion(session, stage);
        session.setStatus(InterviewStatusEnum.IN_PROGRESS.name());
        session.setCurrentStageId(stage.getId());
        session.setCurrentFollowUpCount(0);
        stage.setStatus(InterviewStatusEnum.IN_PROGRESS.name());
        sessionMapper.updateById(session);
        stageMapper.updateById(stage);

        StartInterviewVO vo = new StartInterviewVO();
        vo.setId(session.getId());
        vo.setStatus(session.getStatus());
        vo.setCurrentStage(InterviewConvert.toStageVO(stage));
        vo.setCurrentQuestion(question);
        return vo;
    }

    @Override
    public CurrentInterviewVO current(Long id) {
        InterviewSession session = getOwnedSession(id);
        CurrentInterviewVO vo = new CurrentInterviewVO();
        vo.setId(session.getId());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setCurrentStage(InterviewConvert.toStageVO(session.getCurrentStageId() == null ? null : stageMapper.selectById(session.getCurrentStageId())));
        vo.setCurrentQuestion(currentQuestion(session));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitInterviewAnswerVO answer(Long id, SubmitInterviewAnswerDTO dto) {
        InterviewSession session = getOwnedSession(id);
        if (!InterviewStatusEnum.IN_PROGRESS.name().equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview is not in progress");
        }
        InterviewStage stage = stageMapper.selectById(session.getCurrentStageId());
        InnerQuestionVO question = FeignResultUtils.unwrap(questionFeignClient.getQuestion(session.getCurrentQuestionId()));
        saveMessage(session, stage, question, "USER", "ANSWER", dto.getAnswerContent(), null, null);

        EvaluateAnswerDTO evaluateDTO = new EvaluateAnswerDTO();
        evaluateDTO.setQuestionId(question.getId());
        evaluateDTO.setQuestionTitle(question.getTitle());
        evaluateDTO.setReferenceAnswer(question.getReferenceAnswer());
        evaluateDTO.setAnswerContent(dto.getAnswerContent());
        evaluateDTO.setFollowUpCount(session.getCurrentFollowUpCount());
        EvaluateAnswerVO evaluation = FeignResultUtils.unwrap(aiFeignClient.evaluate(evaluateDTO));
        saveMessage(session, stage, question, "AI", "EVALUATION", evaluation.getComment(), evaluation.getScore(), evaluation.getComment());

        NextActionEnum nextAction = decideNextAction(session, stage, evaluation);
        CurrentQuestionVO nextQuestion = null;
        if (NextActionEnum.FOLLOW_UP.equals(nextAction)) {
            GenerateFollowUpDTO followUpDTO = new GenerateFollowUpDTO();
            followUpDTO.setQuestionId(question.getId());
            followUpDTO.setQuestionTitle(question.getTitle());
            followUpDTO.setAnswerContent(dto.getAnswerContent());
            followUpDTO.setComment(evaluation.getComment());
            GenerateFollowUpVO followUp = FeignResultUtils.unwrap(aiFeignClient.followUp(followUpDTO));
            session.setCurrentFollowUpCount(session.getCurrentFollowUpCount() + 1);
            nextQuestion = new CurrentQuestionVO();
            nextQuestion.setQuestionId(question.getId());
            nextQuestion.setQuestionGroupId(question.getGroupId());
            nextQuestion.setQuestionText(followUp.getFollowUpQuestion());
            saveMessage(session, stage, question, "AI", "FOLLOW_UP", followUp.getFollowUpQuestion(), null, null);
        } else if (NextActionEnum.NEXT_QUESTION.equals(nextAction)) {
            session.setAnsweredQuestionCount(session.getAnsweredQuestionCount() + 1);
            session.setCurrentFollowUpCount(0);
            nextQuestion = generateNextQuestion(session, stage);
        } else if (NextActionEnum.NEXT_STAGE.equals(nextAction)) {
            session.setAnsweredQuestionCount(session.getAnsweredQuestionCount() + 1);
            session.setCurrentFollowUpCount(0);
            InterviewStage nextStage = nextStage(stage);
            if (nextStage == null) {
                nextAction = NextActionEnum.FINISH;
            } else {
                stage.setStatus(InterviewStatusEnum.COMPLETED.name());
                stageMapper.updateById(stage);
                nextStage.setStatus(InterviewStatusEnum.IN_PROGRESS.name());
                stageMapper.updateById(nextStage);
                session.setCurrentStageId(nextStage.getId());
                nextQuestion = generateNextQuestion(session, nextStage);
            }
        }
        sessionMapper.updateById(session);

        SubmitInterviewAnswerVO vo = new SubmitInterviewAnswerVO();
        vo.setScore(evaluation.getScore());
        vo.setComment(evaluation.getComment());
        vo.setNextAction(nextAction.name());
        vo.setNextQuestion(nextQuestion);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinishInterviewVO finish(Long id) {
        InterviewSession session = getOwnedSession(id);
        return finishAndGenerateReport(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinishInterviewVO retryReport(Long id) {
        InterviewSession session = getOwnedSession(id);
        InterviewReport existing = currentReport(session.getId());
        if (existing != null && ReportStatusEnum.GENERATED.name().equals(existing.getStatus())) {
            FinishInterviewVO vo = new FinishInterviewVO();
            vo.setId(session.getId());
            vo.setStatus(session.getStatus());
            vo.setReportStatus(session.getReportStatus());
            vo.setReport(InterviewConvert.toReportVO(existing));
            return vo;
        }
        return finishAndGenerateReport(session);
    }

    @Override
    public PageResult<InterviewListVO> list(Long pageNo, Long pageSize) {
        Page<InterviewSession> page = sessionMapper.selectPage(Page.of(pageNo == null ? 1L : pageNo, pageSize == null ? 10L : pageSize),
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, requireCurrentUserId())
                        .orderByDesc(InterviewSession::getUpdatedAt));
        return PageResult.of(page.getRecords().stream().map(InterviewConvert::toListVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public InterviewDetailVO detail(Long id) {
        InterviewSession session = getOwnedSession(id);
        InterviewDetailVO vo = new InterviewDetailVO();
        vo.setId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setMode(session.getMode());
        vo.setTargetPosition(session.getTargetPosition());
        vo.setExperienceLevel(session.getExperienceLevel());
        vo.setIndustryDirection(session.getIndustryDirection());
        vo.setDifficulty(session.getDifficulty());
        vo.setInterviewerStyle(session.getInterviewerStyle());
        vo.setBasedOnResume(session.getBasedOnResume());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setStages(stages(session.getId()).stream().map(InterviewConvert::toStageVO).toList());
        vo.setMessages(messages(session.getId()).stream().map(InterviewConvert::toMessageVO).toList());
        return vo;
    }

    @Override
    public InterviewReportVO report(Long id) {
        InterviewSession session = getOwnedSession(id);
        InterviewReport report = currentReport(session.getId());
        if (report == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Report not generated");
        }
        return InterviewConvert.toReportVO(report);
    }

    private FinishInterviewVO finishAndGenerateReport(InterviewSession session) {
        session.setReportStatus(ReportStatusEnum.GENERATING.name());
        sessionMapper.updateById(session);
        InterviewReport report = currentReport(session.getId());
        if (report == null) {
            report = new InterviewReport();
            report.setSessionId(session.getId());
        }
        report.setStatus(ReportStatusEnum.GENERATING.name());
        saveReport(report);
        try {
            GenerateReportDTO reportDTO = new GenerateReportDTO();
            reportDTO.setInterviewId(session.getId());
            reportDTO.setMode(session.getMode());
            reportDTO.setMessages(messages(session.getId()).stream().map(InterviewMessage::getContent).toList());
            GenerateReportVO aiReport = FeignResultUtils.unwrap(aiFeignClient.report(reportDTO));
            report.setStatus(ReportStatusEnum.GENERATED.name());
            report.setTotalScore(aiReport.getTotalScore());
            report.setSummary(aiReport.getSummary());
            report.setStrengths(aiReport.getStrengths());
            report.setWeaknesses(aiReport.getWeaknesses());
            report.setSuggestions(aiReport.getSuggestions());
            report.setFailureReason(null);
            session.setStatus(InterviewStatusEnum.COMPLETED.name());
            session.setReportStatus(ReportStatusEnum.GENERATED.name());
        } catch (RuntimeException ex) {
            report.setStatus(ReportStatusEnum.FAILED.name());
            report.setFailureReason(ex.getMessage());
            session.setStatus(InterviewStatusEnum.FAILED.name());
            session.setReportStatus(ReportStatusEnum.FAILED.name());
            session.setFailureReason(ex.getMessage());
        }
        saveReport(report);
        sessionMapper.updateById(session);

        FinishInterviewVO vo = new FinishInterviewVO();
        vo.setId(session.getId());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setReport(InterviewConvert.toReportVO(report));
        return vo;
    }

    private CurrentQuestionVO generateNextQuestion(InterviewSession session, InterviewStage stage) {
        InnerSelectQuestionDTO selectDTO = new InnerSelectQuestionDTO();
        selectDTO.setMode(session.getMode());
        selectDTO.setStageType(stage.getStageType());
        selectDTO.setExcludeGroupIds(usedGroupIds(session.getId()));
        InnerQuestionVO question = FeignResultUtils.unwrap(questionFeignClient.select(selectDTO));

        InnerResumeDetailVO resume = null;
        if (session.getResumeId() != null) {
            resume = FeignResultUtils.unwrap(resumeFeignClient.getResume(session.getResumeId()));
        }
        GenerateInterviewQuestionDTO aiDTO = new GenerateInterviewQuestionDTO();
        aiDTO.setMode(session.getMode());
        aiDTO.setStageType(stage.getStageType());
        aiDTO.setQuestionId(question.getId());
        aiDTO.setQuestionTitle(question.getTitle());
        aiDTO.setQuestionContent(question.getContent());
        aiDTO.setResumeSummary(resume == null ? null : resume.getSummary());
        GenerateInterviewQuestionVO aiQuestion = FeignResultUtils.unwrap(aiFeignClient.generateQuestion(aiDTO));

        session.setCurrentQuestionId(question.getId());
        session.setCurrentQuestionGroupId(question.getGroupId());
        saveMessage(session, stage, question, "AI", "QUESTION", aiQuestion.getQuestionText(), null, null);
        CurrentQuestionVO vo = new CurrentQuestionVO();
        vo.setQuestionId(question.getId());
        vo.setQuestionGroupId(question.getGroupId());
        vo.setQuestionText(aiQuestion.getQuestionText());
        return vo;
    }

    private NextActionEnum decideNextAction(InterviewSession session, InterviewStage stage, EvaluateAnswerVO evaluation) {
        if (session.getAnsweredQuestionCount() + 1 >= session.getMaxQuestionCount()) {
            return NextActionEnum.FINISH;
        }
        if (NextActionEnum.FOLLOW_UP.name().equals(evaluation.getNextAction())
                && session.getCurrentFollowUpCount() < MAX_FOLLOW_UP_COUNT) {
            return NextActionEnum.FOLLOW_UP;
        }
        if (currentStageMainQuestionCount(session.getId(), stage.getId()) >= QUESTIONS_PER_STAGE) {
            return nextStage(stage) == null ? NextActionEnum.FINISH : NextActionEnum.NEXT_STAGE;
        }
        return NextActionEnum.NEXT_QUESTION;
    }

    private Long currentStageMainQuestionCount(Long sessionId, Long stageId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId)
                .eq(InterviewMessage::getStageId, stageId)
                .eq(InterviewMessage::getRole, "AI")
                .eq(InterviewMessage::getMessageType, "QUESTION"));
    }

    private List<InterviewStage> createStages(InterviewSession session) {
        List<InterviewStage> stages = new ArrayList<>();
        stages.add(newStage(session.getId(), "TECHNICAL", "Technical Basics", 1));
        if (!InterviewModeEnum.TECHNICAL_BASIC.name().equals(session.getMode())) {
            stages.add(newStage(session.getId(), "PROJECT", "Project Deep Dive", 2));
        }
        for (InterviewStage stage : stages) {
            stageMapper.insert(stage);
        }
        return stages;
    }

    private InterviewStage newStage(Long sessionId, String type, String name, int sort) {
        InterviewStage stage = new InterviewStage();
        stage.setSessionId(sessionId);
        stage.setStageType(type);
        stage.setStageName(name);
        stage.setSort(sort);
        stage.setStatus(InterviewStatusEnum.NOT_STARTED.name());
        return stage;
    }

    private void saveMessage(InterviewSession session, InterviewStage stage, InnerQuestionVO question,
                             String role, String type, String content, Integer score, String comment) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(session.getId());
        message.setStageId(stage == null ? null : stage.getId());
        message.setQuestionId(question == null ? null : question.getId());
        message.setQuestionGroupId(question == null ? null : question.getGroupId());
        message.setRole(role);
        message.setMessageType(type);
        message.setContent(content);
        message.setScore(score);
        message.setComment(comment);
        messageMapper.insert(message);
    }

    private CurrentQuestionVO currentQuestion(InterviewSession session) {
        if (session.getCurrentQuestionId() == null) {
            return null;
        }
        InterviewMessage message = messageMapper.selectOne(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, session.getId())
                .eq(InterviewMessage::getQuestionId, session.getCurrentQuestionId())
                .eq(InterviewMessage::getRole, "AI")
                .in(InterviewMessage::getMessageType, List.of("QUESTION", "FOLLOW_UP"))
                .orderByDesc(InterviewMessage::getCreatedAt)
                .last("limit 1"));
        CurrentQuestionVO vo = new CurrentQuestionVO();
        vo.setQuestionId(session.getCurrentQuestionId());
        vo.setQuestionGroupId(session.getCurrentQuestionGroupId());
        vo.setQuestionText(message == null ? null : message.getContent());
        return vo;
    }

    private InterviewSession getOwnedSession(Long id) {
        InterviewSession session = sessionMapper.selectById(id);
        if (session == null || !requireCurrentUserId().equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview not found");
        }
        return session;
    }

    private InterviewStage firstStage(Long sessionId) {
        InterviewStage stage = stageMapper.selectOne(new LambdaQueryWrapper<InterviewStage>()
                .eq(InterviewStage::getSessionId, sessionId)
                .orderByAsc(InterviewStage::getSort)
                .last("limit 1"));
        if (stage == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Interview stage missing");
        }
        return stage;
    }

    private InterviewStage nextStage(InterviewStage currentStage) {
        return stageMapper.selectOne(new LambdaQueryWrapper<InterviewStage>()
                .eq(InterviewStage::getSessionId, currentStage.getSessionId())
                .gt(InterviewStage::getSort, currentStage.getSort())
                .orderByAsc(InterviewStage::getSort)
                .last("limit 1"));
    }

    private List<InterviewStage> stages(Long sessionId) {
        return stageMapper.selectList(new LambdaQueryWrapper<InterviewStage>()
                .eq(InterviewStage::getSessionId, sessionId)
                .orderByAsc(InterviewStage::getSort));
    }

    private List<InterviewMessage> messages(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId)
                .orderByAsc(InterviewMessage::getCreatedAt));
    }

    private List<Long> usedGroupIds(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .isNotNull(InterviewMessage::getQuestionGroupId))
                .stream()
                .map(InterviewMessage::getQuestionGroupId)
                .distinct()
                .toList();
    }

    private InterviewReport currentReport(Long sessionId) {
        return reportMapper.selectOne(new LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getSessionId, sessionId)
                .last("limit 1"));
    }

    private void saveReport(InterviewReport report) {
        if (report.getId() == null) {
            reportMapper.insert(report);
        } else {
            reportMapper.updateById(report);
        }
    }

    private String normalizeMode(String mode) {
        try {
            return InterviewModeEnum.valueOf(mode).name();
        } catch (RuntimeException ex) {
            return InterviewModeEnum.COMPREHENSIVE.name();
        }
    }

    private Long requireCurrentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
