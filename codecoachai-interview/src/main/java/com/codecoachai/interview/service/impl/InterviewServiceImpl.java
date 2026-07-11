package com.codecoachai.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.codecoachai.interview.domain.vo.InterviewTranscriptVO;
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
import com.codecoachai.interview.feign.dto.JobApplicationEventSaveDTO;
import com.codecoachai.interview.feign.vo.EvaluateAnswerVO;
import com.codecoachai.interview.feign.vo.GenerateFollowUpVO;
import com.codecoachai.interview.feign.vo.GenerateInterviewQuestionVO;
import com.codecoachai.interview.feign.vo.GenerateReportVO;
import com.codecoachai.interview.feign.vo.InnerJobApplicationPackageVO;
import com.codecoachai.interview.feign.vo.InnerJobApplicationSummaryVO;
import com.codecoachai.interview.feign.vo.InnerProjectEvidenceTrainingContextVO;
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
import com.codecoachai.interview.service.InterviewVoiceService;
import com.codecoachai.interview.support.InterviewReportTrustPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import feign.Response;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
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
    private static final String TRAINING_SCENE_JAVA_SPECIALTY = "JAVA_SPECIALTY";
    private static final String TRAINING_SCENE_PROJECT_DEEP_DIVE = "PROJECT_DEEP_DIVE";

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
    private final InterviewVoiceService interviewVoiceService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateInterviewVO create(CreateInterviewDTO dto) {
        CreateInterviewDTO request = dto == null ? new CreateInterviewDTO() : dto;
        Long userId = requireCurrentUserId();
        Long applicationPackageId = parseApplicationPackageId(request.getApplicationPackageId());
        InnerJobApplicationPackageVO applicationPackage =
                validateApplicationPackageBinding(userId, applicationPackageId, request);
        InnerJobApplicationSummaryVO application = validateApplicationBinding(userId, request);
        validateSkillProfileContext(userId, request);
        InnerResumeJobMatchReportVO matchReport = validateSuccessfulMatchReportContext(userId, request);
        validateApplicationContextMatches(application, request);
        validateApplicationPackageContextMatches(applicationPackage, request);
        validateAnchoredVersionContext(applicationPackage, application, matchReport, request);
        String mode = normalizeMode(StringUtils.hasText(request.getInterviewMode()) ? request.getInterviewMode() : request.getMode());
        InnerTargetJobVO targetJob = resolveTargetJob(userId, request);
        resolveDefaultResumeIfNeeded(mode, request);
        validateResumeOwnership(userId, mode, request);
        IndustryTemplateSnapshot industrySnapshot = withRecommendationContext(resolveIndustrySnapshot(request), request);
        List<String> targetSkillCodes = sanitizeStrings(request.getTargetSkillCodes());
        List<Long> projectEvidenceIds = sanitizeLongs(request.getProjectEvidenceIds());
        String trainingContextSummary = buildTrainingContextSummary(userId, request, targetSkillCodes, projectEvidenceIds);

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setApplicationId(request.getApplicationId());
        session.setApplicationPackageId(applicationPackageId);
        session.setResumeId(request.getResumeId());
        session.setResumeVersionId(request.getResumeVersionId());
        session.setTargetJobId(request.getTargetJobId());
        session.setJdAnalysisId(request.getJdAnalysisId());
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
        session.setTrainingScene(normalizeText(request.getTrainingScene()));
        session.setTargetSkillDomain(normalizeText(request.getTargetSkillDomain()));
        session.setTargetSkillCodes(toJsonOrNull(targetSkillCodes));
        session.setTargetLevel(normalizeText(request.getTargetLevel()));
        session.setProjectEvidenceIds(toJsonOrNull(projectEvidenceIds));
        session.setFollowUpIntensity(normalizeText(request.getFollowUpIntensity()));
        session.setTrainingContextSummary(trainingContextSummary);
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
        vo.setApplicationId(session.getApplicationId());
        vo.setApplicationPackageId(session.getApplicationPackageId());
        vo.setJdAnalysisId(session.getJdAnalysisId());
        vo.setResumeVersionId(session.getResumeVersionId());
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
        vo.setTrainingScene(session.getTrainingScene());
        vo.setTargetSkillDomain(session.getTargetSkillDomain());
        vo.setTargetSkillCodes(targetSkillCodes);
        vo.setTargetLevel(session.getTargetLevel());
        vo.setProjectEvidenceIds(projectEvidenceIds);
        vo.setFollowUpIntensity(session.getFollowUpIntensity());
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

        CurrentQuestionVO question = currentQuestion(session);
        if (question == null) {
            question = generateNextQuestion(session, stage, null, false, 0);
        }
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
            if (InterviewStatusEnum.WAITING_ANSWER.name().equals(session.getStatus())
                    || InterviewStatusEnum.IN_PROGRESS.name().equals(session.getStatus())
                    || InterviewStatusEnum.AI_EVALUATING.name().equals(session.getStatus())) {
                InterviewStage currentStage = session.getCurrentStageId() == null
                        ? firstStage(session.getId())
                        : stageMapper.selectById(session.getCurrentStageId());
                if (currentStage == null) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Interview stage missing");
                }
                return new StartContext(session, currentStage);
            }
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview has already started");
        }
        InterviewStage stage = firstStage(session.getId());
        LocalDateTime startTime = LocalDateTime.now();
        int updated = sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                .eq(InterviewSession::getId, session.getId())
                .eq(InterviewSession::getUserId, session.getUserId())
                .eq(InterviewSession::getStatus, InterviewStatusEnum.NOT_STARTED.name())
                .set(InterviewSession::getStatus, InterviewStatusEnum.WAITING_ANSWER.name())
                .set(InterviewSession::getStartTime, startTime)
                .set(InterviewSession::getCurrentStageId, stage.getId())
                .set(InterviewSession::getCurrentFollowUpCount, 0));
        if (updated != 1) {
            InterviewSession latest = getOwnedSession(id);
            if (InterviewStatusEnum.WAITING_ANSWER.name().equals(latest.getStatus())
                    || InterviewStatusEnum.IN_PROGRESS.name().equals(latest.getStatus())
                    || InterviewStatusEnum.AI_EVALUATING.name().equals(latest.getStatus())) {
                InterviewStage currentStage = latest.getCurrentStageId() == null
                        ? stage
                        : stageMapper.selectById(latest.getCurrentStageId());
                return new StartContext(latest, currentStage == null ? stage : currentStage);
            }
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview has already started");
        }
        stageMapper.update(null, new LambdaUpdateWrapper<InterviewStage>()
                .eq(InterviewStage::getId, stage.getId())
                .eq(InterviewStage::getSessionId, session.getId())
                .eq(InterviewStage::getStatus, InterviewStatusEnum.NOT_STARTED.name())
                .set(InterviewStage::getStatus, InterviewStatusEnum.IN_PROGRESS.name()));
        stage.setStatus(InterviewStatusEnum.IN_PROGRESS.name());
        session.setStatus(InterviewStatusEnum.WAITING_ANSWER.name());
        session.setStartTime(startTime);
        session.setCurrentStageId(stage.getId());
        session.setCurrentFollowUpCount(0);
        return new StartContext(session, stage);
    }

    @Override
    public CurrentInterviewVO current(Long id) {
        InterviewSession session = getOwnedSession(id);
        CurrentInterviewVO vo = new CurrentInterviewVO();
        vo.setId(session.getId());
        vo.setApplicationId(session.getApplicationId());
        vo.setApplicationPackageId(session.getApplicationPackageId());
        vo.setJdAnalysisId(session.getJdAnalysisId());
        vo.setResumeVersionId(session.getResumeVersionId());
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
        InterviewStage currentStage = stage;
        transactionTemplate.executeWithoutResult(status -> {
            stageMapper.update(null, new LambdaUpdateWrapper<InterviewStage>()
                    .eq(InterviewStage::getId, currentStage.getId())
                    .eq(InterviewStage::getSessionId, session.getId())
                    .in(InterviewStage::getStatus, List.of(
                            InterviewStatusEnum.NOT_STARTED.name(),
                            InterviewStatusEnum.IN_PROGRESS.name()))
                    .set(InterviewStage::getStatus, InterviewStatusEnum.IN_PROGRESS.name()));
            sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                    .eq(InterviewSession::getId, session.getId())
                    .eq(InterviewSession::getUserId, session.getUserId())
                    .notIn(InterviewSession::getStatus, List.of(
                            InterviewStatusEnum.COMPLETED.name(),
                            InterviewStatusEnum.CANCELED.name(),
                            InterviewStatusEnum.REPORT_GENERATING.name()))
                    .set(InterviewSession::getCurrentStageId, currentStage.getId())
                    .set(InterviewSession::getStatus, InterviewStatusEnum.WAITING_ANSWER.name()));
        });
        stage.setStatus(InterviewStatusEnum.IN_PROGRESS.name());
        session.setCurrentStageId(stage.getId());
        session.setStatus(InterviewStatusEnum.WAITING_ANSWER.name());
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
        InterviewTranscriptVO voiceTranscript = context.voiceTranscript();
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
        applyTrainingContext(session, evaluateDTO);
        EvaluateAnswerVO evaluation;
        AnswerPersistence persistence;
        try {
            progress(progressConsumer, "CALL_AI_REVIEW");
            evaluation = tokenConsumer == null
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
            persistence = transactionTemplate.execute(status -> saveAnswerEvaluation(
                    session, stage, question, currentAiQuestion, dto, evaluation, finalNextAction, finalFollowUpPlan, progressConsumer));
        } catch (RuntimeException ex) {
            compensateAnswerFailure(context, ex);
            throw ex;
        }

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
        if (voiceTranscript != null) {
            vo.setVoiceSubmissionId(voiceTranscript.getVoiceSubmissionId());
            vo.setTranscriptId(voiceTranscript.getTranscriptId());
            vo.setTranscriptConfidence(voiceTranscript.getConfidence());
            vo.setAnswerSource(dto.getAnswerSource());
            vo.setVoiceLowConfidence(voiceTranscript.getLowConfidence());
            vo.setVoiceFallback(voiceTranscript.getFallback());
            vo.setVoiceTraceId(voiceTranscript.getTraceId());
        }
        return vo;
    }

    private AnswerContext prepareAnswer(Long id, SubmitInterviewAnswerDTO dto) {
        InterviewSession session = getOwnedSession(id);
        String previousStatus = session.getStatus();
        if (!InterviewStatusEnum.WAITING_ANSWER.name().equals(previousStatus)
                && !InterviewStatusEnum.IN_PROGRESS.name().equals(previousStatus)) {
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
        if (dto.getMessageId() != null && !dto.getMessageId().equals(currentAiQuestion.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question does not belong to current interview state");
        }
        if (hasAnswerForCurrentQuestion(session, currentAiQuestion)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Current question has already been answered");
        }
        InterviewTranscriptVO voiceTranscript = interviewVoiceService.validateConfirmedTranscriptForAnswer(id, dto);
        InnerQuestionVO question = loadCurrentQuestion(session);
        if (!claimAnswerProcessing(session)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Current answer is already being processed");
        }
        InterviewMessage answerMessage = saveMessage(session, stage, question, "USER", "ANSWER", dto.getAnswerContent(), null, null,
                currentAiQuestion.getId(), false, session.getCurrentFollowUpCount(), null, null);
        if (voiceTranscript != null) {
            interviewVoiceService.markTranscriptSubmitted(session.getId(), voiceTranscript.getTranscriptId(), answerMessage.getId());
        }
        session.setStatus(InterviewStatusEnum.AI_EVALUATING.name());
        return new AnswerContext(session, previousStatus, stage, currentAiQuestion, question, answerMessage, voiceTranscript);
    }

    private void compensateAnswerFailure(AnswerContext context, RuntimeException ex) {
        if (context == null || context.session() == null || context.session().getId() == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            InterviewSession session = context.session();
            String restoreStatus = InterviewStatusEnum.IN_PROGRESS.name().equals(context.previousStatus())
                    ? InterviewStatusEnum.IN_PROGRESS.name()
                    : InterviewStatusEnum.WAITING_ANSWER.name();
            int resetRows = sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                    .eq(InterviewSession::getId, session.getId())
                    .eq(InterviewSession::getUserId, session.getUserId())
                    .eq(InterviewSession::getStatus, InterviewStatusEnum.AI_EVALUATING.name())
                    .set(InterviewSession::getStatus, restoreStatus));
            if (resetRows <= 0) {
                return;
            }
            if (context.answerMessage() != null && context.answerMessage().getId() != null) {
                messageMapper.deleteById(context.answerMessage().getId());
            }
            if (context.voiceTranscript() != null && context.voiceTranscript().getTranscriptId() != null
                    && context.answerMessage() != null && context.answerMessage().getId() != null) {
                interviewVoiceService.resetTranscriptSubmitted(session.getId(),
                        context.voiceTranscript().getTranscriptId(), context.answerMessage().getId());
            }
            log.warn("Interview answer evaluation failed and was reset, sessionId={} answerMessageId={} failureType={}",
                    session.getId(),
                    context.answerMessage() == null ? null : context.answerMessage().getId(),
                    ex.getClass().getSimpleName());
        });
    }

    private boolean claimAnswerProcessing(InterviewSession session) {
        int updated = sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                .eq(InterviewSession::getId, session.getId())
                .eq(InterviewSession::getUserId, session.getUserId())
                .in(InterviewSession::getStatus, List.of(
                        InterviewStatusEnum.WAITING_ANSWER.name(),
                        InterviewStatusEnum.IN_PROGRESS.name()))
                .set(InterviewSession::getStatus, InterviewStatusEnum.AI_EVALUATING.name()));
        if (updated == 1) {
            session.setStatus(InterviewStatusEnum.AI_EVALUATING.name());
            return true;
        }
        return false;
    }

    private boolean hasAnswerForCurrentQuestion(InterviewSession session, InterviewMessage currentAiQuestion) {
        if (session == null || currentAiQuestion == null || currentAiQuestion.getId() == null) {
            return false;
        }
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, session.getId())
                .eq(InterviewMessage::getParentMessageId, currentAiQuestion.getId())
                .eq(InterviewMessage::getRole, "USER")
                .eq(InterviewMessage::getMessageType, "ANSWER")
                .eq(InterviewMessage::getDeleted, CommonConstants.NO));
        return count != null && count > 0;
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
        if (prepared.dispatchRequired()) {
            submitReportGenerationAfterCommit(session.getId(), session.getUserId(),
                    prepared.report(), prepared.response());
        }
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
        if (prepared.dispatchRequired()) {
            submitReportGenerationAfterCommit(session.getId(), session.getUserId(),
                    prepared.report(), prepared.response());
        }
        return prepared.response();
    }

    @Override
    public PageResult<InterviewListVO> list(Long pageNo, Long pageSize, String status, String reportStatus,
                                            String keyword) {
        long actualPageNo = pageNo == null || pageNo < 1 ? 1L : pageNo;
        long actualPageSize = pageSize == null || pageSize < 1 ? 10L : Math.min(pageSize, 100L);
        String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<InterviewSession> page = sessionMapper.selectPage(Page.of(actualPageNo, actualPageSize),
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, requireCurrentUserId())
                        .eq(StringUtils.hasText(status), InterviewSession::getStatus, status)
                        .eq(StringUtils.hasText(reportStatus), InterviewSession::getReportStatus, reportStatus)
                        .and(StringUtils.hasText(trimmedKeyword), wrapper -> wrapper
                                .like(InterviewSession::getTitle, trimmedKeyword)
                                .or().like(InterviewSession::getTargetPosition, trimmedKeyword)
                                .or().like(InterviewSession::getIndustryDirection, trimmedKeyword)
                                .or().like(InterviewSession::getDifficulty, trimmedKeyword))
                        .orderByDesc(InterviewSession::getUpdatedAt));
        return PageResult.of(page.getRecords().stream().map(InterviewConvert::toListVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public InterviewDetailVO detail(Long id) {
        InterviewSession session = getOwnedSession(id);
        InterviewDetailVO vo = new InterviewDetailVO();
        vo.setId(session.getId());
        vo.setApplicationId(session.getApplicationId());
        vo.setApplicationPackageId(session.getApplicationPackageId());
        vo.setJdAnalysisId(session.getJdAnalysisId());
        vo.setResumeVersionId(session.getResumeVersionId());
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
        vo.setTrainingScene(session.getTrainingScene());
        vo.setTargetSkillDomain(session.getTargetSkillDomain());
        vo.setTargetSkillCodes(readStringList(session.getTargetSkillCodes()));
        vo.setTargetLevel(session.getTargetLevel());
        vo.setProjectEvidenceIds(readLongList(session.getProjectEvidenceIds()));
        vo.setFollowUpIntensity(session.getFollowUpIntensity());
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

        InterviewReport report = existing == null ? new InterviewReport() : existing;
        if (report.getId() == null) {
            report.setSessionId(session.getId());
            report.setUserId(session.getUserId());
        }
        String previousReportStatus = report.getStatus();
        String previousGenerationToken = report.getGenerationToken();
        String generationToken = nextGenerationToken();
        report.setStatus(ReportStatusEnum.GENERATING.name());
        report.setFailureReason(null);
        report.setGenerationToken(generationToken);
        if (!claimReportAttempt(report, previousReportStatus, previousGenerationToken)) {
            InterviewReport latest = currentReport(session.getId());
            return buildReportGenerateResult(session, null, latest == null ? report : latest);
        }

        Long aiCallLogId = null;
        try {
            progress(progressConsumer, "LOAD_ANSWERS");
            List<InterviewMessage> messages = messageEntities(session.getId());
            if (!hasScorableAnswers(messages)) {
                progress(progressConsumer, "SAVE_REPORT");
                markReportSampleInsufficient(session, report, generationToken);
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
            if (!updateCurrentReportAttempt(report, generationToken)) {
                InterviewReport latest = currentReport(session.getId());
                return buildReportGenerateResult(session, aiCallLogId, latest == null ? report : latest);
            }

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
                syncApplicationInterviewEvent(session, report);
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
            if (!updateCurrentReportAttempt(report, generationToken)) {
                InterviewReport latest = currentReport(session.getId());
                return buildReportGenerateResult(session, aiCallLogId, latest == null ? report : latest);
            }

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
        Map<Long, InterviewMessage> messagesById = messageById(messages);
        reportDTO.setMessages(messages.stream()
                .map(message -> reportMessageText(message, messagesById))
                .filter(StringUtils::hasText)
                .toList());
        reportDTO.setTrainingScene(session.getTrainingScene());
        reportDTO.setTargetSkillDomain(session.getTargetSkillDomain());
        reportDTO.setTargetSkillCodes(readStringList(session.getTargetSkillCodes()));
        reportDTO.setTargetLevel(session.getTargetLevel());
        reportDTO.setProjectEvidenceIds(readLongList(session.getProjectEvidenceIds()));
        reportDTO.setFollowUpIntensity(session.getFollowUpIntensity());
        reportDTO.setTrainingContextSummary(session.getTrainingContextSummary());
        return reportDTO;
    }

    private String reportMessageText(InterviewMessage message, Map<Long, InterviewMessage> messagesById) {
        if (message == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "Role", message.getRole());
        appendLine(builder, "Type", message.getMessageType());
        appendLine(builder, "Question", firstText(message.getQuestionContent(), questionContentByParent(message, messagesById)));
        appendLine(builder, "CandidateAnswer", message.getUserAnswer());
        appendLine(builder, "AiComment", firstText(message.getAiComment(), message.getComment()));
        appendLine(builder, "Score", message.getScore() == null ? null : message.getScore().toString());
        appendLine(builder, "Content", message.getContent());
        return builder.toString().trim();
    }

    private Map<Long, InterviewMessage> messageById(List<InterviewMessage> messages) {
        Map<Long, InterviewMessage> result = new LinkedHashMap<>();
        if (messages == null) {
            return result;
        }
        for (InterviewMessage message : messages) {
            if (message != null && message.getId() != null) {
                result.putIfAbsent(message.getId(), message);
            }
        }
        return result;
    }

    private String questionContentByParent(InterviewMessage message, Map<Long, InterviewMessage> messagesById) {
        if (message == null || message.getParentMessageId() == null) {
            return null;
        }
        InterviewMessage parent = messagesById == null ? null : messagesById.get(message.getParentMessageId());
        return parent == null ? null : firstText(parent.getQuestionContent(), parent.getContent());
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
        InterviewReportVO vo = enrichReportWithJdContext(InterviewConvert.toReportVO(report), session);
        if (vo != null && session != null) {
            vo.setVoiceTraces(interviewVoiceService.listSubmittedVoiceTraces(session.getId(), session.getUserId()));
        }
        return vo;
    }

    private InterviewReportVO enrichReportWithJdContext(InterviewReportVO vo, InterviewSession session) {
        if (vo == null || session == null) {
            return vo;
        }
        vo.setTargetJobId(session.getTargetJobId());
        vo.setApplicationId(session.getApplicationId());
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
        LocalDateTime endTime = LocalDateTime.now();
        int claimed = sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                .eq(InterviewSession::getId, session.getId())
                .eq(InterviewSession::getUserId, session.getUserId())
                .and(wrapper -> wrapper
                        .in(InterviewSession::getReportStatus, List.of(
                                ReportStatusEnum.NOT_GENERATED.name(),
                                ReportStatusEnum.FAILED.name()))
                        .or()
                        .isNull(InterviewSession::getReportStatus))
                .set(InterviewSession::getReportStatus, ReportStatusEnum.GENERATING.name())
                .set(InterviewSession::getStatus, InterviewStatusEnum.REPORT_GENERATING.name())
                .set(InterviewSession::getEndTime, endTime)
                .set(InterviewSession::getFailureReason, null));
        if (claimed != 1) {
            InterviewReport latest = currentReport(session.getId());
            InterviewSession latestSession = sessionMapper.selectById(session.getId());
            InterviewSession responseSession = latestSession == null ? session : latestSession;
            if (latest == null) {
                latest = new InterviewReport();
                latest.setSessionId(session.getId());
                latest.setUserId(session.getUserId());
                latest.setStatus(ReportStatusEnum.GENERATING.name());
            }
            return new PreparedReportGeneration(
                    latest, buildExistingReportResponse(responseSession, latest), false);
        }
        session.setReportStatus(ReportStatusEnum.GENERATING.name());
        session.setStatus(InterviewStatusEnum.REPORT_GENERATING.name());
        session.setEndTime(endTime);
        session.setFailureReason(null);

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
        try {
            if (!saveReport(report)) {
                InterviewReport latest = currentReport(session.getId());
                InterviewReport responseReport = latest == null ? report : latest;
                return new PreparedReportGeneration(
                        responseReport, buildExistingReportResponse(session, responseReport), false);
            }
        } catch (DuplicateKeyException ex) {
            InterviewReport latest = currentReport(session.getId());
            InterviewReport responseReport = latest == null ? report : latest;
            return new PreparedReportGeneration(
                    responseReport, buildExistingReportResponse(session, responseReport), false);
        }

        FinishInterviewVO vo = new FinishInterviewVO();
        vo.setId(session.getId());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setReport(toReportVO(report, session));
        return new PreparedReportGeneration(report, vo, true);
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
        if (question == null || !StringUtils.hasText(question.getContent())) {
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
        applyTrainingContext(session, aiDTO);
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
        String generationKey = questionGenerationKey(session, parentMessageId, followUp);
        try {
            return transactionTemplate.execute(status -> saveGeneratedQuestion(session, stage, question,
                    questionContent, parentMessageId, followUp, followUpCount, generationKey));
        } catch (DuplicateKeyException ex) {
            if (!StringUtils.hasText(generationKey)) {
                throw ex;
            }
            InterviewMessage existing = messageMapper.selectOne(new LambdaQueryWrapper<InterviewMessage>()
                    .eq(InterviewMessage::getSessionId, session.getId())
                    .eq(InterviewMessage::getGenerationKey, generationKey)
                    .eq(InterviewMessage::getDeleted, CommonConstants.NO)
                    .last("limit 1"));
            if (existing == null) {
                throw ex;
            }
            InterviewStage existingStage = existing.getStageId() == null
                    ? stage
                    : stageMapper.selectById(existing.getStageId());
            return toCurrentQuestionVO(session, existingStage == null ? stage : existingStage, existing);
        }
    }

    private CurrentQuestionVO saveGeneratedQuestion(InterviewSession session, InterviewStage stage, InnerQuestionVO question,
                                                    String questionContent, Long parentMessageId,
                                                    boolean followUp, int followUpCount,
                                                    String generationKey) {
        session.setCurrentQuestionId(question == null ? null : question.getId());
        session.setCurrentQuestionGroupId(question == null ? null : question.getGroupId());
        InterviewMessage message = saveMessage(session, stage, question, "AI", followUp ? "FOLLOW_UP" : "QUESTION",
                questionContent, null, null, parentMessageId, followUp, followUpCount, null, null, generationKey);
        sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                .eq(InterviewSession::getId, session.getId())
                .eq(InterviewSession::getUserId, session.getUserId())
                .notIn(InterviewSession::getStatus, List.of(
                        InterviewStatusEnum.COMPLETED.name(),
                        InterviewStatusEnum.CANCELED.name(),
                        InterviewStatusEnum.REPORT_GENERATING.name()))
                .set(InterviewSession::getCurrentStageId, stage == null ? null : stage.getId())
                .set(InterviewSession::getCurrentQuestionId, session.getCurrentQuestionId())
                .set(InterviewSession::getCurrentQuestionGroupId, session.getCurrentQuestionGroupId())
                .set(InterviewSession::getStatus, InterviewStatusEnum.WAITING_ANSWER.name()));
        session.setStatus(InterviewStatusEnum.WAITING_ANSWER.name());
        return toCurrentQuestionVO(session, stage, message);
    }

    private String questionGenerationKey(
            InterviewSession session, Long parentMessageId, boolean followUp) {
        if (session == null
                || parentMessageId != null
                || followUp
                || safeInt(session.getAnsweredQuestionCount()) > 0) {
            return null;
        }
        return "FIRST_QUESTION";
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
        return saveMessage(session, stage, question, role, type, content, score, comment,
                parentMessageId, isFollowUp, followUpCount, followUpReason, knowledgePoints, null);
    }

    private InterviewMessage saveMessage(InterviewSession session, InterviewStage stage, InnerQuestionVO question,
                                         String role, String type, String content, Integer score, String comment,
                                         Long parentMessageId, Boolean isFollowUp, Integer followUpCount,
                                         String followUpReason, String knowledgePoints, String generationKey) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(session.getId());
        message.setStageId(stage == null ? null : stage.getId());
        message.setQuestionId(question == null ? null : question.getId());
        message.setQuestionGroupId(question == null ? null : question.getGroupId());
        message.setParentMessageId(parentMessageId);
        message.setGenerationKey(generationKey);
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
        followUpDTO.setTrainingScene(evaluateDTO.getTrainingScene());
        followUpDTO.setTargetSkillDomain(evaluateDTO.getTargetSkillDomain());
        followUpDTO.setTargetSkillCodes(evaluateDTO.getTargetSkillCodes());
        followUpDTO.setTargetLevel(evaluateDTO.getTargetLevel());
        followUpDTO.setProjectEvidenceIds(evaluateDTO.getProjectEvidenceIds());
        followUpDTO.setProjectEvidenceContext(evaluateDTO.getProjectEvidenceContext());
        followUpDTO.setTrainingContextSummary(evaluateDTO.getTrainingContextSummary());
        followUpDTO.setFollowUpIntensity(evaluateDTO.getFollowUpIntensity());
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
        report.setRubricScores(firstText(aiReport.getRubricScores(), buildFallbackRubricScores(messages, answerCount)));
        report.setFollowUpTree(firstText(aiReport.getFollowUpTree(), buildFallbackFollowUpTree(messages)));
        report.setAdviceEvidence(firstText(aiReport.getAdviceEvidence(), buildFallbackAdviceEvidence(report, messages, answerCount)));
        report.setAbilityProfileUpdates(firstText(aiReport.getAbilityProfileUpdates(), buildFallbackAbilityProfileUpdates(messages, answerCount)));
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
        report.setRubricScores(firstText(aiReport == null ? null : aiReport.getRubricScores(),
                buildFallbackRubricScores(messages, answerCount)));
        report.setFollowUpTree(firstText(aiReport == null ? null : aiReport.getFollowUpTree(),
                buildFallbackFollowUpTree(messages)));
        report.setAdviceEvidence(firstText(aiReport == null ? null : aiReport.getAdviceEvidence(),
                buildFallbackAdviceEvidence(report, messages, answerCount)));
        report.setAbilityProfileUpdates(firstText(aiReport == null ? null : aiReport.getAbilityProfileUpdates(),
                buildFallbackAbilityProfileUpdates(messages, answerCount)));
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
        return markReportSampleInsufficient(session, report, null);
    }

    private InterviewReport markReportSampleInsufficient(
            InterviewSession session, InterviewReport report, String generationToken) {
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
        boolean saved = StringUtils.hasText(generationToken)
                ? updateCurrentReportAttempt(report, generationToken)
                : saveReport(report);
        if (!saved) {
            return report;
        }

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

    private boolean saveReport(InterviewReport report) {
        if (report.getId() == null) {
            return reportMapper.insert(report) == 1;
        } else {
            return reportMapper.updateById(report) == 1;
        }
    }

    private boolean claimReportAttempt(
            InterviewReport report, String previousStatus, String previousGenerationToken) {
        if (report == null) {
            return false;
        }
        if (report.getId() == null) {
            try {
                return reportMapper.insert(report) == 1;
            } catch (DuplicateKeyException ex) {
                return false;
            }
        }
        LambdaUpdateWrapper<InterviewReport> wrapper = new LambdaUpdateWrapper<InterviewReport>()
                .eq(InterviewReport::getId, report.getId())
                .eq(InterviewReport::getSessionId, report.getSessionId())
                .eq(InterviewReport::getDeleted, CommonConstants.NO);
        if (StringUtils.hasText(previousStatus)) {
            wrapper.eq(InterviewReport::getStatus, previousStatus);
        } else {
            wrapper.isNull(InterviewReport::getStatus);
        }
        if (StringUtils.hasText(previousGenerationToken)) {
            wrapper.eq(InterviewReport::getGenerationToken, previousGenerationToken);
        } else {
            wrapper.isNull(InterviewReport::getGenerationToken);
        }
        return reportMapper.update(report, wrapper) == 1;
    }

    private boolean updateCurrentReportAttempt(InterviewReport report, String generationToken) {
        if (report == null || report.getId() == null || report.getSessionId() == null) {
            return false;
        }
        LambdaUpdateWrapper<InterviewReport> wrapper = new LambdaUpdateWrapper<InterviewReport>()
                .eq(InterviewReport::getId, report.getId())
                .eq(InterviewReport::getSessionId, report.getSessionId())
                .eq(InterviewReport::getStatus, ReportStatusEnum.GENERATING.name())
                .eq(InterviewReport::getDeleted, CommonConstants.NO);
        if (StringUtils.hasText(generationToken)) {
            wrapper.eq(InterviewReport::getGenerationToken, generationToken);
        } else {
            wrapper.isNull(InterviewReport::getGenerationToken);
        }
        return reportMapper.update(report, wrapper) == 1;
    }

    private void completeAgentInterviewTask(InterviewSession session, InterviewReport report) {
        if (session == null || !InterviewReportTrustPolicy.isTrustedForFormalAction(report)) {
            return;
        }
        agentBusinessActionNotifier.completeInterviewReport(session.getUserId(), session.getTargetJobId(),
                report.getId());
    }

    private void syncApplicationInterviewEvent(InterviewSession session, InterviewReport report) {
        if (session == null || session.getApplicationId() == null
                || !InterviewReportTrustPolicy.isTrustedForFormalAction(report)) {
            return;
        }
        JobApplicationEventSaveDTO dto = new JobApplicationEventSaveDTO();
        dto.setEventType("INTERVIEW_COMPLETED");
        dto.setEventTime(report.getGeneratedAt() == null ? LocalDateTime.now() : report.getGeneratedAt());
        dto.setSummary(StringUtils.hasText(report.getSummary()) ? report.getSummary() : "Interview report generated");

        Map<String, Object> review = new LinkedHashMap<>();
        review.put("source", "interview-report");
        review.put("interviewId", session.getId());
        review.put("reportId", report.getId());
        review.put("reportStatus", report.getStatus());
        review.put("totalScore", report.getTotalScore());
        if (session.getTargetJobId() != null) {
            review.put("targetJobId", session.getTargetJobId());
        }
        if (session.getMatchReportId() != null) {
            review.put("matchReportId", session.getMatchReportId());
        }
        dto.setReview(review);

        try {
            FeignResultUtils.unwrap(resumeFeignClient.createApplicationEvent(
                    session.getUserId(), session.getApplicationId(), dto));
        } catch (RuntimeException ex) {
            log.warn("Failed to sync interview report to application event, sessionId={}, applicationId={}, reportId={}",
                    session.getId(), session.getApplicationId(), report.getId(), ex);
        }
    }

    private String buildFallbackRubricScores(List<InterviewMessage> messages, int answerCount) {
        int baseScore = normalizeFivePointScore(averageAnswerScore(messages));
        boolean sampleInsufficient = answerCount < 2;
        String warning = sampleInsufficient ? "Sample is insufficient; this is a weak signal from saved interview answers." : null;
        List<Map<String, Object>> scores = new ArrayList<>();
        scores.add(rubricItem("EXPRESSION_STRUCTURE", baseScore,
                "Structure is estimated from saved answer reviews.",
                firstAnswerEvidence(messages), "Use STAR or context-action-result structure for each project answer.",
                sampleInsufficient, warning));
        scores.add(rubricItem("TECHNICAL_DEPTH", Math.max(1, baseScore - 1),
                "Technical depth is estimated from AI answer comments and knowledge points.",
                firstKnowledgeEvidence(messages), "Add implementation details, trade-offs, and boundary conditions.",
                sampleInsufficient, warning));
        scores.add(rubricItem("BUSINESS_UNDERSTANDING", baseScore,
                "Business understanding is estimated from project and scenario explanations.",
                firstAnswerEvidence(messages), "Connect technical choices to user impact and measurable results.",
                sampleInsufficient, warning));
        scores.add(rubricItem("RISK_AWARENESS", Math.max(1, baseScore - 1),
                "Risk awareness is estimated from follow-up reasons and missing edge cases.",
                firstFollowUpEvidence(messages), "Name failure modes, rollback strategy, monitoring, and data consistency risks.",
                sampleInsufficient, warning));
        scores.add(rubricItem("IMPLEMENTABILITY", baseScore,
                "Implementability is estimated from whether answers include concrete steps.",
                firstAnswerEvidence(messages), "Turn conclusions into steps, metrics, and verification methods.",
                sampleInsufficient, warning));
        return jsonArray(scores);
    }

    private Map<String, Object> rubricItem(String dimension, int score, String comment, String evidenceSummary,
                                           String improvementSuggestion, boolean sampleInsufficient, String warning) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dimension", dimension);
        item.put("score", score);
        item.put("comment", comment);
        item.put("evidenceSummary", truncate(firstText(evidenceSummary, "No stable evidence yet."), 160));
        item.put("improvementSuggestion", improvementSuggestion);
        item.put("sampleInsufficient", sampleInsufficient);
        item.put("sampleWarning", warning);
        return item;
    }

    private String buildFallbackFollowUpTree(List<InterviewMessage> messages) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (InterviewMessage followUp : messages == null ? List.<InterviewMessage>of() : messages) {
            if (!"AI".equalsIgnoreCase(followUp.getRole()) || !"FOLLOW_UP".equalsIgnoreCase(followUp.getMessageType())) {
                continue;
            }
            InterviewMessage question = messageById(messages, followUp.getParentMessageId());
            InterviewMessage answer = firstUserAnswer(messages, followUp.getParentMessageId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionMessageId", followUp.getParentMessageId());
            item.put("answerMessageId", answer == null ? null : answer.getId());
            item.put("followUpMessageId", followUp.getId());
            item.put("questionSummary", truncate(firstText(question == null ? null : question.getQuestionContent(),
                    question == null ? null : question.getContent()), 160));
            item.put("answerSummary", truncate(firstText(answer == null ? null : answer.getUserAnswer(),
                    answer == null ? null : answer.getContent()), 160));
            item.put("followUpQuestion", truncate(followUp.getContent(), 160));
            item.put("followUpIntent", "CLARIFY_RISK");
            item.put("followUpReason", truncate(firstText(followUp.getFollowUpReason(), "Need to verify depth or risk boundary."), 160));
            item.put("exposedRisk", inferExposedRisk(followUp));
            item.put("evidenceSource", "INTERVIEW_MESSAGE");
            tree.add(item);
        }
        return jsonArray(tree);
    }

    private String buildFallbackAdviceEvidence(InterviewReport report, List<InterviewMessage> messages, int answerCount) {
        boolean sampleInsufficient = answerCount < 2;
        List<Map<String, Object>> advice = new ArrayList<>();
        advice.add(adviceItem("Replay weak interview answers", "PRACTICE_SKILL", "MEDIUM",
                "Use the report weak points and follow-up trace to run one targeted practice round.",
                "/interviews/create?source=interviewReport&reportId=" + (report == null ? "" : report.getId()),
                report == null ? null : report.getId(), firstAnswerEvidence(messages), sampleInsufficient));
        if (hasFollowUps(messages)) {
            advice.add(adviceItem("Close follow-up risk gaps", "FOLLOW_UP_REVIEW", "MEDIUM",
                    "Review every follow-up reason and prepare a stronger second answer.",
                    "/agent/today?source=interviewReport&reportId=" + (report == null ? "" : report.getId()),
                    report == null ? null : report.getId(), firstFollowUpEvidence(messages), sampleInsufficient));
        }
        return jsonArray(advice);
    }

    private Map<String, Object> adviceItem(String title, String type, String confidence, String content,
                                           String actionUrl, Long reportId, String evidence, boolean sampleInsufficient) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("adviceType", type);
        item.put("confidence", sampleInsufficient ? "LOW" : confidence);
        item.put("content", content);
        item.put("actionUrl", actionUrl);
        item.put("sampleInsufficient", sampleInsufficient);
        item.put("sampleWarning", sampleInsufficient
                ? "Sample is insufficient; advice is a candidate next step, not a strong conclusion."
                : null);
        item.put("feedbackStatus", "NONE");
        List<Map<String, Object>> sources = new ArrayList<>();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sourceType", "INTERVIEW_REPORT");
        source.put("sourceId", reportId);
        source.put("sourceSummary", truncate(firstText(evidence, "Generated from interview report summary."), 160));
        sources.add(source);
        item.put("evidenceSources", sources);
        return item;
    }

    private String buildFallbackAbilityProfileUpdates(List<InterviewMessage> messages, int answerCount) {
        boolean sampleInsufficient = answerCount < 2;
        List<Map<String, Object>> updates = new ArrayList<>();
        List<String> skillCodes = extractKnowledgePoints(messages);
        for (String skillCode : skillCodes.stream().limit(5).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skillCode", skillCode);
            item.put("candidateStatus", normalizeFivePointScore(averageAnswerScore(messages)) >= 3 ? "BASIC" : "WEAK");
            item.put("confidence", sampleInsufficient ? "LOW" : "MEDIUM");
            item.put("evidenceCount", answerCount);
            item.put("sampleInsufficient", sampleInsufficient);
            item.put("sampleWarning", sampleInsufficient
                    ? "Need more interview samples before automatically updating the ability profile."
                    : null);
            updates.add(item);
        }
        return jsonArray(updates);
    }

    private int normalizeFivePointScore(Integer score) {
        if (score == null || score <= 0) {
            return 2;
        }
        if (score > 5) {
            return Math.max(1, Math.min(5, Math.round(score / 20.0f)));
        }
        return Math.max(1, Math.min(5, score));
    }

    private String firstAnswerEvidence(List<InterviewMessage> messages) {
        if (messages == null) {
            return null;
        }
        return messages.stream()
                .filter(this::isUserAnswer)
                .map(message -> firstText(message.getAiComment(), message.getComment(), message.getUserAnswer(), message.getContent()))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String firstKnowledgeEvidence(List<InterviewMessage> messages) {
        if (messages == null) {
            return null;
        }
        return messages.stream()
                .map(InterviewMessage::getKnowledgePoints)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(firstAnswerEvidence(messages));
    }

    private String firstFollowUpEvidence(List<InterviewMessage> messages) {
        if (messages == null) {
            return null;
        }
        return messages.stream()
                .filter(message -> "AI".equalsIgnoreCase(message.getRole()))
                .filter(message -> "FOLLOW_UP".equalsIgnoreCase(message.getMessageType()))
                .map(message -> firstText(message.getFollowUpReason(), message.getContent()))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(firstAnswerEvidence(messages));
    }

    private InterviewMessage messageById(List<InterviewMessage> messages, Long messageId) {
        if (messages == null || messageId == null) {
            return null;
        }
        return messages.stream()
                .filter(message -> messageId.equals(message.getId()))
                .findFirst()
                .orElse(null);
    }

    private InterviewMessage firstUserAnswer(List<InterviewMessage> messages, Long parentMessageId) {
        if (messages == null || parentMessageId == null) {
            return null;
        }
        return messages.stream()
                .filter(this::isUserAnswer)
                .filter(message -> parentMessageId.equals(message.getParentMessageId()))
                .findFirst()
                .orElse(null);
    }

    private String inferExposedRisk(InterviewMessage followUp) {
        String text = firstText(followUp == null ? null : followUp.getFollowUpReason(),
                followUp == null ? null : followUp.getContent());
        if (!StringUtils.hasText(text)) {
            return "Risk needs further verification.";
        }
        String lower = text.toLowerCase();
        if (lower.contains("risk") || lower.contains("fail") || lower.contains("rollback")) {
            return "Risk awareness may be insufficient.";
        }
        if (lower.contains("why") || lower.contains("how") || lower.contains("detail")) {
            return "Technical depth may need more evidence.";
        }
        return "Answer may need clearer evidence or boundary conditions.";
    }

    private boolean hasFollowUps(List<InterviewMessage> messages) {
        return messages != null && messages.stream()
                .anyMatch(message -> "AI".equalsIgnoreCase(message.getRole())
                        && "FOLLOW_UP".equalsIgnoreCase(message.getMessageType()));
    }

    private List<String> extractKnowledgePoints(List<InterviewMessage> messages) {
        if (messages == null) {
            return List.of();
        }
        return messages.stream()
                .map(InterviewMessage::getKnowledgePoints)
                .filter(StringUtils::hasText)
                .flatMap(value -> List.of(value.split("[,，;；/\\s]+")).stream())
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String jsonArray(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void validateResumeOwnership(Long userId, String mode, CreateInterviewDTO dto) {
        boolean resumeRequired = !isTrainingWithoutResumeAllowed(dto)
                && Boolean.TRUE.equals(dto.getBasedOnResume())
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

    private InnerJobApplicationPackageVO validateApplicationPackageBinding(
            Long userId, Long applicationPackageId, CreateInterviewDTO request) {
        if (applicationPackageId == null) {
            return null;
        }
        InnerJobApplicationPackageVO applicationPackage;
        try {
            applicationPackage = FeignResultUtils.unwrap(
                    resumeFeignClient.getApplicationPackage(applicationPackageId));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "求职申请包暂时无法校验，不能创建面试");
        }
        Long returnedPackageId = parseApplicationPackageId(
                applicationPackage == null ? null : applicationPackage.getId());
        if (applicationPackage == null
                || !applicationPackageId.equals(returnedPackageId)
                || !userId.equals(applicationPackage.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "求职申请包不存在或无权使用");
        }
        if (Boolean.TRUE.equals(applicationPackage.getFallback())
                || !"REAL".equalsIgnoreCase(String.valueOf(applicationPackage.getResultSource()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "求职申请包来源不可信，不能创建正式模拟面试");
        }
        bindContext(request.getApplicationId(), applicationPackage.getJobApplicationId(),
                request::setApplicationId, "投递记录与求职申请包不一致");
        bindContext(request.getTargetJobId(), applicationPackage.getTargetJobId(),
                request::setTargetJobId, "目标岗位与求职申请包不一致");
        bindContext(request.getJdAnalysisId(), applicationPackage.getJdAnalysisId(),
                request::setJdAnalysisId, "JD 分析与求职申请包不一致");
        bindContext(request.getResumeVersionId(), applicationPackage.getRecommendedResumeVersionId(),
                request::setResumeVersionId, "简历版本与求职申请包不一致");
        bindContext(request.getMatchReportId(), applicationPackage.getMatchReportId(),
                request::setMatchReportId, "匹配报告与求职申请包不一致");
        bindProjectEvidenceContext(request, applicationPackage.getProjectEvidenceIds(),
                "项目素材与求职申请包不一致");
        request.setApplicationPackageId(String.valueOf(applicationPackageId));
        return applicationPackage;
    }

    private InnerResumeJobMatchReportVO validateSuccessfulMatchReportContext(
            Long userId, CreateInterviewDTO request) {
        if (request == null || request.getMatchReportId() == null) {
            return null;
        }
        InnerResumeJobMatchReportVO report = FeignResultUtils.unwrap(
                resumeFeignClient.getSuccessResumeJobMatchReport(request.getMatchReportId()));
        if (report == null
                || !request.getMatchReportId().equals(report.getReportId())
                || !userId.equals(report.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "匹配报告不存在，不能作为面试依据");
        }
        if (!"SUCCESS".equalsIgnoreCase(String.valueOf(report.getStatus()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "匹配报告未成功，不能作为面试依据");
        }
        if (report.getResumeId() == null
                || report.getTargetJobId() == null
                || report.getResumeVersionId() == null
                || report.getJdAnalysisId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "匹配报告上下文不完整，不能作为面试依据");
        }
        bindContext(request.getResumeId(), report.getResumeId(),
                request::setResumeId, "面试简历与匹配报告不一致");
        bindContext(request.getTargetJobId(), report.getTargetJobId(),
                request::setTargetJobId, "目标岗位与匹配报告不一致");
        bindContext(request.getResumeVersionId(), report.getResumeVersionId(),
                request::setResumeVersionId, "简历版本与匹配报告不一致");
        bindContext(request.getJdAnalysisId(), report.getJdAnalysisId(),
                request::setJdAnalysisId, "JD 分析与匹配报告不一致");
        return report;
    }

    private InnerJobApplicationSummaryVO validateApplicationBinding(Long userId, CreateInterviewDTO request) {
        if (request == null || request.getApplicationId() == null) {
            return null;
        }
        InnerJobApplicationSummaryVO application;
        try {
            application = FeignResultUtils.unwrap(
                    resumeFeignClient.getApplicationSummary(userId, request.getApplicationId()));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "投递记录暂时无法校验，不能绑定面试");
        }
        if (application == null || application.getId() == null || !userId.equals(application.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "投递记录不存在，不能绑定面试");
        }
        if (!request.getApplicationId().equals(application.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "投递记录不存在，不能绑定面试");
        }
        bindContext(request.getTargetJobId(), application.getTargetJobId(),
                request::setTargetJobId, "面试岗位与投递记录不一致");
        bindContext(request.getMatchReportId(), application.getMatchReportId(),
                request::setMatchReportId, "面试匹配报告与投递记录不一致");
        bindContext(request.getResumeVersionId(), application.getResumeVersionId(),
                request::setResumeVersionId, "简历版本与投递记录不一致");
        return application;
    }

    private InnerSkillProfileVO validateSkillProfileContext(Long userId, CreateInterviewDTO request) {
        if (request == null || request.getSkillProfileId() == null) {
            return null;
        }
        InnerSkillProfileVO profile = FeignResultUtils.unwrap(
                resumeFeignClient.getSkillProfile(request.getSkillProfileId()));
        if (profile == null
                || !request.getSkillProfileId().equals(profile.getProfileId())
                || !userId.equals(profile.getUserId())
                || !"SUCCESS".equalsIgnoreCase(profile.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像不存在或暂时不可用");
        }
        if ("INTERVIEW_REPORT".equalsIgnoreCase(profile.getSourceType())) {
            InterviewReport sourceReport = reportMapper.selectOne(new LambdaQueryWrapper<InterviewReport>()
                    .eq(InterviewReport::getSessionId, profile.getSourceBizId())
                    .eq(InterviewReport::getUserId, userId)
                    .eq(InterviewReport::getDeleted, CommonConstants.NO)
                    .orderByDesc(InterviewReport::getId)
                    .last("limit 1"));
            if (!InterviewReportTrustPolicy.isTrustedForFormalAction(sourceReport)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像来源报告可信度不足");
            }
        } else if (!"RESUME_JOB_MATCH".equalsIgnoreCase(profile.getSourceType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像来源不可信");
        }
        if (profile.getTargetJobId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像缺少目标岗位上下文");
        }
        bindContext(request.getTargetJobId(), profile.getTargetJobId(),
                request::setTargetJobId, "目标岗位与能力画像不一致");
        if (profile.getMatchReportId() != null) {
            bindContext(request.getMatchReportId(), profile.getMatchReportId(),
                    request::setMatchReportId, "匹配报告与能力画像不一致");
        }
        return profile;
    }

    private void validateApplicationContextMatches(
            InnerJobApplicationSummaryVO application, CreateInterviewDTO request) {
        if (application == null) {
            return;
        }
        requireSameContext(request.getTargetJobId(), application.getTargetJobId(),
                "面试岗位与投递记录不一致");
        requireSameContext(request.getMatchReportId(), application.getMatchReportId(),
                "面试匹配报告与投递记录不一致");
        requireSameContext(request.getResumeVersionId(), application.getResumeVersionId(),
                "简历版本与投递记录不一致");
    }

    private void validateApplicationPackageContextMatches(
            InnerJobApplicationPackageVO applicationPackage, CreateInterviewDTO request) {
        if (applicationPackage == null) {
            return;
        }
        requireSameContext(request.getApplicationId(), applicationPackage.getJobApplicationId(),
                "投递记录与求职申请包不一致");
        requireSameContext(request.getTargetJobId(), applicationPackage.getTargetJobId(),
                "目标岗位与求职申请包不一致");
        requireSameContext(request.getJdAnalysisId(), applicationPackage.getJdAnalysisId(),
                "JD 分析与求职申请包不一致");
        requireSameContext(request.getResumeVersionId(), applicationPackage.getRecommendedResumeVersionId(),
                "简历版本与求职申请包不一致");
        requireSameContext(request.getMatchReportId(), applicationPackage.getMatchReportId(),
                "匹配报告与求职申请包不一致");
        Set<Long> requestedEvidence = Set.copyOf(sanitizeLongs(request.getProjectEvidenceIds()));
        Set<Long> packageEvidence = Set.copyOf(sanitizeLongs(applicationPackage.getProjectEvidenceIds()));
        if (!requestedEvidence.equals(packageEvidence)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "项目素材与求职申请包不一致");
        }
    }

    private void validateAnchoredVersionContext(
            InnerJobApplicationPackageVO applicationPackage,
            InnerJobApplicationSummaryVO application,
            InnerResumeJobMatchReportVO matchReport,
            CreateInterviewDTO request) {
        if (request.getResumeVersionId() != null
                && applicationPackage == null
                && application == null
                && matchReport == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历版本缺少可验证的业务上下文");
        }
        if (request.getJdAnalysisId() != null
                && applicationPackage == null
                && matchReport == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "JD 分析缺少可验证的业务上下文");
        }
    }

    private void bindProjectEvidenceContext(
            CreateInterviewDTO request, List<Long> authoritativeIds, String errorMessage) {
        List<Long> requestedIds = sanitizeLongs(request.getProjectEvidenceIds());
        List<Long> sourceIds = sanitizeLongs(authoritativeIds);
        if (request.getProjectEvidenceIds() != null
                && request.getProjectEvidenceIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "项目素材 ID 必须为正整数");
        }
        if (!requestedIds.isEmpty() && !Set.copyOf(requestedIds).equals(Set.copyOf(sourceIds))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, errorMessage);
        }
        if (requestedIds.isEmpty() && !sourceIds.isEmpty()) {
            request.setProjectEvidenceIds(sourceIds);
        }
    }

    private void bindContext(
            Long requested, Long authoritative, Consumer<Long> setter, String errorMessage) {
        if (requested != null && !Objects.equals(requested, authoritative)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, errorMessage);
        }
        if (requested == null && authoritative != null) {
            setter.accept(authoritative);
        }
    }

    private void requireSameContext(Long actual, Long expected, String errorMessage) {
        if (actual != null && !Objects.equals(actual, expected)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, errorMessage);
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

    private Long parseApplicationPackageId(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        try {
            Long parsed = Long.valueOf(normalized);
            if (parsed <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "applicationPackageId 必须为正整数");
        }
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

    private void applyTrainingContext(InterviewSession session, GenerateInterviewQuestionDTO aiDTO) {
        if (session == null || aiDTO == null) {
            return;
        }
        aiDTO.setTrainingScene(session.getTrainingScene());
        aiDTO.setTargetSkillDomain(session.getTargetSkillDomain());
        aiDTO.setTargetSkillCodes(readStringList(session.getTargetSkillCodes()));
        aiDTO.setTargetLevel(session.getTargetLevel());
        aiDTO.setProjectEvidenceIds(readLongList(session.getProjectEvidenceIds()));
        aiDTO.setProjectEvidenceContext(session.getTrainingContextSummary());
        aiDTO.setTrainingContextSummary(session.getTrainingContextSummary());
        aiDTO.setFollowUpIntensity(session.getFollowUpIntensity());
    }

    private void applyTrainingContext(InterviewSession session, EvaluateAnswerDTO aiDTO) {
        if (session == null || aiDTO == null) {
            return;
        }
        aiDTO.setTrainingScene(session.getTrainingScene());
        aiDTO.setTargetSkillDomain(session.getTargetSkillDomain());
        aiDTO.setTargetSkillCodes(readStringList(session.getTargetSkillCodes()));
        aiDTO.setTargetLevel(session.getTargetLevel());
        aiDTO.setProjectEvidenceIds(readLongList(session.getProjectEvidenceIds()));
        aiDTO.setProjectEvidenceContext(session.getTrainingContextSummary());
        aiDTO.setTrainingContextSummary(session.getTrainingContextSummary());
        aiDTO.setFollowUpIntensity(session.getFollowUpIntensity());
    }

    private String buildTrainingContextSummary(Long userId, CreateInterviewDTO request,
                                               List<String> targetSkillCodes, List<Long> projectEvidenceIds) {
        String trainingScene = normalizeText(request.getTrainingScene());
        Map<String, Object> applicationContext = buildApplicationContextSummary(request);
        if (!StringUtils.hasText(trainingScene)
                && !StringUtils.hasText(request.getTargetSkillDomain())
                && targetSkillCodes.isEmpty()
                && projectEvidenceIds.isEmpty()
                && applicationContext.isEmpty()) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        if (!applicationContext.isEmpty()) {
            summary.put("applicationContext", applicationContext);
        }
        summary.put("trainingScene", trainingScene);
        summary.put("targetSkillDomain", normalizeText(request.getTargetSkillDomain()));
        summary.put("targetSkillCodes", targetSkillCodes);
        summary.put("targetLevel", normalizeText(request.getTargetLevel()));
        summary.put("projectEvidenceIds", projectEvidenceIds);
        summary.put("followUpIntensity", normalizeText(request.getFollowUpIntensity()));
        if (!projectEvidenceIds.isEmpty()) {
            List<InnerProjectEvidenceTrainingContextVO> projects = loadProjectEvidenceTrainingContext(userId, projectEvidenceIds);
            if (TRAINING_SCENE_PROJECT_DEEP_DIVE.equals(trainingScene) && projects.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "项目素材不可用，不能用于项目深挖训练");
            }
            summary.put("projectEvidenceSummaries", projects);
        }
        return toJsonOrNull(summary);
    }

    private Map<String, Object> buildApplicationContextSummary(CreateInterviewDTO request) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (request == null) {
            return context;
        }
        putIfPresent(context, "applicationId", request.getApplicationId());
        putIfPresent(context, "applicationPackageId", normalizeText(request.getApplicationPackageId()));
        putIfPresent(context, "targetJobId", request.getTargetJobId());
        putIfPresent(context, "jdAnalysisId", request.getJdAnalysisId());
        putIfPresent(context, "resumeVersionId", request.getResumeVersionId());
        putIfPresent(context, "matchReportId", request.getMatchReportId());
        putIfPresent(context, "resumeId", request.getResumeId());
        return context;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && !StringUtils.hasText(stringValue)) {
            return;
        }
        target.put(key, value);
    }

    private List<InnerProjectEvidenceTrainingContextVO> loadProjectEvidenceTrainingContext(Long userId, List<Long> projectEvidenceIds) {
        if (userId == null || projectEvidenceIds == null || projectEvidenceIds.isEmpty()) {
            return List.of();
        }
        try {
            List<InnerProjectEvidenceTrainingContextVO> result = FeignResultUtils.unwrap(
                    resumeFeignClient.listProjectEvidenceTrainingContext(userId, projectEvidenceIds));
            List<InnerProjectEvidenceTrainingContextVO> contexts = result == null
                    ? List.of()
                    : result.stream()
                    .filter(Objects::nonNull)
                    .filter(item -> item.getProjectEvidenceId() != null)
                    .toList();
            Set<Long> requestedIds = Set.copyOf(projectEvidenceIds);
            Set<Long> returnedIds = contexts.stream()
                    .map(InnerProjectEvidenceTrainingContextVO::getProjectEvidenceId)
                    .collect(java.util.stream.Collectors.toSet());
            if (!requestedIds.equals(returnedIds)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "项目素材不存在、无权使用或与当前上下文不一致");
            }
            return contexts;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "项目素材训练上下文暂时不可用");
        }
    }

    private boolean isTrainingWithoutResumeAllowed(CreateInterviewDTO dto) {
        if (dto == null) {
            return false;
        }
        String scene = normalizeText(dto.getTrainingScene());
        if (TRAINING_SCENE_JAVA_SPECIALTY.equals(scene)) {
            return true;
        }
        return TRAINING_SCENE_PROJECT_DEEP_DIVE.equals(scene)
                && dto.getProjectEvidenceIds() != null
                && !sanitizeLongs(dto.getProjectEvidenceIds()).isEmpty();
    }

    private List<String> sanitizeStrings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(20)
                .toList();
    }

    private List<Long> sanitizeLongs(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && value > 0)
                .distinct()
                .limit(10)
                .toList();
    }

    private String toJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> readStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(value, new TypeReference<>() {
            });
            return list == null ? List.of() : list;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Long> readLongList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            List<Long> list = objectMapper.readValue(value, new TypeReference<>() {
            });
            return list == null ? List.of() : list;
        } catch (Exception ex) {
            return List.of();
        }
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

    private record AnswerContext(InterviewSession session, String previousStatus, InterviewStage stage,
                                 InterviewMessage currentAiQuestion, InnerQuestionVO question,
                                 InterviewMessage answerMessage, InterviewTranscriptVO voiceTranscript) {
    }

    private record FollowUpPlan(String question, String reason, Long aiCallLogId) {
    }

    private record AnswerPersistence(InterviewMessage evaluationMessage, InterviewMessage followUpMessage,
                                     Long followUpAiCallLogId, NextActionEnum nextAction,
                                     CurrentQuestionVO nextQuestion, InterviewStage questionGenerationStage) {
    }

    private record PreparedReportGeneration(
            InterviewReport report, FinishInterviewVO response, boolean dispatchRequired) {
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
