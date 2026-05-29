package com.codecoachai.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.question.domain.dto.QuestionDuplicateEvalCaseQueryDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateEvalCaseSaveDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateEvalRunRequestDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateEvaluationDTO;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionDuplicateEvalCase;
import com.codecoachai.question.domain.entity.QuestionDuplicateEvalResult;
import com.codecoachai.question.domain.entity.QuestionDuplicateEvalRun;
import com.codecoachai.question.domain.vo.QuestionDuplicateEvalCaseVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateEvalRunVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateEvaluationVO;
import com.codecoachai.question.mapper.QuestionDuplicateEvalCaseMapper;
import com.codecoachai.question.mapper.QuestionDuplicateEvalResultMapper;
import com.codecoachai.question.mapper.QuestionDuplicateEvalRunMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.service.QuestionDuplicateEvaluationService;
import com.codecoachai.question.service.QuestionDuplicateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionDuplicateEvaluationServiceImpl implements QuestionDuplicateEvaluationService {

    private static final DateTimeFormatter RUN_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final QuestionDuplicateEvalCaseMapper evalCaseMapper;
    private final QuestionDuplicateEvalRunMapper evalRunMapper;
    private final QuestionDuplicateEvalResultMapper evalResultMapper;
    private final QuestionMapper questionMapper;
    private final QuestionDuplicateService duplicateService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<QuestionDuplicateEvalCaseVO> pageCases(QuestionDuplicateEvalCaseQueryDTO query) {
        QuestionDuplicateEvalCaseQueryDTO safeQuery = query == null ? new QuestionDuplicateEvalCaseQueryDTO() : query;
        long pageNo = defaultPage(safeQuery.getPageNo());
        long pageSize = defaultSize(safeQuery.getPageSize());
        Page<QuestionDuplicateEvalCase> page = evalCaseMapper.selectPage(Page.of(pageNo, pageSize), buildCaseWrapper(safeQuery));
        Map<Long, Question> questionMap = loadQuestions(page.getRecords());
        return PageResult.of(page.getRecords().stream()
                .map(item -> toCaseVO(item, questionMap))
                .toList(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionDuplicateEvalCaseVO saveCase(QuestionDuplicateEvalCaseSaveDTO dto) {
        if (dto == null || dto.getSourceQuestionId() == null || dto.getTargetQuestionId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "sourceQuestionId and targetQuestionId are required");
        }
        if (Objects.equals(dto.getSourceQuestionId(), dto.getTargetQuestionId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "sourceQuestionId and targetQuestionId must be different");
        }
        String expected = normalizeExpected(dto.getExpected());
        Question source = getQuestionOrThrow(dto.getSourceQuestionId());
        Question target = getQuestionOrThrow(dto.getTargetQuestionId());
        QuestionDuplicateEvalCase item = dto.getId() == null ? new QuestionDuplicateEvalCase() : evalCaseMapper.selectById(dto.getId());
        if (item == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question duplicate eval case not found");
        }
        item.setCaseId(firstText(dto.getCaseId(), defaultCaseId(dto.getSourceQuestionId(), dto.getTargetQuestionId())));
        item.setSourceQuestionId(source.getId());
        item.setTargetQuestionId(target.getId());
        item.setExpected(expected);
        item.setNote(dto.getNote());
        item.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        if (item.getId() == null) {
            item.setCreatedBy(SecurityAssert.requireLoginUserId());
            evalCaseMapper.insert(item);
        } else {
            evalCaseMapper.updateById(item);
        }
        return toCaseVO(item, Map.of(source.getId(), source, target.getId(), target));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCase(Long id) {
        if (id == null) {
            return;
        }
        evalCaseMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionDuplicateEvalRunVO run(QuestionDuplicateEvalRunRequestDTO dto) {
        List<QuestionDuplicateEvalCase> cases = loadRunCases(dto);
        QuestionDuplicateEvalRun run = new QuestionDuplicateEvalRun();
        LocalDateTime startedAt = LocalDateTime.now();
        run.setRunNo("QD-" + RUN_NO_FORMATTER.format(startedAt));
        run.setStatus("RUNNING");
        run.setSampleCount(cases.size());
        run.setEvaluatedCount(0);
        run.setPassedCount(0);
        run.setFailedCount(0);
        run.setMissingQuestionCount(0);
        run.setStartedAt(startedAt);
        run.setCreatedBy(SecurityAssert.requireLoginUserId());
        evalRunMapper.insert(run);

        try {
            QuestionDuplicateEvaluationDTO evaluationDTO = new QuestionDuplicateEvaluationDTO();
            evaluationDTO.setSamples(cases.stream().map(this::toEvaluationSample).toList());
            QuestionDuplicateEvaluationVO evaluation = duplicateService.evaluate(evaluationDTO);
            Map<String, QuestionDuplicateEvalCase> caseMap = caseIdMap(cases);
            for (QuestionDuplicateEvaluationVO.Item item : evaluation.getItems()) {
                evalResultMapper.insert(toResult(run.getId(), caseMap.get(item.getCaseId()), item));
            }
            run.setStatus("SUCCESS");
            run.setEvaluatedCount(evaluation.getEvaluatedCount());
            run.setPassedCount(evaluation.getPassedCount());
            run.setFailedCount(evaluation.getFailedCount());
            run.setMissingQuestionCount(evaluation.getMissingQuestionCount());
            run.setAccuracyRate(evaluation.getAccuracyRate());
            run.setFinishedAt(LocalDateTime.now());
            evalRunMapper.updateById(run);
            return getRun(run.getId());
        } catch (Exception ex) {
            run.setStatus("FAILED");
            run.setFinishedAt(LocalDateTime.now());
            run.setErrorMessage(truncate(ex.getMessage(), 512));
            evalRunMapper.updateById(run);
            throw ex;
        }
    }

    @Override
    public PageResult<QuestionDuplicateEvalRunVO> pageRuns(Long pageNo, Long pageSize) {
        long actualPageNo = defaultPage(pageNo);
        long actualPageSize = defaultSize(pageSize);
        Page<QuestionDuplicateEvalRun> page = evalRunMapper.selectPage(Page.of(actualPageNo, actualPageSize),
                new LambdaQueryWrapper<QuestionDuplicateEvalRun>().orderByDesc(QuestionDuplicateEvalRun::getCreatedAt));
        return PageResult.of(page.getRecords().stream()
                .map(run -> toRunVO(run, false))
                .toList(), page.getTotal(), actualPageNo, actualPageSize);
    }

    @Override
    public QuestionDuplicateEvalRunVO getRun(Long runId) {
        QuestionDuplicateEvalRun run = evalRunMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question duplicate eval run not found");
        }
        return toRunVO(run, true);
    }

    private LambdaQueryWrapper<QuestionDuplicateEvalCase> buildCaseWrapper(QuestionDuplicateEvalCaseQueryDTO query) {
        return new LambdaQueryWrapper<QuestionDuplicateEvalCase>()
                .eq(StringUtils.hasText(query.getExpected()), QuestionDuplicateEvalCase::getExpected, normalizeExpected(query.getExpected()))
                .eq(query.getEnabled() != null, QuestionDuplicateEvalCase::getEnabled, query.getEnabled())
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(QuestionDuplicateEvalCase::getCaseId, query.getKeyword())
                        .or()
                        .like(QuestionDuplicateEvalCase::getNote, query.getKeyword()))
                .orderByDesc(QuestionDuplicateEvalCase::getUpdatedAt);
    }

    private List<QuestionDuplicateEvalCase> loadRunCases(QuestionDuplicateEvalRunRequestDTO dto) {
        QuestionDuplicateEvalRunRequestDTO request = dto == null ? new QuestionDuplicateEvalRunRequestDTO() : dto;
        int limit = request.getLimit() == null ? 100 : Math.max(1, Math.min(request.getLimit(), 500));
        LambdaQueryWrapper<QuestionDuplicateEvalCase> wrapper = new LambdaQueryWrapper<QuestionDuplicateEvalCase>()
                .in(request.getCaseIds() != null && !request.getCaseIds().isEmpty(), QuestionDuplicateEvalCase::getId, request.getCaseIds())
                .eq(Boolean.TRUE.equals(request.getOnlyEnabled()), QuestionDuplicateEvalCase::getEnabled, 1)
                .orderByAsc(QuestionDuplicateEvalCase::getId)
                .last("limit " + limit);
        List<QuestionDuplicateEvalCase> cases = evalCaseMapper.selectList(wrapper);
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "No question duplicate eval cases matched");
        }
        return cases;
    }

    private QuestionDuplicateEvaluationDTO.Sample toEvaluationSample(QuestionDuplicateEvalCase item) {
        QuestionDuplicateEvaluationDTO.Sample sample = new QuestionDuplicateEvaluationDTO.Sample();
        sample.setCaseId(item.getCaseId());
        sample.setSourceQuestionId(item.getSourceQuestionId());
        sample.setTargetQuestionId(item.getTargetQuestionId());
        sample.setExpected(item.getExpected());
        sample.setNote(item.getNote());
        return sample;
    }

    private QuestionDuplicateEvalResult toResult(Long runId, QuestionDuplicateEvalCase sourceCase,
                                                 QuestionDuplicateEvaluationVO.Item item) {
        QuestionDuplicateEvalResult result = new QuestionDuplicateEvalResult();
        result.setRunId(runId);
        result.setEvalCaseId(sourceCase == null ? null : sourceCase.getId());
        result.setCaseId(item.getCaseId());
        result.setSourceQuestionId(item.getSourceQuestionId());
        result.setTargetQuestionId(item.getTargetQuestionId());
        result.setExpected(item.getExpected());
        result.setPredicted(item.getPredicted());
        result.setPassed(Boolean.TRUE.equals(item.getPassed()) ? 1 : 0);
        result.setMatchType(item.getMatchType());
        result.setScore(item.getScore());
        result.setScoreBand(item.getScoreBand());
        result.setScorePartsJson(writeJson(item.getScoreParts()));
        result.setReason(item.getReason());
        result.setNote(item.getNote());
        return result;
    }

    private Map<String, QuestionDuplicateEvalCase> caseIdMap(List<QuestionDuplicateEvalCase> cases) {
        Map<String, QuestionDuplicateEvalCase> result = new LinkedHashMap<>();
        for (QuestionDuplicateEvalCase item : cases) {
            result.put(item.getCaseId(), item);
        }
        return result;
    }

    private Map<Long, Question> loadQuestions(List<QuestionDuplicateEvalCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (QuestionDuplicateEvalCase item : cases) {
            if (item.getSourceQuestionId() != null) {
                ids.add(item.getSourceQuestionId());
            }
            if (item.getTargetQuestionId() != null) {
                ids.add(item.getTargetQuestionId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Question> questionMap = new LinkedHashMap<>();
        for (Question question : questionMapper.selectBatchIds(ids)) {
            questionMap.put(question.getId(), question);
        }
        return questionMap;
    }

    private QuestionDuplicateEvalCaseVO toCaseVO(QuestionDuplicateEvalCase item, Map<Long, Question> questionMap) {
        QuestionDuplicateEvalCaseVO vo = new QuestionDuplicateEvalCaseVO();
        vo.setId(item.getId());
        vo.setCaseId(item.getCaseId());
        vo.setSourceQuestionId(item.getSourceQuestionId());
        vo.setTargetQuestionId(item.getTargetQuestionId());
        Question source = questionMap.get(item.getSourceQuestionId());
        Question target = questionMap.get(item.getTargetQuestionId());
        vo.setSourceTitle(source == null ? null : source.getTitle());
        vo.setTargetTitle(target == null ? null : target.getTitle());
        vo.setExpected(item.getExpected());
        vo.setNote(item.getNote());
        vo.setEnabled(item.getEnabled());
        vo.setCreatedBy(item.getCreatedBy());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private QuestionDuplicateEvalRunVO toRunVO(QuestionDuplicateEvalRun run, boolean includeResults) {
        QuestionDuplicateEvalRunVO vo = new QuestionDuplicateEvalRunVO();
        vo.setId(run.getId());
        vo.setRunNo(run.getRunNo());
        vo.setStatus(run.getStatus());
        vo.setSampleCount(run.getSampleCount());
        vo.setEvaluatedCount(run.getEvaluatedCount());
        vo.setPassedCount(run.getPassedCount());
        vo.setFailedCount(run.getFailedCount());
        vo.setMissingQuestionCount(run.getMissingQuestionCount());
        vo.setAccuracyRate(run.getAccuracyRate());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setCreatedBy(run.getCreatedBy());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setCreatedAt(run.getCreatedAt());
        vo.setUpdatedAt(run.getUpdatedAt());
        if (includeResults) {
            vo.setResults(evalResultMapper.selectList(new LambdaQueryWrapper<QuestionDuplicateEvalResult>()
                            .eq(QuestionDuplicateEvalResult::getRunId, run.getId())
                            .orderByAsc(QuestionDuplicateEvalResult::getId))
                    .stream()
                    .map(this::toResultVO)
                    .toList());
        }
        return vo;
    }

    private QuestionDuplicateEvalRunVO.ResultItem toResultVO(QuestionDuplicateEvalResult result) {
        QuestionDuplicateEvalRunVO.ResultItem vo = new QuestionDuplicateEvalRunVO.ResultItem();
        vo.setId(result.getId());
        vo.setEvalCaseId(result.getEvalCaseId());
        vo.setCaseId(result.getCaseId());
        vo.setSourceQuestionId(result.getSourceQuestionId());
        vo.setTargetQuestionId(result.getTargetQuestionId());
        vo.setExpected(result.getExpected());
        vo.setPredicted(result.getPredicted());
        vo.setPassed(result.getPassed() != null && result.getPassed() == 1);
        vo.setMatchType(result.getMatchType());
        vo.setScore(result.getScore());
        vo.setScoreBand(result.getScoreBand());
        vo.setScoreParts(readScoreParts(result.getScorePartsJson()));
        vo.setReason(result.getReason());
        vo.setNote(result.getNote());
        vo.setCreatedAt(result.getCreatedAt());
        return vo;
    }

    private Question getQuestionOrThrow(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Question not found");
        }
        return question;
    }

    private String normalizeExpected(String expected) {
        if (!StringUtils.hasText(expected)) {
            return "NOT_DUPLICATE";
        }
        String value = expected.trim().toUpperCase().replace('-', '_');
        if ("DUPLICATE".equals(value) || "REVIEW".equals(value) || "NOT_DUPLICATE".equals(value)) {
            return value;
        }
        if ("TRUE".equals(value) || "YES".equals(value)) {
            return "DUPLICATE";
        }
        if ("FALSE".equals(value) || "NO".equals(value)) {
            return "NOT_DUPLICATE";
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "expected must be DUPLICATE, REVIEW or NOT_DUPLICATE");
    }

    private String defaultCaseId(Long sourceQuestionId, Long targetQuestionId) {
        return "QD-" + sourceQuestionId + "-" + targetQuestionId;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            log.warn("Question duplicate eval result serialize failed", ex);
            return "[]";
        }
    }

    private List<com.codecoachai.question.domain.vo.QuestionDuplicateReviewListVO.ScorePart> readScoreParts(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(List.class,
                    com.codecoachai.question.domain.vo.QuestionDuplicateReviewListVO.ScorePart.class));
        } catch (Exception ex) {
            log.warn("Question duplicate eval score parts parse failed", ex);
            return List.of();
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private long defaultPage(Long pageNo) {
        return pageNo == null || pageNo <= 0 ? 1L : pageNo;
    }

    private long defaultSize(Long pageSize) {
        return pageSize == null || pageSize <= 0 ? 10L : Math.min(pageSize, 100L);
    }
}
