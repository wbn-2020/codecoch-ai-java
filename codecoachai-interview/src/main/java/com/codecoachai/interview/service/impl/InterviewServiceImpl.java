package com.codecoachai.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.convert.InterviewConvert;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.domain.entity.IndustryTemplate;
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
import com.codecoachai.interview.domain.vo.InterviewMessageVO;
import com.codecoachai.interview.domain.vo.InterviewReportGenerateResultVO;
import com.codecoachai.interview.domain.vo.InterviewReportMissingSkillVO;
import com.codecoachai.interview.domain.vo.InterviewReportVO;
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
import com.codecoachai.interview.feign.vo.InnerResumeJobMatchReportVO;
import com.codecoachai.interview.feign.vo.InnerResumeProjectVO;
import com.codecoachai.interview.feign.vo.InnerSkillGapItemVO;
import com.codecoachai.interview.feign.vo.InnerSkillProfileVO;
import com.codecoachai.interview.feign.vo.InnerTargetJobVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mapper.InterviewStageMapper;
import com.codecoachai.interview.mq.InterviewMqDispatcher;
import com.codecoachai.interview.service.IndustryTemplateService;
import com.codecoachai.interview.service.InterviewService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewServiceImpl implements InterviewService {

    private static final int MAX_FOLLOW_UP_COUNT = 2;
    private static final String REPORT_AI_EMPTY_MESSAGE = "AI 报告内容暂时不完整";
    private static final String REPORT_AI_INCOMPLETE_MESSAGE = REPORT_AI_EMPTY_MESSAGE
            + "，未生成可核对的题目明细。答题记录已保留，请稍后重新生成报告。";
    private static final String REPORT_AI_INCOMPLETE_SUGGESTIONS = "[\"稍后重新生成面试报告\",\"继续补充回答后再生成报告\",\"如多次失败，请将诊断记录交给管理员排查\"]";
    private static final String REPORT_AI_INCOMPLETE_FALLBACK_REASON =
            "AI 报告结构不完整，已基于已保存题目、回答、评分和追问生成保底复盘。";
    private static final String DEFAULT_REPORT_SUMMARY = "本场模拟面试已完成，请结合题目明细复盘回答表现。";
    private static final String DEFAULT_REPORT_STRENGTHS = "回答亮点：能够围绕 Java 后端常见题目给出基本结论，并能结合 Spring、MySQL、Redis 等技术栈说明常见处理思路。项目类问题中能描述业务背景和核心方案。";
    private static final String DEFAULT_REPORT_WEAKNESSES = "主要问题：部分回答停留在结论层，对源码细节、执行计划字段、缓存一致性边界和线上排查步骤展开不足，项目优化结果缺少量化指标。";
    private static final String DEFAULT_REPORT_SUGGESTIONS = "复习建议：1. 复盘集合、并发、事务、索引和缓存的高频题；2. 准备 2-3 个带指标的项目优化案例；3. 回答时按结论、原理、项目实践、风险边界的顺序组织。";
    private static final String REPORT_SAMPLE_INSUFFICIENT_MESSAGE = "答题样本不足，无法生成评分报告。请至少提交 1 条有效回答后再结束面试。";
    private static final String REPORT_SAMPLE_INSUFFICIENT_SUGGESTIONS = "[\"至少提交 1 条有效回答后再结束面试\",\"如果只是想退出，可稍后重新开始面试训练\"]";
    private static final String REPORT_GENERATION_FAILED_MESSAGE =
            "面试报告生成失败，答题记录已保留，请稍后重新生成或联系管理员查看诊断。";

    private final InterviewSessionMapper sessionMapper;
    private final InterviewStageMapper stageMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewReportMapper reportMapper;
    private final QuestionFeignClient questionFeignClient;
    private final ResumeFeignClient resumeFeignClient;
    private final AiFeignClient aiFeignClient;
    private final InterviewReportAsyncService reportAsyncService;
    private final IndustryTemplateService industryTemplateService;
    private final InterviewMqDispatcher interviewMqDispatcher;
    private final AgentBusinessActionNotifier agentBusinessActionNotifier;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateInterviewVO create(CreateInterviewDTO dto) {
        CreateInterviewDTO request = dto == null ? new CreateInterviewDTO() : dto;
        Long userId = requireCurrentUserId();
        validateSuccessfulMatchReportContext(userId, request);
        String mode = normalizeMode(StringUtils.hasText(request.getInterviewMode()) ? request.getInterviewMode() : request.getMode());
        InnerTargetJobVO targetJob = resolveTargetJob(userId, request);
        resolveDefaultResumeIfNeeded(mode, request);
        validateResumeOwnership(userId, mode, request);
        IndustryTemplateSnapshot industrySnapshot = withRecommendationContext(resolveIndustrySnapshot(request), request);

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setResumeId(request.getResumeId());
        session.setTargetJobId(request.getTargetJobId());
        session.setSkillProfileId(request.getSkillProfileId());
        session.setMatchReportId(request.getMatchReportId());
        session.setMode(mode);
        session.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle() : "CodeCoachAI V1 模拟面试");
        session.setTargetPosition(firstText(request.getTargetPosition(), targetJob == null ? null : targetJob.getJobTitle()));
        session.setExperienceLevel(request.getExperienceLevel());
        session.setIndustryTemplateId(industrySnapshot.industryTemplateId());
        session.setIndustryDirection(industrySnapshot.industryDirection());
        session.setIndustryContext(industrySnapshot.industryContext());
        session.setDifficulty(request.getDifficulty());
        session.setInterviewerStyle(request.getInterviewerStyle());
        session.setBasedOnResume(Boolean.TRUE.equals(request.getBasedOnResume()));
        session.setStatus(InterviewStatusEnum.NOT_STARTED.name());
        session.setReportStatus(ReportStatusEnum.NOT_GENERATED.name());
        session.setAnsweredQuestionCount(0);
        session.setMaxQuestionCount(normalizeQuestionCount(request.getMaxQuestionCount()));
        session.setCurrentFollowUpCount(0);
        session.setTotalScore(0);
        sessionMapper.insert(session);

        List<InterviewStage> stages = createStages(session);
        int totalQuestionCount = totalExpectedQuestionCount(stages);
        if (!Integer.valueOf(totalQuestionCount).equals(session.getMaxQuestionCount())) {
            session.setMaxQuestionCount(totalQuestionCount);
            sessionMapper.updateById(session);
        }
        CreateInterviewVO vo = new CreateInterviewVO();
        vo.setId(session.getId());
        vo.setTargetJobId(session.getTargetJobId());
        vo.setSkillProfileId(session.getSkillProfileId());
        vo.setMatchReportId(session.getMatchReportId());
        vo.setTitle(session.getTitle());
        vo.setMode(session.getMode());
        vo.setTargetPosition(session.getTargetPosition());
        vo.setExperienceLevel(session.getExperienceLevel());
        vo.setIndustryTemplateId(session.getIndustryTemplateId());
        vo.setIndustryDirection(session.getIndustryDirection());
        vo.setIndustryContext(session.getIndustryContext());
        vo.setDifficulty(session.getDifficulty());
        vo.setInterviewerStyle(session.getInterviewerStyle());
        vo.setBasedOnResume(session.getBasedOnResume());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setMaxQuestionCount(session.getMaxQuestionCount());
        vo.setTotalQuestionCount(totalQuestionCount);
        vo.setAnsweredQuestionCount(session.getAnsweredQuestionCount());
        vo.setOverallProgress(overallProgress(session));
        vo.setStages(stages.stream().map(InterviewConvert::toStageVO).toList());
        return vo;
    }

    @Override
    public StartInterviewVO start(Long id) {
        StartContext context = transactionTemplate.execute(status -> prepareStart(id));
        InterviewSession session = context.session();
        InterviewStage stage = context.stage();

        CurrentQuestionVO question = generateNextQuestion(session, stage, null, false, 0);
        StartInterviewVO vo = new StartInterviewVO();
        vo.setId(session.getId());
        vo.setStatus(session.getStatus());
        vo.setCurrentStage(InterviewConvert.toStageVO(stage));
        vo.setCurrentQuestion(question);
        return vo;
    }

    private StartContext prepareStart(Long id) {
        InterviewSession session = getOwnedSession(id);
        if (!InterviewStatusEnum.NOT_STARTED.name().equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview has already started");
        }
        InterviewStage stage = firstStage(session.getId());
        stage.setStatus(InterviewStatusEnum.IN_PROGRESS.name());
        stageMapper.updateById(stage);

        session.setStatus(InterviewStatusEnum.WAITING_ANSWER.name());
        session.setStartTime(LocalDateTime.now());
        session.setCurrentStageId(stage.getId());
        session.setCurrentFollowUpCount(0);
        sessionMapper.updateById(session);
        return new StartContext(session, stage);
    }

    @Override
    public CurrentInterviewVO current(Long id) {
        InterviewSession session = getOwnedSession(id);
        CurrentInterviewVO vo = new CurrentInterviewVO();
        vo.setId(session.getId());
        vo.setTargetJobId(session.getTargetJobId());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setCurrentQuestionIndex(currentQuestionIndex(session));
        vo.setTotalQuestionCount(totalQuestionCount(session));
        vo.setAnsweredQuestionCount(safeInt(session.getAnsweredQuestionCount()));
        vo.setOverallProgress(overallProgress(session));
        vo.setCurrentStage(InterviewConvert.toStageVO(session.getCurrentStageId() == null
                ? null
                : stageMapper.selectById(session.getCurrentStageId())));
        vo.setCurrentQuestion(currentQuestion(session));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CurrentQuestionVO currentQuestion(Long id) {
        InterviewSession session = getOwnedSession(id);
        CurrentQuestionVO question = currentQuestion(session);
        if (question != null) {
            return question;
        }
        if (InterviewStatusEnum.COMPLETED.name().equals(session.getStatus())) {
            CurrentQuestionVO vo = new CurrentQuestionVO();
            vo.setSessionId(session.getId());
            vo.setInterviewStatus(session.getStatus());
            vo.setStageProgress("COMPLETED");
            vo.setCurrentQuestionIndex(totalQuestionCount(session));
            vo.setTotalQuestionCount(totalQuestionCount(session));
            vo.setOverallProgress(overallProgress(session));
            return vo;
        }
        InterviewStage stage = session.getCurrentStageId() == null
                ? firstStage(session.getId())
                : stageMapper.selectById(session.getCurrentStageId());
        if (stage == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Interview stage missing");
        }
        stage.setStatus(InterviewStatusEnum.IN_PROGRESS.name());
        stageMapper.updateById(stage);
        session.setCurrentStageId(stage.getId());
        session.setStatus(InterviewStatusEnum.WAITING_ANSWER.name());
        sessionMapper.updateById(session);
        return generateNextQuestion(session, stage, null, false, 0);
    }

    @Override
    public SubmitInterviewAnswerVO answer(Long id, SubmitInterviewAnswerDTO dto) {
        return answerInternal(id, dto, null, null);
    }

    @Override
    public SubmitInterviewAnswerVO answerForSse(Long id, SubmitInterviewAnswerDTO dto,
                                                Consumer<String> progressConsumer,
                                                Consumer<String> tokenConsumer) {
        return answerInternal(id, dto, progressConsumer, tokenConsumer);
    }

    private SubmitInterviewAnswerVO answerInternal(Long id, SubmitInterviewAnswerDTO dto,
                                                   Consumer<String> progressConsumer,
                                                   Consumer<String> tokenConsumer) {
        progress(progressConsumer, "VALIDATE_REQUEST");
        AnswerContext context = transactionTemplate.execute(status -> prepareAnswer(id, dto));
        InterviewSession session = context.session();
        InterviewStage stage = context.stage();
        InterviewMessage currentAiQuestion = context.currentAiQuestion();
        InnerQuestionVO question = context.question();
        InterviewMessage answerMessage = context.answerMessage();
        progress(progressConsumer, "LOAD_INTERVIEW");
        InterviewMessage rootAiQuestion = rootQuestionMessage(currentAiQuestion);
        String rootQuestionContent = firstText(rootAiQuestion == null ? null : rootAiQuestion.getQuestionContent(),
                rootAiQuestion == null ? null : rootAiQuestion.getContent(),
                currentAiQuestion.getQuestionContent(),
                question == null ? null : question.getContent());
        int maxFollowUp = stage == null || stage.getMaxFollowUpCount() == null ? MAX_FOLLOW_UP_COUNT : stage.getMaxFollowUpCount();

        progress(progressConsumer, "BUILD_PROMPT");
        EvaluateAnswerDTO evaluateDTO = new EvaluateAnswerDTO();
        evaluateDTO.setQuestionId(question == null ? null : question.getId());
        evaluateDTO.setQuestionTitle(question == null ? null : question.getTitle());
        evaluateDTO.setRootQuestionContent(rootQuestionContent);
        evaluateDTO.setCurrentQuestionContent(firstText(currentAiQuestion.getQuestionContent(),
                currentAiQuestion.getContent(),
                question == null ? null : question.getContent()));
        evaluateDTO.setQuestionContent(evaluateDTO.getCurrentQuestionContent());
        evaluateDTO.setReferenceAnswer(question == null ? null : question.getReferenceAnswer());
        evaluateDTO.setAnswerContent(dto.getAnswerContent());
        evaluateDTO.setFollowUpCount(session.getCurrentFollowUpCount());
        evaluateDTO.setMaxFollowUpCount(maxFollowUp);
        evaluateDTO.setStageType(stage == null ? null : stage.getStageType());
        evaluateDTO.setCurrentStage(stage == null ? null : stage.getStageName());
        evaluateDTO.setProjectContent(buildProjectContent(loadResume(session)));
        evaluateDTO.setIndustryContext(session.getIndustryContext());
        evaluateDTO.setHistorySummary(historySummary(session.getId()));
        progress(progressConsumer, "CALL_AI_REVIEW");
        EvaluateAnswerVO evaluation = tokenConsumer == null
                ? FeignResultUtils.unwrap(aiFeignClient.evaluate(evaluateDTO))
                : evaluateAnswerStream(evaluateDTO, tokenConsumer);

        NextActionEnum nextAction = decideNextAction(session, stage, evaluation);
        if (NextActionEnum.FOLLOW_UP.equals(nextAction) && Boolean.FALSE.equals(dto.getNeedFollowUp())) {
            nextAction = NextActionEnum.NEXT_QUESTION;
        }
        FollowUpPlan followUpPlan = null;
        if (NextActionEnum.FOLLOW_UP.equals(nextAction)) {
            String followUpQuestion = evaluation == null ? null : evaluation.getFollowUpQuestion();
            String followUpReason = evaluation == null ? null : evaluation.getFollowUpReason();
            Long followUpAiCallLogId = null;
            if (!isValidFollowUp(followUpQuestion)) {
                progress(progressConsumer, "GENERATE_FOLLOW_UP");
                GenerateFollowUpVO followUp = FeignResultUtils.unwrap(aiFeignClient.followUp(buildFollowUpDTO(
                        question, stage, evaluateDTO, dto.getAnswerContent(), evaluation)));
                followUpQuestion = followUp == null ? null : followUp.getFollowUpQuestion();
                followUpReason = followUp == null ? followUpReason : firstText(followUp.getReason(), followUpReason);
                followUpAiCallLogId = followUp == null ? null : followUp.getAiCallLogId();
            }
            if (evaluation != null) {
                evaluation.setFollowUpQuestion(followUpQuestion);
                evaluation.setFollowUpReason(followUpReason);
                evaluation.setFollowUpValid(isValidFollowUp(followUpQuestion));
            }
            followUpPlan = new FollowUpPlan(followUpQuestion, followUpReason, followUpAiCallLogId);
        }

        progress(progressConsumer, "SAVE_REVIEW");
        final NextActionEnum finalNextAction = nextAction;
        final FollowUpPlan finalFollowUpPlan = followUpPlan;
        AnswerPersistence persistence = transactionTemplate.execute(status -> saveAnswerEvaluation(
                session, stage, question, currentAiQuestion, dto, evaluation, finalNextAction, finalFollowUpPlan, progressConsumer));
        CurrentQuestionVO nextQuestion = persistence.nextQuestion();
        if (persistence.questionGenerationStage() != null) {
            nextQuestion = generateNextQuestion(session, persistence.questionGenerationStage(), null, false, 0);
        }

        SubmitInterviewAnswerVO vo = new SubmitInterviewAnswerVO();
        vo.setInterviewId(session.getId());
        vo.setQuestionId(question == null ? session.getCurrentQuestionId() : question.getId());
        vo.setAnswerId(answerMessage.getId());
        vo.setEvaluationMessageId(persistence.evaluationMessage().getId());
        vo.setFollowUpMessageId(persistence.followUpMessage() == null ? null : persistence.followUpMessage().getId());
        vo.setAiCallLogId(evaluation == null ? null : evaluation.getAiCallLogId());
        vo.setFollowUpAiCallLogId(persistence.followUpAiCallLogId());
        vo.setScore(evaluation == null ? null : evaluation.getScore());
        vo.setComment(evaluation == null ? null : evaluation.getComment());
        vo.setNextAction(persistence.nextAction().name());
        vo.setKnowledgePoints(evaluation == null ? null : evaluation.getKnowledgePoints());
        vo.setFollowUpQuestion(evaluation == null ? null : evaluation.getFollowUpQuestion());
        vo.setFollowUpReason(evaluation == null ? null : evaluation.getFollowUpReason());
        vo.setFollowUpValid(evaluation == null ? null : evaluation.getFollowUpValid());
        vo.setNextQuestion(nextQuestion);
        return vo;
    }

    private AnswerContext prepareAnswer(Long id, SubmitInterviewAnswerDTO dto) {
        InterviewSession session = getOwnedSession(id);
        if (!InterviewStatusEnum.WAITING_ANSWER.name().equals(session.getStatus())
                && !InterviewStatusEnum.IN_PROGRESS.name().equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview is not waiting for answer");
        }
        InterviewStage stage = stageMapper.selectById(session.getCurrentStageId());
        InterviewMessage currentAiQuestion = currentAiQuestionMessage(session);
        if (currentAiQuestion == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "No current question");
        }
        if (dto.getQuestionId() != null && !dto.getQuestionId().equals(currentAiQuestion.getQuestionId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question does not belong to current interview state");
        }
        InnerQuestionVO question = loadCurrentQuestion(session);
        InterviewMessage answerMessage = saveMessage(session, stage, question, "USER", "ANSWER", dto.getAnswerContent(), null, null,
                currentAiQuestion.getId(), false, session.getCurrentFollowUpCount(), null, null);
        session.setStatus(InterviewStatusEnum.AI_EVALUATING.name());
        sessionMapper.updateById(session);
        return new AnswerContext(session, stage, currentAiQuestion, question, answerMessage);
    }

    private AnswerPersistence saveAnswerEvaluation(InterviewSession session, InterviewStage stage, InnerQuestionVO question,
                                                   InterviewMessage currentAiQuestion, SubmitInterviewAnswerDTO dto,
                                                   EvaluateAnswerVO evaluation, NextActionEnum nextAction,
                                                   FollowUpPlan followUpPlan, Consumer<String> progressConsumer) {
        InterviewMessage evaluationMessage = saveMessage(session, stage, question, "AI", "EVALUATION", evaluation == null ? null : evaluation.getComment(),
                evaluation == null ? null : evaluation.getScore(), evaluation == null ? null : evaluation.getComment(),
                currentAiQuestion.getId(), false, session.getCurrentFollowUpCount(), null,
                evaluation == null ? null : evaluation.getKnowledgePoints());

        CurrentQuestionVO nextQuestion = null;
        InterviewMessage followUpMessage = null;
        Long followUpAiCallLogId = followUpPlan == null ? null : followUpPlan.aiCallLogId();
        InterviewStage questionGenerationStage = null;
        if (NextActionEnum.FOLLOW_UP.equals(nextAction)) {
            int nextFollowUpCount = safeInt(session.getCurrentFollowUpCount()) + 1;
            session.setCurrentFollowUpCount(nextFollowUpCount);
            progress(progressConsumer, "SAVE_FOLLOW_UP");
            followUpMessage = saveMessage(session, stage, question, "AI", "FOLLOW_UP",
                    followUpPlan == null ? null : followUpPlan.question(), null, null,
                    currentAiQuestion.getId(), true, nextFollowUpCount,
                    followUpPlan == null ? null : followUpPlan.reason(), evaluation == null ? null : evaluation.getKnowledgePoints());
            nextQuestion = toCurrentQuestionVO(session, stage, followUpMessage);
            session.setStatus(InterviewStatusEnum.WAITING_ANSWER.name());
        } else if (NextActionEnum.NEXT_QUESTION.equals(nextAction)) {
            markAnswered(session, stage);
            questionGenerationStage = stage;
            session.setStatus(InterviewStatusEnum.WAITING_ANSWER.name());
        } else if (NextActionEnum.NEXT_STAGE.equals(nextAction)) {
            markAnswered(session, stage);
            InterviewStage nextStage = nextStage(stage);
            if (nextStage == null) {
                nextAction = NextActionEnum.FINISH;
                session.setStatus(InterviewStatusEnum.IN_PROGRESS.name());
            } else {
                stage.setStatus(InterviewStatusEnum.COMPLETED.name());
                stageMapper.updateById(stage);
                nextStage.setStatus(InterviewStatusEnum.IN_PROGRESS.name());
                stageMapper.updateById(nextStage);
                session.setCurrentStageId(nextStage.getId());
                questionGenerationStage = nextStage;
                session.setStatus(InterviewStatusEnum.WAITING_ANSWER.name());
            }
        } else {
            markAnswered(session, stage);
            session.setStatus(InterviewStatusEnum.IN_PROGRESS.name());
        }
        sessionMapper.updateById(session);
        return new AnswerPersistence(evaluationMessage, followUpMessage, followUpAiCallLogId, nextAction,
                nextQuestion, questionGenerationStage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinishInterviewVO finish(Long id) {
        InterviewSession session = getOwnedSession(id);
        if (!hasScorableAnswers(session.getId())) {
            return prepareInsufficientReport(session);
        }
        InterviewReport existing = currentReport(session.getId());
        if (shouldReuseExistingReport(existing)) {
            return buildExistingReportResponse(session, existing);
        }
        PreparedReportGeneration prepared = prepareReportGeneration(session);
        submitReportGenerationAfterCommit(session.getId(), session.getUserId(),
                prepared.report(), prepared.response());
        return prepared.response();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinishInterviewVO retryReport(Long id) {
        InterviewSession session = getOwnedSession(id);
        if (!hasScorableAnswers(session.getId())) {
            return prepareInsufficientReport(session);
        }
        InterviewReport existing = currentReport(session.getId());
        if (shouldReuseExistingReport(existing)) {
            return buildExistingReportResponse(session, existing);
        }
        PreparedReportGeneration prepared = prepareReportGeneration(session);
        submitReportGenerationAfterCommit(session.getId(), session.getUserId(),
                prepared.report(), prepared.response());
        return prepared.response();
    }

    @Override
    public PageResult<InterviewListVO> list(Long pageNo, Long pageSize) {
        long actualPageNo = pageNo == null || pageNo < 1 ? 1L : pageNo;
        long actualPageSize = pageSize == null || pageSize < 1 ? 10L : Math.min(pageSize, 100L);
        Page<InterviewSession> page = sessionMapper.selectPage(Page.of(actualPageNo, actualPageSize),
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
        vo.setTargetJobId(session.getTargetJobId());
        vo.setSkillProfileId(session.getSkillProfileId());
        vo.setMatchReportId(session.getMatchReportId());
        vo.setTitle(session.getTitle());
        vo.setMode(session.getMode());
        vo.setTargetPosition(session.getTargetPosition());
        vo.setExperienceLevel(session.getExperienceLevel());
        vo.setIndustryTemplateId(session.getIndustryTemplateId());
        vo.setIndustryDirection(session.getIndustryDirection());
        vo.setIndustryContext(session.getIndustryContext());
        vo.setDifficulty(session.getDifficulty());
        vo.setInterviewerStyle(session.getInterviewerStyle());
        vo.setBasedOnResume(session.getBasedOnResume());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setStages(stages(session.getId()).stream().map(InterviewConvert::toStageVO).toList());
        vo.setMessages(messageEntities(session.getId()).stream().map(InterviewConvert::toMessageVO).toList());
        return vo;
    }

    @Override
    public List<InterviewMessageVO> messages(Long id) {
        InterviewSession session = getOwnedSession(id);
        return messageEntities(session.getId()).stream().map(InterviewConvert::toMessageVO).toList();
    }

    @Override
    public InterviewReportVO report(Long id) {
        InterviewSession session = getOwnedSession(id);
        InterviewReport report = currentReport(session.getId());
        if (report == null) {
            if (ReportStatusEnum.GENERATING.name().equals(session.getReportStatus())) {
                InterviewReport generating = new InterviewReport();
                generating.setSessionId(session.getId());
                generating.setUserId(session.getUserId());
                generating.setStatus(ReportStatusEnum.GENERATING.name());
                generating.setSummary("面试报告正在生成中，请稍后刷新。");
                return toReportVO(generating, session);
            }
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Report not generated");
        }
        List<InterviewMessage> messages = messageEntities(session.getId());
        if (!hasScorableAnswers(messages)) {
            report = markReportSampleInsufficient(session, report);
        } else if (ReportStatusEnum.GENERATED.name().equals(report.getStatus())
                && !hasExpectedQaReviews(report.getQaReview(), countScorableAnswers(messages))) {
            applyFallbackReportContent(report, null, messages, countScorableAnswers(messages));
            saveReport(report);
            session.setReportStatus(ReportStatusEnum.GENERATED.name());
            session.setTotalScore(report.getTotalScore());
            session.setFailureReason(report.getFailureReason());
            sessionMapper.updateById(session);
        }
        if (normalizeReportContent(report)) {
            session.setReportStatus(ReportStatusEnum.FAILED.name());
            session.setTotalScore(null);
            session.setFailureReason(report.getFailureReason());
            sessionMapper.updateById(session);
        }
        attachFallbackQaReview(report, messages);
        return toReportVO(report, session);
    }

    @Override
    public InterviewReportGenerateResultVO generateReportForSse(Long id, Long reportId, Boolean forceRegenerate,
                                                                Consumer<String> progressConsumer) {
        InterviewSession session = getOwnedSession(id);
        InterviewReport existing = currentReport(session.getId());
        if (reportId != null && (existing == null || !reportId.equals(existing.getId()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试报告不存在或已不可用");
        }
        boolean force = Boolean.TRUE.equals(forceRegenerate);
        if (!force && existing != null && ReportStatusEnum.GENERATED.name().equals(existing.getStatus())) {
            progress(progressConsumer, "LOAD_INTERVIEW");
            completeAgentInterviewTask(session, existing);
            return buildReportGenerateResult(session, null, existing);
        }
        if (!force && existing != null && ReportStatusEnum.GENERATING.name().equals(existing.getStatus())) {
            progress(progressConsumer, "LOAD_INTERVIEW");
            return buildReportGenerateResult(session, null, existing);
        }

        progress(progressConsumer, "LOAD_INTERVIEW");
        session.setReportStatus(ReportStatusEnum.GENERATING.name());
        session.setStatus(InterviewStatusEnum.REPORT_GENERATING.name());
        session.setEndTime(session.getEndTime() == null ? LocalDateTime.now() : session.getEndTime());
        session.setFailureReason(null);
        sessionMapper.updateById(session);

        InterviewReport report = force ? new InterviewReport() : (existing == null ? new InterviewReport() : existing);
        if (report.getId() == null) {
            report.setSessionId(session.getId());
            report.setUserId(session.getUserId());
        }
        String generationToken = nextGenerationToken();
        report.setStatus(ReportStatusEnum.GENERATING.name());
        report.setFailureReason(null);
        report.setGenerationToken(generationToken);
        saveReport(report);

        Long aiCallLogId = null;
        try {
            progress(progressConsumer, "LOAD_ANSWERS");
            List<InterviewMessage> messages = messageEntities(session.getId());
            if (!hasScorableAnswers(messages)) {
                progress(progressConsumer, "SAVE_REPORT");
                markReportSampleInsufficient(session, report);
                return buildReportGenerateResult(session, null, report);
            }
            progress(progressConsumer, "BUILD_PROMPT");
            GenerateReportDTO reportDTO = buildReportDTO(session, messages);
            progress(progressConsumer, "CALL_AI");
            GenerateReportVO aiReport = FeignResultUtils.unwrap(aiFeignClient.report(reportDTO));
            aiCallLogId = aiReport == null ? null : aiReport.getAiCallLogId();

            if (!isCurrentReportAttempt(session.getId(), report.getId(), generationToken)) {
                log.info("Skip stale SSE report success write-back, sessionId={}, reportId={}",
                        session.getId(), report.getId());
                InterviewReport latest = currentReport(session.getId());
                return buildReportGenerateResult(session, aiCallLogId, latest == null ? report : latest);
            }

            progress(progressConsumer, "SAVE_REPORT");
            report.setStatus(ReportStatusEnum.GENERATED.name());
            applyReportContent(report, aiReport, messages);
            saveReport(report);

            if (isCurrentReportAttempt(session.getId(), report.getId(), generationToken)) {
                session.setStatus(InterviewStatusEnum.COMPLETED.name());
                if (ReportStatusEnum.GENERATED.name().equals(report.getStatus())) {
                    session.setReportStatus(ReportStatusEnum.GENERATED.name());
                    session.setTotalScore(report.getTotalScore());
                    session.setFailureReason(isFallbackReport(report) ? report.getFailureReason() : null);
                } else {
                    session.setReportStatus(ReportStatusEnum.FAILED.name());
                    session.setTotalScore(null);
                    session.setFailureReason(report.getFailureReason());
                }
                session.setEndTime(LocalDateTime.now());
                sessionMapper.updateById(session);
            }
            if (isCurrentReportAttempt(session.getId(), report.getId(), generationToken)
                    && ReportStatusEnum.GENERATED.name().equals(report.getStatus())) {
                syncInterviewSearchAfterCommit(session.getId(), session.getUserId());
                completeAgentInterviewTask(session, report);
            }
            return buildReportGenerateResult(session, aiCallLogId, report);
        } catch (RuntimeException ex) {
            log.warn("Interview report generation failed, sessionId={}", session.getId(), ex);
            if (!isCurrentReportAttempt(session.getId(), report.getId(), generationToken)) {
                log.info("Skip stale SSE report failure write-back, sessionId={}, reportId={}",
                        session.getId(), report.getId());
                InterviewReport latest = currentReport(session.getId());
                return buildReportGenerateResult(session, aiCallLogId, latest == null ? report : latest);
            }
            report.setStatus(ReportStatusEnum.FAILED.name());
            report.setTotalScore(null);
            report.setFailureReason(REPORT_GENERATION_FAILED_MESSAGE);
            saveReport(report);

            if (isCurrentReportAttempt(session.getId(), report.getId(), generationToken)) {
                session.setStatus(InterviewStatusEnum.FAILED.name());
                session.setReportStatus(ReportStatusEnum.FAILED.name());
                session.setTotalScore(null);
                session.setFailureReason(REPORT_GENERATION_FAILED_MESSAGE);
                sessionMapper.updateById(session);
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "面试报告生成失败，请稍后重试");
        }
    }

    private GenerateReportDTO buildReportDTO(InterviewSession session, List<InterviewMessage> messages) {
        InnerResumeDetailVO resume = loadResume(session);
        GenerateReportDTO reportDTO = new GenerateReportDTO();
        reportDTO.setInterviewId(session.getId());
        reportDTO.setUserId(session.getUserId());
        reportDTO.setTargetJobId(session.getTargetJobId());
        reportDTO.setSkillProfileId(session.getSkillProfileId());
        reportDTO.setMatchReportId(session.getMatchReportId());
        reportDTO.setSkillGapContext(skillGapContext(session));
        reportDTO.setMode(session.getMode());
        reportDTO.setTargetPosition(session.getTargetPosition());
        reportDTO.setExperienceLevel(session.getExperienceLevel());
        reportDTO.setIndustryDirection(session.getIndustryDirection());
        reportDTO.setIndustryContext(session.getIndustryContext());
        reportDTO.setDifficulty(session.getDifficulty());
        reportDTO.setResumeContent(resume == null ? null : resume.getSummary());
        reportDTO.setProjectContent(buildProjectContent(resume));
        reportDTO.setMessages(messages.stream()
                .map(message -> firstText(message.getQuestionContent(), message.getUserAnswer(),
                        message.getAiComment(), message.getContent()))
                .filter(StringUtils::hasText)
                .toList());
        return reportDTO;
    }

    private InterviewReportGenerateResultVO buildReportGenerateResult(InterviewSession session, Long aiCallLogId,
                                                                      InterviewReport report) {
        InterviewReportGenerateResultVO result = new InterviewReportGenerateResultVO();
        result.setInterviewId(session == null ? null : session.getId());
        result.setReportId(report == null ? null : report.getId());
        result.setAiCallLogId(aiCallLogId);
        result.setResult(toReportVO(report, session));
        return result;
    }

    private InterviewReportVO toReportVO(InterviewReport report, InterviewSession session) {
        return enrichReportWithJdContext(InterviewConvert.toReportVO(report), session);
    }

    private InterviewReportVO enrichReportWithJdContext(InterviewReportVO vo, InterviewSession session) {
        if (vo == null || session == null) {
            return vo;
        }
        vo.setTargetJobId(session.getTargetJobId());
        vo.setSkillProfileId(session.getSkillProfileId());
        vo.setMatchReportId(session.getMatchReportId());
        vo.setTargetJobTitle(session.getTargetPosition());
        vo.setJdEvidenceSummary(jdEvidenceSummary(session));
        vo.setMissingSkills(List.of());

        InnerSkillProfileVO profile = resolveReportSkillProfile(session);
        if (profile != null) {
            if (vo.getTargetJobId() == null) {
                vo.setTargetJobId(profile.getTargetJobId());
            }
            if (vo.getSkillProfileId() == null) {
                vo.setSkillProfileId(profile.getProfileId());
            }
            if (vo.getMatchReportId() == null) {
                vo.setMatchReportId(profile.getMatchReportId());
            }
            if (StringUtils.hasText(profile.getTargetJobTitle())) {
                vo.setTargetJobTitle(profile.getTargetJobTitle());
            }
            if (StringUtils.hasText(profile.getTargetCompanyName())) {
                vo.setTargetCompanyName(profile.getTargetCompanyName());
            }
            vo.setMissingSkills(missingSkills(profile));
        } else {
            InnerTargetJobVO targetJob = resolveReportTargetJob(session);
            if (targetJob != null) {
                if (StringUtils.hasText(targetJob.getJobTitle())) {
                    vo.setTargetJobTitle(targetJob.getJobTitle());
                }
                if (StringUtils.hasText(targetJob.getCompanyName())) {
                    vo.setTargetCompanyName(targetJob.getCompanyName());
                }
            }
        }
        return vo;
    }

    private InnerSkillProfileVO resolveReportSkillProfile(InterviewSession session) {
        try {
            if (session.getSkillProfileId() != null) {
                return FeignResultUtils.unwrap(resumeFeignClient.getSkillProfile(session.getSkillProfileId()));
            }
            if (session.getMatchReportId() != null) {
                return FeignResultUtils.unwrap(resumeFeignClient.getSuccessSkillProfileByMatchReport(session.getMatchReportId()));
            }
        } catch (RuntimeException ex) {
            log.info("Interview report skill profile context unavailable, sessionId={}, reason={}",
                    session.getId(), ex.getMessage());
        }
        return null;
    }

    private InnerTargetJobVO resolveReportTargetJob(InterviewSession session) {
        if (session.getTargetJobId() == null || session.getUserId() == null) {
            return null;
        }
        try {
            return FeignResultUtils.unwrap(resumeFeignClient.getTargetJob(session.getUserId(), session.getTargetJobId()));
        } catch (RuntimeException ex) {
            log.info("Interview report target job context unavailable, sessionId={}, reason={}",
                    session.getId(), ex.getMessage());
            return null;
        }
    }

    private String jdEvidenceSummary(InterviewSession session) {
        List<String> items = new ArrayList<>();
        if (session.getSkillProfileId() != null) {
            items.add("Skill profile #" + session.getSkillProfileId());
        }
        if (session.getMatchReportId() != null) {
            items.add("match report #" + session.getMatchReportId());
        }
        if (items.isEmpty() && session.getTargetJobId() != null) {
            items.add("target job #" + session.getTargetJobId());
        }
        return items.isEmpty() ? null : String.join(", ", items);
    }

    private List<InterviewReportMissingSkillVO> missingSkills(InnerSkillProfileVO profile) {
        if (profile.getGapItems() == null || profile.getGapItems().isEmpty()) {
            return List.of();
        }
        List<InnerSkillGapItemVO> gapItems = new ArrayList<>(profile.getGapItems());
        gapItems.sort((left, right) -> Integer.compare(safeGapPriority(left), safeGapPriority(right)));
        return gapItems.stream()
                .filter(item -> item != null && StringUtils.hasText(item.getSkillName()))
                .limit(5)
                .map(this::toMissingSkill)
                .toList();
    }

    private InterviewReportMissingSkillVO toMissingSkill(InnerSkillGapItemVO item) {
        InterviewReportMissingSkillVO vo = new InterviewReportMissingSkillVO();
        vo.setId(item.getId());
        vo.setSkillName(item.getSkillName());
        vo.setSeverity(item.getSeverity());
        vo.setGapDescription(item.getGapDescription());
        vo.setRecommendedActions(parseRecommendedActions(item.getRecommendedActionsJson()));
        vo.setPriority(item.getPriority());
        vo.setSourceType(item.getSourceType());
        vo.setSourceBizId(item.getSourceBizId());
        return vo;
    }

    private int safeGapPriority(InnerSkillGapItemVO item) {
        return item == null || item.getPriority() == null ? Integer.MAX_VALUE : item.getPriority();
    }

    private List<String> parseRecommendedActions(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node != null && node.isArray()) {
                List<String> actions = new ArrayList<>();
                node.forEach(item -> {
                    if (item != null && item.isTextual() && StringUtils.hasText(item.asText())) {
                        actions.add(item.asText());
                    }
                });
                return actions;
            }
            if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
                return List.of(node.asText());
            }
        } catch (Exception ignored) {
            return List.of(value);
        }
        return List.of();
    }

    private void progress(Consumer<String> progressConsumer, String stage) {
        if (progressConsumer != null) {
            progressConsumer.accept(stage);
        }
    }

    private EvaluateAnswerVO evaluateAnswerStream(EvaluateAnswerDTO dto, Consumer<String> tokenConsumer) {
        Response response = aiFeignClient.evaluateStream(dto);
        if (response == null || response.status() < 200 || response.status() >= 300 || response.body() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI answer evaluation stream unavailable");
        }
        EvaluateAnswerVO result = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                response.body().asInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder block = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    result = handleEvaluateStreamBlock(block.toString(), tokenConsumer, result);
                    block.setLength(0);
                } else {
                    block.append(line).append('\n');
                }
            }
            if (block.length() > 0) {
                result = handleEvaluateStreamBlock(block.toString(), tokenConsumer, result);
            }
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI answer evaluation stream failed");
        }
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI answer evaluation stream returned no result");
        }
        return result;
    }

    private EvaluateAnswerVO handleEvaluateStreamBlock(String block, Consumer<String> tokenConsumer,
                                                       EvaluateAnswerVO currentResult) throws Exception {
        if (!StringUtils.hasText(block)) {
            return currentResult;
        }
        String event = "message";
        StringBuilder dataText = new StringBuilder();
        for (String line : block.split("\\R")) {
            if (line.startsWith("event:")) {
                event = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                if (dataText.length() > 0) {
                    dataText.append('\n');
                }
                dataText.append(line.substring("data:".length()).stripLeading());
            }
        }
        if (!StringUtils.hasText(dataText.toString())) {
            return currentResult;
        }
        JsonNode node = objectMapper.readTree(dataText.toString());
        String type = firstText(node.path("type").asText(null), event);
        if ("token".equals(type) || "delta".equals(type)) {
            String token = firstText(node.path("content").asText(null),
                    node.path("delta").asText(null),
                    node.path("message").asText(null));
            if (StringUtils.hasText(token) && tokenConsumer != null) {
                tokenConsumer.accept(token);
            }
            return currentResult;
        }
        if ("result".equals(type)) {
            JsonNode resultNode = node.path("result");
            return resultNode.isMissingNode() || resultNode.isNull()
                    ? currentResult
                    : objectMapper.treeToValue(resultNode, EvaluateAnswerVO.class);
        }
        if ("error".equals(type)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI answer evaluation stream failed");
        }
        return currentResult;
    }

    private PreparedReportGeneration prepareReportGeneration(InterviewSession session) {
        session.setReportStatus(ReportStatusEnum.GENERATING.name());
        session.setStatus(InterviewStatusEnum.REPORT_GENERATING.name());
        session.setEndTime(LocalDateTime.now());
        session.setFailureReason(null);
        sessionMapper.updateById(session);

        InterviewReport report = currentReport(session.getId());
        if (report == null) {
            report = new InterviewReport();
            report.setSessionId(session.getId());
            report.setUserId(session.getUserId());
        }
        report.setUserId(session.getUserId());
        report.setStatus(ReportStatusEnum.GENERATING.name());
        report.setFailureReason(null);
        report.setGenerationToken(nextGenerationToken());
        saveReport(report);

        FinishInterviewVO vo = new FinishInterviewVO();
        vo.setId(session.getId());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setReport(toReportVO(report, session));
        return new PreparedReportGeneration(report, vo);
    }

    private FinishInterviewVO prepareInsufficientReport(InterviewSession session) {
        InterviewReport report = currentReport(session.getId());
        if (report == null) {
            report = new InterviewReport();
            report.setSessionId(session.getId());
            report.setUserId(session.getUserId());
        }
        markReportSampleInsufficient(session, report);

        FinishInterviewVO vo = new FinishInterviewVO();
        vo.setId(session.getId());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setReport(toReportVO(report, session));
        return vo;
    }

    private boolean shouldReuseExistingReport(InterviewReport existing) {
        return existing != null
                && (ReportStatusEnum.GENERATED.name().equals(existing.getStatus())
                || ReportStatusEnum.GENERATING.name().equals(existing.getStatus()));
    }

    private FinishInterviewVO buildExistingReportResponse(InterviewSession session, InterviewReport existing) {
        FinishInterviewVO vo = new FinishInterviewVO();
        vo.setId(session.getId());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setReport(toReportVO(existing, session));
        return vo;
    }

    private void submitReportGenerationAfterCommit(Long sessionId, Long userId, InterviewReport report, FinishInterviewVO vo) {
        Long reportId = report == null ? null : report.getId();
        String generationToken = report == null ? null : report.getGenerationToken();
        Runnable action = () -> {
            MqDispatchReceipt receipt = interviewMqDispatcher.dispatchReportWithReceipt(
                    sessionId, userId, reportId, generationToken);
            if (receipt != null) {
                attachReportDispatchReceipt(vo, receipt);
                return;
            }
            log.warn("面试报告 MQ 投递不可用，回退本地异步生成 sessionId={} reportId={}",
                    sessionId, reportId);
            reportAsyncService.generateReportAsync(sessionId, reportId, generationToken);
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void attachReportDispatchReceipt(FinishInterviewVO vo, MqDispatchReceipt receipt) {
        if (vo == null || receipt == null) {
            return;
        }
        vo.setAsyncMessageId(receipt.getMessageId());
        vo.setAsyncTraceId(receipt.getTraceId());
        vo.setAsyncBizType(receipt.getBizType());
        vo.setAsyncBizId(receipt.getBizId());
        vo.setAsyncSendStatus(receipt.getSendStatus());
    }

    private void syncInterviewSearchAfterCommit(Long sessionId, Long userId) {
        String op = "UPSERT";
        Runnable action = () -> {
            if (!interviewMqDispatcher.dispatchInterviewSearchUpsert(sessionId, userId)) {
                log.warn("Interview after-commit sync returned false syncType=interview_search_sync sessionId={} op={}",
                        sessionId, op);
            }
        };
        Runnable safeAction = () -> runAfterCommitSafely("interview_search_sync", sessionId, op, action);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeAction.run();
            }
        });
    }

    private void runAfterCommitSafely(String syncType, Long sessionId, String op, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("Interview after-commit sync failed syncType={} sessionId={} op={} reason={}",
                    syncType, sessionId, op, ex.getMessage(), ex);
        }
    }

    private FinishInterviewVO finishAndGenerateReport(InterviewSession session) {
        session.setReportStatus(ReportStatusEnum.GENERATING.name());
        session.setStatus(InterviewStatusEnum.REPORT_GENERATING.name());
        sessionMapper.updateById(session);
        InterviewReport report = currentReport(session.getId());
        if (report == null) {
            report = new InterviewReport();
            report.setSessionId(session.getId());
        }
        report.setUserId(session.getUserId());
        report.setStatus(ReportStatusEnum.GENERATING.name());
        saveReport(report);
        try {
            InnerResumeDetailVO resume = loadResume(session);
            GenerateReportDTO reportDTO = new GenerateReportDTO();
            reportDTO.setInterviewId(session.getId());
            reportDTO.setMode(session.getMode());
            reportDTO.setTargetPosition(session.getTargetPosition());
            reportDTO.setExperienceLevel(session.getExperienceLevel());
            reportDTO.setIndustryDirection(session.getIndustryDirection());
            reportDTO.setIndustryContext(session.getIndustryContext());
            reportDTO.setDifficulty(session.getDifficulty());
            reportDTO.setResumeContent(resume == null ? null : resume.getSummary());
            reportDTO.setProjectContent(buildProjectContent(resume));
            List<InterviewMessage> messages = messageEntities(session.getId());
            if (!hasScorableAnswers(messages)) {
                markReportSampleInsufficient(session, report);
                saveReport(report);
                sessionMapper.updateById(session);
                FinishInterviewVO vo = new FinishInterviewVO();
                vo.setId(session.getId());
                vo.setStatus(session.getStatus());
                vo.setReportStatus(session.getReportStatus());
                vo.setReport(toReportVO(report, session));
                return vo;
            }
            reportDTO.setMessages(messages.stream().map(InterviewMessage::getContent).toList());
            GenerateReportVO aiReport = FeignResultUtils.unwrap(aiFeignClient.report(reportDTO));
            report.setStatus(ReportStatusEnum.GENERATED.name());
            applyReportContent(report, aiReport, messages);
            session.setStatus(InterviewStatusEnum.COMPLETED.name());
            if (ReportStatusEnum.GENERATED.name().equals(report.getStatus())) {
                session.setReportStatus(ReportStatusEnum.GENERATED.name());
                session.setTotalScore(report.getTotalScore());
                session.setFailureReason(isFallbackReport(report) ? report.getFailureReason() : null);
            } else {
                session.setReportStatus(ReportStatusEnum.FAILED.name());
                session.setTotalScore(null);
                session.setFailureReason(report.getFailureReason());
            }
            session.setEndTime(LocalDateTime.now());
        } catch (RuntimeException ex) {
            log.warn("Interview finish report generation failed, sessionId={}", session.getId(), ex);
            report.setStatus(ReportStatusEnum.FAILED.name());
            report.setFailureReason(REPORT_GENERATION_FAILED_MESSAGE);
            session.setStatus(InterviewStatusEnum.FAILED.name());
            session.setReportStatus(ReportStatusEnum.FAILED.name());
            session.setFailureReason(REPORT_GENERATION_FAILED_MESSAGE);
        }
        saveReport(report);
        sessionMapper.updateById(session);
        if (ReportStatusEnum.GENERATED.name().equals(report.getStatus())) {
            syncInterviewSearchAfterCommit(session.getId(), session.getUserId());
            completeAgentInterviewTask(session, report);
        }

        FinishInterviewVO vo = new FinishInterviewVO();
        vo.setId(session.getId());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setReport(toReportVO(report, session));
        return vo;
    }

    private CurrentQuestionVO generateNextQuestion(InterviewSession session, InterviewStage stage,
                                                   Long parentMessageId, boolean followUp, int followUpCount) {
        InnerSelectQuestionDTO selectDTO = new InnerSelectQuestionDTO();
        selectDTO.setMode(session.getMode());
        selectDTO.setStageType(stage.getStageType());
        selectDTO.setDifficulty(session.getDifficulty());
        selectDTO.setExperienceLevel(session.getExperienceLevel());
        selectDTO.setExcludeGroupIds(usedGroupIds(session.getId()));
        InnerQuestionVO question = FeignResultUtils.unwrap(questionFeignClient.select(selectDTO));
        if (question == null || question.getId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "No persisted interview question is available for this stage");
        }

        InnerResumeDetailVO resume = loadResume(session);
        GenerateInterviewQuestionDTO aiDTO = new GenerateInterviewQuestionDTO();
        aiDTO.setMode(session.getMode());
        aiDTO.setTargetJobId(session.getTargetJobId());
        aiDTO.setSkillProfileId(session.getSkillProfileId());
        aiDTO.setMatchReportId(session.getMatchReportId());
        aiDTO.setSkillGapContext(skillGapContext(session));
        aiDTO.setStageType(stage.getStageType());
        aiDTO.setCurrentStage(stage.getStageName());
        aiDTO.setFocusPoints(stage.getFocusPoints());
        aiDTO.setTargetPosition(session.getTargetPosition());
        aiDTO.setExperienceLevel(session.getExperienceLevel());
        aiDTO.setIndustryDirection(session.getIndustryDirection());
        aiDTO.setIndustryContext(session.getIndustryContext());
        aiDTO.setDifficulty(session.getDifficulty());
        aiDTO.setInterviewerStyle(session.getInterviewerStyle());
        aiDTO.setQuestionId(question == null ? null : question.getId());
        aiDTO.setQuestionTitle(question == null ? null : question.getTitle());
        aiDTO.setQuestionContent(question == null ? null : question.getContent());
        aiDTO.setResumeSummary(resume == null ? null : resume.getSummary());
        aiDTO.setResumeContent(resume == null ? null : resume.getSummary());
        aiDTO.setProjectContent(buildProjectContent(resume));
        aiDTO.setHistorySummary(historySummary(session.getId()));
        GenerateInterviewQuestionVO aiQuestion = FeignResultUtils.unwrap(aiFeignClient.generateQuestion(aiDTO));
        if (!StringUtils.hasText(aiQuestion == null ? null : aiQuestion.getQuestionContent())
                && !StringUtils.hasText(aiQuestion == null ? null : aiQuestion.getQuestionText())
                && !StringUtils.hasText(question == null ? null : question.getContent())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI question generation returned empty content");
        }
        String questionContent = firstText(aiQuestion == null ? null : aiQuestion.getQuestionContent(),
                aiQuestion == null ? null : aiQuestion.getQuestionText(),
                question == null ? null : question.getContent(),
                "请结合当前面试阶段说明你的理解和项目实践。");

        if (!StringUtils.hasText(questionContent)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI question generation returned empty content");
        }
        return transactionTemplate.execute(status -> saveGeneratedQuestion(session, stage, question,
                questionContent, parentMessageId, followUp, followUpCount));
    }

    private CurrentQuestionVO saveGeneratedQuestion(InterviewSession session, InterviewStage stage, InnerQuestionVO question,
                                                    String questionContent, Long parentMessageId,
                                                    boolean followUp, int followUpCount) {
        session.setCurrentQuestionId(question.getId());
        session.setCurrentQuestionGroupId(question.getGroupId());
        InterviewMessage message = saveMessage(session, stage, question, "AI", followUp ? "FOLLOW_UP" : "QUESTION",
                questionContent, null, null, parentMessageId, followUp, followUpCount, null, null);
        return toCurrentQuestionVO(session, stage, message);
    }

    private NextActionEnum decideNextAction(InterviewSession session, InterviewStage stage, EvaluateAnswerVO evaluation) {
        if (safeInt(session.getAnsweredQuestionCount()) + 1 >= safeInt(session.getMaxQuestionCount())) {
            return NextActionEnum.FINISH;
        }
        int maxFollowUp = stage == null || stage.getMaxFollowUpCount() == null ? MAX_FOLLOW_UP_COUNT : stage.getMaxFollowUpCount();
        if (evaluation != null && NextActionEnum.FOLLOW_UP.name().equals(evaluation.getNextAction())
                && safeInt(session.getCurrentFollowUpCount()) < maxFollowUp) {
            return NextActionEnum.FOLLOW_UP;
        }
        int expectedQuestionCount = stage == null || stage.getExpectedQuestionCount() == null
                ? 1
                : Math.max(0, stage.getExpectedQuestionCount());
        if (expectedQuestionCount <= 0 || currentStageMainQuestionCount(session.getId(), stage.getId()) >= expectedQuestionCount) {
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
        if (InterviewModeEnum.TECHNICAL_BASIC.name().equals(session.getMode())) {
            addStage(stages, session, "JAVA_BASIC", "Java 基础", 1, "Java 基础语法、面向对象、常见集合");
            addStage(stages, session, "CONCURRENCY", "集合与并发", 2, "集合源码、线程池、并发工具类");
            addStage(stages, session, "JVM", "JVM", 3, "内存模型、GC、类加载");
            addStage(stages, session, "SPRING", "Spring / Spring Boot", 4, "IOC、AOP、事务、启动流程");
            addStage(stages, session, "MYSQL", "MySQL", 5, "索引、事务、锁、SQL 优化");
            addStage(stages, session, "REDIS", "Redis", 6, "缓存、数据结构、一致性问题");
        } else if (InterviewModeEnum.PROJECT_DEEP_DIVE.name().equals(session.getMode())) {
            addStage(stages, session, "PROJECT_BACKGROUND", "项目背景", 1, "业务背景、目标用户、核心价值");
            addStage(stages, session, "PROJECT_ROLE", "个人职责", 2, "职责边界、协作方式、交付内容");
            addStage(stages, session, "TECH_SELECTION", "技术选型", 3, "技术栈选择、取舍和风险");
            addStage(stages, session, "CORE_FLOW", "核心流程", 4, "核心链路、数据流和异常处理");
            addStage(stages, session, "DB_CACHE", "数据库与缓存", 5, "表设计、索引、缓存策略");
            addStage(stages, session, "OPTIMIZATION", "难点与优化", 6, "性能瓶颈、优化结果、量化指标");
            addStage(stages, session, "SCALABILITY", "扩展性问题", 7, "扩展、容错、可维护性");
        } else {
            addStage(stages, session, "OPENING", "开场与自我介绍", 1, "个人背景、岗位匹配度");
            addStage(stages, session, "JAVA_BASIC", "Java 基础", 2, "Java 核心知识点");
            addStage(stages, session, "DATABASE", "数据库", 3, "MySQL 事务、索引和 SQL 优化");
            addStage(stages, session, "CACHE_MQ", "缓存与中间件", 4, "Redis、缓存一致性和常见中间件设计");
            addStage(stages, session, "FRAMEWORK", "框架基础", 5, "Spring、Spring Boot 和微服务基础");
            addStage(stages, session, "PROJECT", "项目深挖", 6, "项目背景、职责、难点和优化");
            addStage(stages, session, "SCENARIO", "场景设计", 7, "业务建模、容量和异常场景");
            addStage(stages, session, "SUMMARY", "总结报告", 8, "面试总结和改进建议");
        }
        rebalanceExpectedQuestionCounts(stages, session.getMaxQuestionCount());
        for (InterviewStage stage : stages) {
            stageMapper.insert(stage);
        }
        return stages;
    }

    private void rebalanceExpectedQuestionCounts(List<InterviewStage> stages, int totalQuestionCount) {
        if (stages == null || stages.isEmpty()) {
            return;
        }
        int remaining = normalizeQuestionCount(totalQuestionCount);
        for (InterviewStage stage : stages) {
            stage.setExpectedQuestionCount(0);
        }
        int index = 0;
        while (remaining > 0) {
            InterviewStage stage = stages.get(index % stages.size());
            stage.setExpectedQuestionCount(safeInt(stage.getExpectedQuestionCount()) + 1);
            remaining--;
            index++;
        }
    }

    private void addStage(List<InterviewStage> stages, InterviewSession session, String type, String name, int sort, String focusPoints) {
        stages.add(newStage(session, type, name, sort, focusPoints));
    }

    private InterviewStage newStage(InterviewSession session, String type, String name, int sort, String focusPoints) {
        InterviewStage stage = new InterviewStage();
        stage.setSessionId(session.getId());
        stage.setStageType(type);
        stage.setStageName(name);
        stage.setSort(sort);
        stage.setStageOrder(sort);
        stage.setExpectedQuestionCount(expectedQuestionCount(type));
        stage.setAskedQuestionCount(0);
        stage.setFocusPoints(focusPoints);
        stage.setBasedOnResume(Boolean.TRUE.equals(session.getBasedOnResume()) || type.startsWith("PROJECT") || "PROJECT".equals(type));
        stage.setAllowFollowUp(true);
        stage.setMaxFollowUpCount(MAX_FOLLOW_UP_COUNT);
        stage.setStatus(InterviewStatusEnum.NOT_STARTED.name());
        stage.setScore(0);
        return stage;
    }

    private int expectedQuestionCount(String stageType) {
        return switch (stageType) {
            case "OPENING", "SUMMARY", "JVM", "SCENARIO",
                    "PROJECT_BACKGROUND", "PROJECT_ROLE", "TECH_SELECTION", "CORE_FLOW", "DB_CACHE", "OPTIMIZATION", "SCALABILITY" -> 1;
            case "JAVA_BASIC", "CONCURRENCY", "MYSQL", "DATABASE", "REDIS", "CACHE_MQ", "PROJECT" -> 2;
            case "SPRING", "FRAMEWORK" -> 1;
            default -> 1;
        };
    }

    private InterviewMessage saveMessage(InterviewSession session, InterviewStage stage, InnerQuestionVO question,
                                         String role, String type, String content, Integer score, String comment,
                                         Long parentMessageId, Boolean isFollowUp, Integer followUpCount,
                                         String followUpReason, String knowledgePoints) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(session.getId());
        message.setStageId(stage == null ? null : stage.getId());
        message.setQuestionId(question == null ? null : question.getId());
        message.setQuestionGroupId(question == null ? null : question.getGroupId());
        message.setParentMessageId(parentMessageId);
        message.setRole(role);
        message.setMessageType(type);
        message.setContent(content);
        if ("QUESTION".equals(type) || "FOLLOW_UP".equals(type)) {
            message.setQuestionContent(content);
        }
        if ("ANSWER".equals(type)) {
            message.setUserAnswer(content);
        }
        if ("EVALUATION".equals(type)) {
            message.setAiComment(comment);
            message.setAiScore(score);
        }
        message.setIsFollowUp(Boolean.TRUE.equals(isFollowUp));
        message.setFollowUpCount(followUpCount == null ? 0 : followUpCount);
        message.setFollowUpReason(followUpReason);
        message.setKnowledgePoints(knowledgePoints);
        message.setScore(score);
        message.setComment(comment);
        messageMapper.insert(message);
        return message;
    }

    private GenerateFollowUpDTO buildFollowUpDTO(InnerQuestionVO question, InterviewStage stage,
                                                  EvaluateAnswerDTO evaluateDTO, String answerContent,
                                                  EvaluateAnswerVO evaluation) {
        GenerateFollowUpDTO followUpDTO = new GenerateFollowUpDTO();
        followUpDTO.setQuestionId(question == null ? null : question.getId());
        followUpDTO.setQuestionTitle(question == null ? null : question.getTitle());
        followUpDTO.setRootQuestionContent(evaluateDTO.getRootQuestionContent());
        followUpDTO.setCurrentQuestionContent(evaluateDTO.getCurrentQuestionContent());
        followUpDTO.setQuestionContent(evaluateDTO.getCurrentQuestionContent());
        followUpDTO.setReferenceAnswer(evaluateDTO.getReferenceAnswer());
        followUpDTO.setAnswerContent(answerContent);
        followUpDTO.setComment(evaluation == null ? null : evaluation.getComment());
        followUpDTO.setFollowUpCount(evaluateDTO.getFollowUpCount());
        followUpDTO.setMaxFollowUpCount(evaluateDTO.getMaxFollowUpCount());
        followUpDTO.setCurrentStage(stage == null ? null : stage.getStageName());
        followUpDTO.setHistorySummary(evaluateDTO.getHistorySummary());
        followUpDTO.setKnowledgePoints(evaluation == null ? null : evaluation.getKnowledgePoints());
        followUpDTO.setIndustryContext(evaluateDTO.getIndustryContext());
        return followUpDTO;
    }

    private InterviewMessage rootQuestionMessage(InterviewMessage message) {
        InterviewMessage current = message;
        int depth = 0;
        while (current != null && Boolean.TRUE.equals(current.getIsFollowUp()) && current.getParentMessageId() != null && depth < 8) {
            InterviewMessage parent = messageMapper.selectById(current.getParentMessageId());
            if (parent == null) {
                break;
            }
            current = parent;
            depth++;
        }
        return current;
    }

    private boolean isValidFollowUp(String followUpQuestion) {
        if (!StringUtils.hasText(followUpQuestion) || followUpQuestion.trim().length() < 12) {
            return false;
        }
        String value = followUpQuestion.trim();
        String[] banned = {"假设原问题", "如果你有具体", "请提供具体", "由于没有", "无法生成", "用户增长", "团队协作", "市场运营"};
        for (String item : banned) {
            if (value.contains(item)) {
                return false;
            }
        }
        return value.endsWith("?") || value.endsWith("？") || value.contains("请") || value.contains("如何")
                || value.contains("为什么") || value.contains("能否") || value.contains("是否");
    }

    private CurrentQuestionVO currentQuestion(InterviewSession session) {
        InterviewMessage message = currentAiQuestionMessage(session);
        if (message == null) {
            return null;
        }
        InterviewStage stage = message.getStageId() == null ? null : stageMapper.selectById(message.getStageId());
        return toCurrentQuestionVO(session, stage, message);
    }

    private CurrentQuestionVO toCurrentQuestionVO(InterviewSession session, InterviewStage stage, InterviewMessage message) {
        CurrentQuestionVO vo = new CurrentQuestionVO();
        vo.setSessionId(session.getId());
        vo.setStageId(stage == null ? null : stage.getId());
        vo.setStageName(stage == null ? null : stage.getStageName());
        vo.setMessageId(message.getId());
        vo.setQuestionId(message.getQuestionId());
        vo.setQuestionGroupId(message.getQuestionGroupId());
        vo.setQuestionContent(firstText(message.getQuestionContent(), message.getContent()));
        vo.setQuestionText(vo.getQuestionContent());
        vo.setIsFollowUp(Boolean.TRUE.equals(message.getIsFollowUp()));
        vo.setParentMessageId(message.getParentMessageId());
        vo.setFollowUpCount(message.getFollowUpCount());
        vo.setCurrentQuestionIndex(currentQuestionIndex(session));
        vo.setTotalQuestionCount(totalQuestionCount(session));
        vo.setStageAnsweredCount(safeInt(stage == null ? null : stage.getAskedQuestionCount()));
        vo.setStageExpectedQuestionCount(Math.max(0, safeInt(stage == null ? null : stage.getExpectedQuestionCount())));
        vo.setStageProgress(stageProgress(stage));
        vo.setOverallProgress(overallProgress(session));
        vo.setInterviewStatus(session.getStatus());
        return vo;
    }

    private InterviewMessage currentAiQuestionMessage(InterviewSession session) {
        return messageMapper.selectOne(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, session.getId())
                .eq(InterviewMessage::getRole, "AI")
                .in(InterviewMessage::getMessageType, List.of("QUESTION", "FOLLOW_UP"))
                .orderByDesc(InterviewMessage::getCreatedAt)
                .orderByDesc(InterviewMessage::getId)
                .last("limit 1"));
    }

    private InnerQuestionVO loadCurrentQuestion(InterviewSession session) {
        if (session.getCurrentQuestionId() == null) {
            return null;
        }
        return FeignResultUtils.unwrap(questionFeignClient.getQuestion(session.getCurrentQuestionId()));
    }

    private InterviewSession getOwnedSession(Long id) {
        InterviewSession session = sessionMapper.selectById(id);
        if (session == null || !requireCurrentUserId().equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试记录不存在或已不可用");
        }
        return session;
    }

    private InterviewStage firstStage(Long sessionId) {
        InterviewStage stage = stageMapper.selectOne(new LambdaQueryWrapper<InterviewStage>()
                .eq(InterviewStage::getSessionId, sessionId)
                .orderByAsc(InterviewStage::getSort)
                .last("limit 1"));
        if (stage == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "面试流程暂时不完整，请重新进入面试");
        }
        return stage;
    }

    private InterviewStage nextStage(InterviewStage currentStage) {
        if (currentStage == null) {
            return null;
        }
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

    private List<InterviewMessage> messageEntities(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId)
                .orderByAsc(InterviewMessage::getCreatedAt)
                .orderByAsc(InterviewMessage::getId));
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
                .eq(InterviewReport::getDeleted, CommonConstants.NO)
                .orderByDesc(InterviewReport::getId)
                .last("limit 1"));
    }

    private boolean isCurrentReportAttempt(Long sessionId, Long reportId, String generationToken) {
        if (sessionId == null || reportId == null || !StringUtils.hasText(generationToken)) {
            return false;
        }
        InterviewReport latest = currentReport(sessionId);
        return latest != null
                && reportId.equals(latest.getId())
                && generationToken.equals(latest.getGenerationToken());
    }

    private String nextGenerationToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void applyReportContent(InterviewReport report, GenerateReportVO aiReport, List<InterviewMessage> messages) {
        int answerCount = countScorableAnswers(messages);
        if (aiReportMissingDisplayContent(aiReport)
                || !hasExpectedQaReviews(aiReport == null ? null : aiReport.getQaReview(), answerCount)) {
            applyFallbackReportContent(report, aiReport, messages, answerCount);
            return;
        }
        report.setTotalScore(aiReport.getTotalScore());
        report.setSummary(StringUtils.hasText(aiReport.getSummary()) ? aiReport.getSummary() : DEFAULT_REPORT_SUMMARY);
        report.setStageScores(aiReport.getStageScores());
        report.setWeakPoints(aiReport.getWeakPoints());
        report.setStrengths(StringUtils.hasText(aiReport.getStrengths()) ? aiReport.getStrengths() : DEFAULT_REPORT_STRENGTHS);
        report.setWeaknesses(StringUtils.hasText(aiReport.getWeaknesses()) ? aiReport.getWeaknesses() : DEFAULT_REPORT_WEAKNESSES);
        report.setMainProblems(StringUtils.hasText(aiReport.getMainProblems()) ? aiReport.getMainProblems() : report.getWeaknesses());
        report.setProjectProblems(aiReport.getProjectProblems());
        report.setReviewSuggestions(StringUtils.hasText(aiReport.getReviewSuggestions())
                ? aiReport.getReviewSuggestions()
                : aiReport.getSuggestions());
        report.setRecommendedQuestions(aiReport.getRecommendedQuestions());
        report.setQaReview(aiReport.getQaReview());
        report.setReportContent(StringUtils.hasText(aiReport.getReportContent()) ? aiReport.getReportContent() : report.getSummary());
        report.setGeneratedAt(LocalDateTime.now());
        report.setSuggestions(StringUtils.hasText(aiReport.getSuggestions()) ? aiReport.getSuggestions() : DEFAULT_REPORT_SUGGESTIONS);
        report.setFailureReason(null);
        normalizeReportContent(report);
    }

    private void applyFallbackReportContent(InterviewReport report, GenerateReportVO aiReport,
                                            List<InterviewMessage> messages, int answerCount) {
        if (answerCount <= 0) {
            markReportAiIncomplete(report);
            return;
        }
        report.setStatus(ReportStatusEnum.GENERATED.name());
        report.setTotalScore(firstPositive(aiReport == null ? null : aiReport.getTotalScore(), averageAnswerScore(messages)));
        report.setSummary(firstText(aiReport == null ? null : aiReport.getSummary(), DEFAULT_REPORT_SUMMARY));
        report.setStageScores(firstText(aiReport == null ? null : aiReport.getStageScores(), "{}"));
        report.setWeakPoints(firstText(aiReport == null ? null : aiReport.getWeakPoints(), "[]"));
        report.setStrengths(firstText(aiReport == null ? null : aiReport.getStrengths(), DEFAULT_REPORT_STRENGTHS));
        report.setWeaknesses(firstText(aiReport == null ? null : aiReport.getWeaknesses(), DEFAULT_REPORT_WEAKNESSES));
        report.setMainProblems(firstText(aiReport == null ? null : aiReport.getMainProblems(), report.getWeaknesses()));
        report.setProjectProblems(firstText(aiReport == null ? null : aiReport.getProjectProblems(), "[]"));
        report.setReviewSuggestions(firstText(
                aiReport == null ? null : aiReport.getReviewSuggestions(),
                aiReport == null ? null : aiReport.getSuggestions(),
                REPORT_AI_INCOMPLETE_SUGGESTIONS));
        report.setRecommendedQuestions(firstText(aiReport == null ? null : aiReport.getRecommendedQuestions(), "[]"));
        report.setQaReview(buildFallbackQaReview(messages));
        report.setReportContent(firstText(aiReport == null ? null : aiReport.getReportContent(), report.getSummary()));
        report.setGeneratedAt(LocalDateTime.now());
        report.setSuggestions(firstText(aiReport == null ? null : aiReport.getSuggestions(), DEFAULT_REPORT_SUGGESTIONS));
        report.setFailureReason(REPORT_AI_INCOMPLETE_FALLBACK_REASON);
    }

    private String buildFallbackQaReview(List<InterviewMessage> messages) {
        List<Map<String, Object>> reviews = new ArrayList<>();
        for (InterviewMessage answer : messages == null ? List.<InterviewMessage>of() : messages) {
            if (!isUserAnswer(answer)) {
                continue;
            }
            InterviewMessage evaluation = firstMessage(messages, answer.getParentMessageId(), "AI", "EVALUATION");
            InterviewMessage followUp = firstMessage(messages, answer.getParentMessageId(), "AI", "FOLLOW_UP");
            Integer score = firstPositive(answer.getAiScore(), answer.getScore(),
                    evaluation == null ? null : evaluation.getAiScore(),
                    evaluation == null ? null : evaluation.getScore());
            String comment = firstText(answer.getAiComment(), answer.getComment(),
                    evaluation == null ? null : evaluation.getAiComment(),
                    evaluation == null ? null : evaluation.getComment(),
                    evaluation == null ? null : evaluation.getContent());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", answer.getId());
            item.put("role", answer.getRole());
            item.put("messageType", answer.getMessageType());
            item.put("questionId", answer.getQuestionId());
            item.put("questionContent", firstText(answer.getQuestionContent(), parentQuestionContent(messages, answer)));
            item.put("userAnswer", firstText(answer.getUserAnswer(), answer.getContent()));
            item.put("aiScore", score);
            item.put("score", score);
            item.put("aiComment", comment);
            item.put("comment", comment);
            item.put("knowledgePoints", firstText(answer.getKnowledgePoints(),
                    evaluation == null ? null : evaluation.getKnowledgePoints()));
            item.put("followUpQuestion", followUp == null ? null : followUp.getContent());
            item.put("followUpReason", followUp == null ? null : followUp.getFollowUpReason());
            item.put("fallback", true);
            item.put("createdAt", answer.getCreatedAt() == null ? null : answer.getCreatedAt().toString());
            reviews.add(item);
        }
        try {
            return objectMapper.writeValueAsString(reviews);
        } catch (Exception ex) {
            log.warn("Failed to build fallback qaReview");
            return "[]";
        }
    }

    private Integer averageAnswerScore(List<InterviewMessage> messages) {
        int total = 0;
        int count = 0;
        if (messages != null) {
            for (InterviewMessage message : messages) {
                Integer score = firstPositive(message.getAiScore(), message.getScore());
                if (score != null) {
                    total += score;
                    count++;
                }
            }
        }
        return count == 0 ? null : Math.round((float) total / count);
    }

    private Integer firstPositive(Integer... values) {
        if (values == null) {
            return null;
        }
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private boolean isUserAnswer(InterviewMessage message) {
        return message != null
                && "USER".equalsIgnoreCase(message.getRole())
                && "ANSWER".equalsIgnoreCase(message.getMessageType())
                && StringUtils.hasText(firstText(message.getUserAnswer(), message.getContent()));
    }

    private boolean isFallbackReport(InterviewReport report) {
        return report != null
                && StringUtils.hasText(report.getFailureReason())
                && report.getFailureReason().contains(REPORT_AI_INCOMPLETE_FALLBACK_REASON);
    }

    private InterviewMessage firstMessage(List<InterviewMessage> messages, Long parentMessageId, String role, String type) {
        if (messages == null || parentMessageId == null) {
            return null;
        }
        return messages.stream()
                .filter(message -> parentMessageId.equals(message.getParentMessageId()))
                .filter(message -> role.equalsIgnoreCase(message.getRole()))
                .filter(message -> type.equalsIgnoreCase(message.getMessageType()))
                .findFirst()
                .orElse(null);
    }

    private String parentQuestionContent(List<InterviewMessage> messages, InterviewMessage answer) {
        if (messages == null || answer == null || answer.getParentMessageId() == null) {
            return null;
        }
        return messages.stream()
                .filter(message -> answer.getParentMessageId().equals(message.getId()))
                .findFirst()
                .map(message -> firstText(message.getQuestionContent(), message.getContent()))
                .orElse(null);
    }

    private boolean aiReportMissingDisplayContent(GenerateReportVO aiReport) {
        return aiReport == null
                || aiReport.getTotalScore() == null
                || aiReport.getTotalScore() <= 0
                || !StringUtils.hasText(aiReport.getSummary())
                || !StringUtils.hasText(aiReport.getReportContent());
    }

    private boolean hasExpectedQaReviews(String qaReview, int answerCount) {
        if (answerCount <= 0) {
            return false;
        }
        return countJsonArrayItems(qaReview) == answerCount;
    }

    private int countScorableAnswers(List<InterviewMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return (int) messages.stream().filter(message ->
                "USER".equalsIgnoreCase(message.getRole())
                        && "ANSWER".equalsIgnoreCase(message.getMessageType())
                        && StringUtils.hasText(firstText(message.getUserAnswer(), message.getContent()))).count();
    }

    private int countJsonArrayItems(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node != null && node.isArray() ? node.size() : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    private void attachFallbackQaReview(InterviewReport report, List<InterviewMessage> messages) {
        if (report == null || messages == null || messages.isEmpty() || countJsonArrayItems(report.getQaReview()) > 0) {
            return;
        }
        List<InterviewMessageVO> reviews = messages.stream()
                .filter(message -> StringUtils.hasText(message.getQuestionContent())
                        || StringUtils.hasText(message.getUserAnswer())
                        || StringUtils.hasText(message.getAiComment())
                        || StringUtils.hasText(message.getContent()))
                .map(InterviewConvert::toMessageVO)
                .toList();
        if (reviews.isEmpty()) {
            return;
        }
        try {
            report.setQaReview(objectMapper.writeValueAsString(reviews));
        } catch (Exception ex) {
            log.warn("Failed to attach fallback qaReview, sessionId={}", report.getSessionId(), ex);
        }
    }

    private boolean normalizeReportContent(InterviewReport report) {
        if (report == null || !isEnglishMockReport(report)) {
            return false;
        }
        markReportAiIncomplete(report);
        saveReport(report);
        return true;
    }

    private boolean isEnglishMockReport(InterviewReport report) {
        return containsIgnoreCase(report.getSummary(), "Mock report")
                || containsIgnoreCase(report.getSummary(), "the interview has been completed")
                || containsIgnoreCase(report.getStrengths(), "Shows basic understanding")
                || containsIgnoreCase(report.getWeaknesses(), "Needs more depth")
                || containsIgnoreCase(report.getSuggestions(), "Review JVM");
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return StringUtils.hasText(value) && value.toLowerCase().contains(keyword.toLowerCase());
    }

    private void markReportAiIncomplete(InterviewSession session, InterviewReport report) {
        markReportAiIncomplete(report);
        session.setStatus(InterviewStatusEnum.COMPLETED.name());
        session.setReportStatus(ReportStatusEnum.FAILED.name());
        session.setTotalScore(null);
        session.setEndTime(session.getEndTime() == null ? LocalDateTime.now() : session.getEndTime());
        session.setFailureReason(REPORT_AI_INCOMPLETE_MESSAGE);
    }

    private void markReportAiIncomplete(InterviewReport report) {
        report.setStatus(ReportStatusEnum.FAILED.name());
        report.setTotalScore(null);
        report.setSummary(REPORT_AI_INCOMPLETE_MESSAGE);
        report.setStageScores("{}");
        report.setWeakPoints("[]");
        report.setStrengths("[]");
        report.setWeaknesses(null);
        report.setMainProblems("[]");
        report.setProjectProblems("[]");
        report.setReviewSuggestions(REPORT_AI_INCOMPLETE_SUGGESTIONS);
        report.setRecommendedQuestions("[]");
        report.setQaReview("[]");
        report.setReportContent(REPORT_AI_INCOMPLETE_MESSAGE);
        report.setGeneratedAt(LocalDateTime.now());
        report.setSuggestions(REPORT_AI_INCOMPLETE_SUGGESTIONS);
        report.setFailureReason(REPORT_AI_INCOMPLETE_MESSAGE);
    }

    private InterviewReport markReportSampleInsufficient(InterviewSession session, InterviewReport report) {
        report.setUserId(session.getUserId());
        report.setStatus(ReportStatusEnum.FAILED.name());
        report.setTotalScore(null);
        report.setSummary(REPORT_SAMPLE_INSUFFICIENT_MESSAGE);
        report.setStageScores("{}");
        report.setWeakPoints("[]");
        report.setStrengths("[]");
        report.setWeaknesses(null);
        report.setMainProblems("[]");
        report.setProjectProblems("[]");
        report.setReviewSuggestions(REPORT_SAMPLE_INSUFFICIENT_SUGGESTIONS);
        report.setRecommendedQuestions("[]");
        report.setQaReview("[]");
        report.setReportContent(REPORT_SAMPLE_INSUFFICIENT_MESSAGE);
        report.setGeneratedAt(LocalDateTime.now());
        report.setSuggestions(REPORT_SAMPLE_INSUFFICIENT_SUGGESTIONS);
        report.setFailureReason(REPORT_SAMPLE_INSUFFICIENT_MESSAGE);
        saveReport(report);

        session.setStatus(InterviewStatusEnum.COMPLETED.name());
        session.setReportStatus(ReportStatusEnum.FAILED.name());
        session.setTotalScore(null);
        session.setEndTime(session.getEndTime() == null ? LocalDateTime.now() : session.getEndTime());
        session.setFailureReason(REPORT_SAMPLE_INSUFFICIENT_MESSAGE);
        sessionMapper.updateById(session);
        return report;
    }

    private boolean hasScorableAnswers(Long sessionId) {
        return hasScorableAnswers(messageEntities(sessionId));
    }

    private boolean hasScorableAnswers(List<InterviewMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        return messages.stream().anyMatch(message ->
                "USER".equalsIgnoreCase(message.getRole())
                        && "ANSWER".equalsIgnoreCase(message.getMessageType())
                        && StringUtils.hasText(firstText(message.getUserAnswer(), message.getContent())));
    }

    private void saveReport(InterviewReport report) {
        if (report.getId() == null) {
            reportMapper.insert(report);
        } else {
            reportMapper.updateById(report);
        }
    }

    private void completeAgentInterviewTask(InterviewSession session, InterviewReport report) {
        if (session == null || report == null || !ReportStatusEnum.GENERATED.name().equals(report.getStatus())) {
            return;
        }
        agentBusinessActionNotifier.completeInterviewReport(session.getUserId(), session.getTargetJobId(),
                report.getId());
    }

    private void validateResumeOwnership(Long userId, String mode, CreateInterviewDTO dto) {
        boolean resumeRequired = Boolean.TRUE.equals(dto.getBasedOnResume())
                && (InterviewModeEnum.PROJECT_DEEP_DIVE.name().equals(mode) || InterviewModeEnum.COMPREHENSIVE.name().equals(mode));
        if (resumeRequired && dto.getResumeId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择用于面试的简历");
        }
        if (dto.getResumeId() != null) {
            InnerResumeDetailVO resume = FeignResultUtils.unwrap(resumeFeignClient.getResume(dto.getResumeId()));
            if (resume == null || !userId.equals(resume.getUserId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume not found");
            }
        }
    }

    private void validateSuccessfulMatchReportContext(Long userId, CreateInterviewDTO request) {
        if (request == null || request.getMatchReportId() == null) {
            return;
        }
        InnerResumeJobMatchReportVO report = FeignResultUtils.unwrap(
                resumeFeignClient.getSuccessResumeJobMatchReport(request.getMatchReportId()));
        if (report == null || !userId.equals(report.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "匹配报告不存在，不能作为面试依据");
        }
        if (!"SUCCESS".equalsIgnoreCase(String.valueOf(report.getStatus()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "匹配报告未成功，不能作为面试依据");
        }
        if (request.getResumeId() != null && report.getResumeId() != null
                && !request.getResumeId().equals(report.getResumeId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试简历与匹配报告不一致");
        }
        if (request.getTargetJobId() != null && report.getTargetJobId() != null
                && !request.getTargetJobId().equals(report.getTargetJobId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标岗位与匹配报告不一致");
        }
        if (request.getResumeId() == null) {
            request.setResumeId(report.getResumeId());
        }
        if (request.getTargetJobId() == null) {
            request.setTargetJobId(report.getTargetJobId());
        }
    }

    private InnerTargetJobVO resolveTargetJob(Long userId, CreateInterviewDTO request) {
        if (request.getTargetJobId() != null) {
            InnerTargetJobVO targetJob = FeignResultUtils.unwrap(resumeFeignClient.getTargetJob(userId, request.getTargetJobId()));
            if (targetJob == null || !userId.equals(targetJob.getUserId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Target job not found");
            }
            if (!StringUtils.hasText(request.getTargetPosition())) {
                request.setTargetPosition(targetJob.getJobTitle());
            }
            return targetJob;
        }
        InnerTargetJobVO currentTargetJob = FeignResultUtils.unwrap(resumeFeignClient.getCurrentTargetJob(userId));
        if (currentTargetJob == null) {
            return null;
        }
        request.setTargetJobId(currentTargetJob.getId());
        if (!StringUtils.hasText(request.getTargetPosition())) {
            request.setTargetPosition(currentTargetJob.getJobTitle());
        }
        return currentTargetJob;
    }

    private void resolveDefaultResumeIfNeeded(String mode, CreateInterviewDTO request) {
        if (request.getResumeId() != null || !Boolean.TRUE.equals(request.getBasedOnResume())) {
            return;
        }
        try {
            InnerResumeDetailVO resume = FeignResultUtils.unwrap(resumeFeignClient.getDefaultResume());
            if (resume != null) {
                request.setResumeId(resume.getId());
            }
        } catch (BusinessException ignored) {
            // Keep the original validation path so the API returns the existing resume-required message.
        }
    }

    private IndustryTemplateSnapshot resolveIndustrySnapshot(CreateInterviewDTO dto) {
        if (dto == null || dto.getIndustryTemplateId() == null) {
            String direction = normalizeText(dto == null ? null : dto.getIndustryDirection());
            String context = StringUtils.hasText(direction)
                    ? "行业方向：" + direction + "\n行业上下文仅作为场景化提问参考，不代表候选人真实项目经历。"
                    : null;
            return new IndustryTemplateSnapshot(null, direction, context);
        }
        IndustryTemplate template = industryTemplateService.getEnabledTemplate(dto.getIndustryTemplateId());
        String direction = firstText(normalizeText(dto.getIndustryDirection()), template.getIndustryName());
        return new IndustryTemplateSnapshot(template.getId(), direction, buildIndustryContext(template, direction));
    }

    private IndustryTemplateSnapshot withRecommendationContext(IndustryTemplateSnapshot snapshot, CreateInterviewDTO dto) {
        if (dto == null) {
            return snapshot;
        }
        String source = normalizeText(dto.getRecommendationSource());
        String reason = normalizeText(dto.getRecommendationReason());
        String practiceMode = normalizeText(dto.getPracticeMode());
        if (!StringUtils.hasText(source) && !StringUtils.hasText(reason) && !StringUtils.hasText(practiceMode)) {
            return snapshot;
        }
        StringBuilder builder = new StringBuilder(firstText(snapshot.industryContext(), ""));
        appendLine(builder, "推荐配置来源", source);
        appendLine(builder, "推荐配置依据", reason);
        appendLine(builder, "训练模式", practiceMode);
        return new IndustryTemplateSnapshot(
                snapshot.industryTemplateId(),
                snapshot.industryDirection(),
                builder.toString().trim());
    }

    private String buildIndustryContext(IndustryTemplate template, String direction) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "行业方向", direction);
        appendLine(builder, "行业编码", template.getIndustryCode());
        appendLine(builder, "行业说明", template.getDescription());
        appendLine(builder, "适用岗位", template.getTargetPositions());
        appendLine(builder, "核心业务场景", template.getCoreBusinessScenarios());
        appendLine(builder, "关键技术关注点", template.getKeyTechnicalPoints());
        appendLine(builder, "常见追问方向", template.getCommonQuestionDirections());
        appendLine(builder, "常见风险点", template.getRiskPoints());
        appendLine(builder, "Prompt上下文", template.getPromptContext());
        builder.append("边界要求：行业上下文只作为场景化提问参考，不代表候选人真实经历；如果简历项目没有对应行业背景，只能以假设场景或迁移能力方式追问。");
        return builder.toString().trim();
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void markAnswered(InterviewSession session, InterviewStage stage) {
        session.setAnsweredQuestionCount(safeInt(session.getAnsweredQuestionCount()) + 1);
        session.setCurrentFollowUpCount(0);
        incrementStageAsked(stage);
    }

    private void incrementStageAsked(InterviewStage stage) {
        if (stage == null) {
            return;
        }
        stage.setAskedQuestionCount(safeInt(stage.getAskedQuestionCount()) + 1);
        stageMapper.updateById(stage);
    }

    private String historySummary(Long sessionId) {
        return messageEntities(sessionId).stream()
                .map(message -> firstText(message.getQuestionContent(), message.getUserAnswer(), message.getAiComment(), message.getContent()))
                .filter(StringUtils::hasText)
                .limit(12)
                .toList()
                .toString();
    }

    private String stageProgress(InterviewStage stage) {
        if (stage == null) {
            return null;
        }
        return safeInt(stage.getAskedQuestionCount()) + "/" + Math.max(0, safeInt(stage.getExpectedQuestionCount()));
    }

    private int totalExpectedQuestionCount(List<InterviewStage> stages) {
        if (stages == null || stages.isEmpty()) {
            return 0;
        }
        return stages.stream()
                .map(InterviewStage::getExpectedQuestionCount)
                .mapToInt(this::safeInt)
                .sum();
    }

    private int totalQuestionCount(InterviewSession session) {
        if (safeInt(session.getMaxQuestionCount()) > 0) {
            return session.getMaxQuestionCount();
        }
        int total = totalExpectedQuestionCount(stages(session.getId()));
        return total > 0 ? total : normalizeQuestionCount(null);
    }

    private int currentQuestionIndex(InterviewSession session) {
        int total = totalQuestionCount(session);
        if (total <= 0) {
            return 0;
        }
        if (InterviewStatusEnum.COMPLETED.name().equals(session.getStatus())
                || InterviewStatusEnum.REPORT_GENERATING.name().equals(session.getStatus())) {
            return total;
        }
        return Math.min(total, safeInt(session.getAnsweredQuestionCount()) + 1);
    }

    private String overallProgress(InterviewSession session) {
        int total = totalQuestionCount(session);
        int answered = Math.min(safeInt(session.getAnsweredQuestionCount()), total);
        return answered + "/" + total;
    }

    private InnerResumeDetailVO loadResume(InterviewSession session) {
        if (session.getResumeId() == null) {
            return null;
        }
        return FeignResultUtils.unwrap(resumeFeignClient.getResume(session.getResumeId()));
    }

    private String skillGapContext(InterviewSession session) {
        InnerSkillProfileVO profile = null;
        try {
            if (session.getSkillProfileId() != null) {
                profile = FeignResultUtils.unwrap(resumeFeignClient.getSkillProfile(session.getSkillProfileId()));
            } else if (session.getMatchReportId() != null) {
                profile = FeignResultUtils.unwrap(resumeFeignClient.getSuccessSkillProfileByMatchReport(session.getMatchReportId()));
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        if (profile == null || profile.getGapItems() == null || profile.getGapItems().isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "目标岗位ID", profile.getTargetJobId() == null ? null : profile.getTargetJobId().toString());
        appendLine(builder, "匹配报告ID", profile.getMatchReportId() == null ? null : profile.getMatchReportId().toString());
        appendLine(builder, "能力画像", profile.getSummary());
        for (InnerSkillGapItemVO gap : profile.getGapItems().stream().limit(8).toList()) {
            if (gap == null) {
                continue;
            }
            builder.append("- ")
                    .append(firstText(gap.getSkillName(), gap.getCategory(), "未命名短板"))
                    .append(" | severity=").append(firstText(gap.getSeverity(), "UNKNOWN"))
                    .append(" | gapLevel=").append(gap.getGapLevel() == null ? "" : gap.getGapLevel())
                    .append(" | ").append(firstText(gap.getGapDescription(), ""))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String buildProjectContent(InnerResumeDetailVO resume) {
        if (resume == null || resume.getProjects() == null || resume.getProjects().isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (InnerResumeProjectVO project : resume.getProjects()) {
            if (project == null) {
                continue;
            }
            appendLine(builder, "项目" + index + "名称", project.getProjectName());
            appendLine(builder, "项目周期", project.getProjectPeriod());
            appendLine(builder, "项目背景", firstText(project.getProjectBackground(), project.getDescription()));
            appendLine(builder, "技术栈", project.getTechStack());
            appendLine(builder, "个人角色", project.getRole());
            appendLine(builder, "个人职责", project.getResponsibility());
            appendLine(builder, "核心功能", project.getCoreFeatures());
            appendLine(builder, "技术难点", project.getTechnicalDifficulties());
            appendLine(builder, "优化结果", project.getOptimizationResults());
            appendLine(builder, "项目亮点", project.getHighlights());
            builder.append('\n');
            index++;
        }
        return builder.toString().trim();
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(label).append("：").append(value.trim()).append('\n');
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record IndustryTemplateSnapshot(Long industryTemplateId, String industryDirection, String industryContext) {
    }

    private record StartContext(InterviewSession session, InterviewStage stage) {
    }

    private record AnswerContext(InterviewSession session, InterviewStage stage,
                                 InterviewMessage currentAiQuestion, InnerQuestionVO question,
                                 InterviewMessage answerMessage) {
    }

    private record FollowUpPlan(String question, String reason, Long aiCallLogId) {
    }

    private record AnswerPersistence(InterviewMessage evaluationMessage, InterviewMessage followUpMessage,
                                     Long followUpAiCallLogId, NextActionEnum nextAction,
                                     CurrentQuestionVO nextQuestion, InterviewStage questionGenerationStage) {
    }

    private record PreparedReportGeneration(InterviewReport report, FinishInterviewVO response) {
    }

    private int normalizeQuestionCount(Integer value) {
        if (value == null || value <= 0) {
            return 8;
        }
        return Math.min(20, Math.max(1, value));
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
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
