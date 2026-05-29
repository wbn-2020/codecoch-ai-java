package com.codecoachai.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.ai.agent.config.KnowledgeProperties;
import com.codecoachai.ai.agent.domain.dto.KnowledgeEvalCaseQueryDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeEvalCaseSaveDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeEvalRunRequestDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeEvaluationDTO;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeDocument;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeEvalCase;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeEvalResult;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeEvalRun;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeEvalCaseVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeEvalRunVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeEvaluationVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchResultVO;
import com.codecoachai.ai.agent.mapper.PersonalKnowledgeDocumentMapper;
import com.codecoachai.ai.agent.mapper.PersonalKnowledgeEvalCaseMapper;
import com.codecoachai.ai.agent.mapper.PersonalKnowledgeEvalResultMapper;
import com.codecoachai.ai.agent.mapper.PersonalKnowledgeEvalRunMapper;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.ai.agent.service.KnowledgeEvaluationService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeEvaluationServiceImpl implements KnowledgeEvaluationService {

    private static final DateTimeFormatter RUN_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PersonalKnowledgeEvalCaseMapper evalCaseMapper;
    private final PersonalKnowledgeEvalRunMapper evalRunMapper;
    private final PersonalKnowledgeEvalResultMapper evalResultMapper;
    private final PersonalKnowledgeDocumentMapper personalKnowledgeDocumentMapper;
    private final AgentV4OpsService agentV4OpsService;
    private final KnowledgeProperties knowledgeProperties;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<KnowledgeEvalCaseVO> pageCases(Long userId, KnowledgeEvalCaseQueryDTO query) {
        KnowledgeEvalCaseQueryDTO safeQuery = query == null ? new KnowledgeEvalCaseQueryDTO() : query;
        long pageNo = defaultPage(safeQuery.getPageNo());
        long pageSize = defaultSize(safeQuery.getPageSize());
        Page<PersonalKnowledgeEvalCase> page = evalCaseMapper.selectPage(Page.of(pageNo, pageSize), buildCaseWrapper(userId, safeQuery));
        return PageResult.of(page.getRecords().stream().map(this::toCaseVO).toList(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeEvalCaseVO saveCase(Long userId, KnowledgeEvalCaseSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getQuery())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "query is required");
        }
        if (dto.getExpectedDocumentId() != null) {
            PersonalKnowledgeDocument document = personalKnowledgeDocumentMapper.selectById(dto.getExpectedDocumentId());
            if (document == null || !Objects.equals(document.getUserId(), userId)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Expected knowledge document not found");
            }
        }
        PersonalKnowledgeEvalCase item = dto.getId() == null ? new PersonalKnowledgeEvalCase() : ownedCase(userId, dto.getId());
        item.setUserId(userId);
        item.setCaseId(firstText(dto.getCaseId(), defaultCaseId(dto.getQuery())));
        item.setQueryText(dto.getQuery().trim());
        item.setExpectedDocumentId(dto.getExpectedDocumentId());
        item.setExpectedDocumentTitle(trimToNull(dto.getExpectedDocumentTitle()));
        item.setExpectedDocumentType(trimToNull(dto.getExpectedDocumentType()));
        item.setExpectNoAnswer(Boolean.TRUE.equals(dto.getExpectNoAnswer()) ? 1 : 0);
        item.setNote(dto.getNote());
        item.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        if (item.getId() == null) {
            evalCaseMapper.insert(item);
        } else {
            evalCaseMapper.updateById(item);
        }
        return toCaseVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCase(Long userId, Long id) {
        if (id == null) {
            return;
        }
        ownedCase(userId, id);
        evalCaseMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeEvalRunVO run(Long userId, KnowledgeEvalRunRequestDTO dto) {
        KnowledgeEvalRunRequestDTO request = dto == null ? new KnowledgeEvalRunRequestDTO() : dto;
        List<PersonalKnowledgeEvalCase> cases = loadRunCases(userId, request);
        int limit = request.getLimit() == null ? knowledgeProperties.safeAskDefaultLimit() : normalizeLimit(request.getLimit());
        Double minScore = request.getMinScore() == null ? knowledgeProperties.safeAskMinScore() : normalizeScore(request.getMinScore());
        LocalDateTime startedAt = LocalDateTime.now();
        PersonalKnowledgeEvalRun run = new PersonalKnowledgeEvalRun();
        run.setUserId(userId);
        run.setRunNo("RAG-" + RUN_NO_FORMATTER.format(startedAt));
        run.setStatus("RUNNING");
        run.setSampleCount(cases.size());
        run.setEvaluatedCount(0);
        run.setPassedCount(0);
        run.setFailedCount(0);
        run.setPassRate(0D);
        run.setLimitCount(limit);
        run.setMinScore(minScore);
        run.setStartedAt(startedAt);
        evalRunMapper.insert(run);
        try {
            KnowledgeEvaluationDTO evaluationDTO = new KnowledgeEvaluationDTO();
            evaluationDTO.setLimit(limit);
            evaluationDTO.setMinScore(minScore);
            evaluationDTO.setSamples(cases.stream().map(this::toEvaluationSample).toList());
            KnowledgeEvaluationVO evaluation = agentV4OpsService.evaluateKnowledge(userId, evaluationDTO);
            Map<String, PersonalKnowledgeEvalCase> caseMap = caseIdMap(cases);
            for (KnowledgeEvaluationVO.Item item : evaluation.getItems()) {
                evalResultMapper.insert(toResult(userId, run.getId(), caseMap.get(item.getCaseId()), item));
            }
            run.setStatus("SUCCESS");
            run.setEvaluatedCount(evaluation.getEvaluatedCount());
            run.setPassedCount(evaluation.getPassedCount());
            run.setFailedCount(evaluation.getFailedCount());
            run.setPassRate(evaluation.getPassRate());
            run.setFinishedAt(LocalDateTime.now());
            evalRunMapper.updateById(run);
            return getRun(userId, run.getId());
        } catch (Exception ex) {
            run.setStatus("FAILED");
            run.setFinishedAt(LocalDateTime.now());
            run.setErrorMessage(truncate(ex.getMessage(), 512));
            evalRunMapper.updateById(run);
            throw ex;
        }
    }

    @Override
    public PageResult<KnowledgeEvalRunVO> pageRuns(Long userId, Long pageNo, Long pageSize) {
        long actualPageNo = defaultPage(pageNo);
        long actualPageSize = defaultSize(pageSize);
        Page<PersonalKnowledgeEvalRun> page = evalRunMapper.selectPage(Page.of(actualPageNo, actualPageSize),
                new LambdaQueryWrapper<PersonalKnowledgeEvalRun>()
                        .eq(PersonalKnowledgeEvalRun::getUserId, userId)
                        .orderByDesc(PersonalKnowledgeEvalRun::getCreatedAt));
        return PageResult.of(page.getRecords().stream().map(run -> toRunVO(run, false)).toList(),
                page.getTotal(), actualPageNo, actualPageSize);
    }

    @Override
    public KnowledgeEvalRunVO getRun(Long userId, Long runId) {
        PersonalKnowledgeEvalRun run = evalRunMapper.selectOne(new LambdaQueryWrapper<PersonalKnowledgeEvalRun>()
                .eq(PersonalKnowledgeEvalRun::getId, runId)
                .eq(PersonalKnowledgeEvalRun::getUserId, userId)
                .last("limit 1"));
        if (run == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Knowledge eval run not found");
        }
        return toRunVO(run, true);
    }

    private LambdaQueryWrapper<PersonalKnowledgeEvalCase> buildCaseWrapper(Long userId, KnowledgeEvalCaseQueryDTO query) {
        return new LambdaQueryWrapper<PersonalKnowledgeEvalCase>()
                .eq(PersonalKnowledgeEvalCase::getUserId, userId)
                .eq(query.getExpectedDocumentId() != null, PersonalKnowledgeEvalCase::getExpectedDocumentId, query.getExpectedDocumentId())
                .eq(StringUtils.hasText(query.getExpectedDocumentType()), PersonalKnowledgeEvalCase::getExpectedDocumentType, query.getExpectedDocumentType())
                .eq(query.getExpectNoAnswer() != null, PersonalKnowledgeEvalCase::getExpectNoAnswer, Boolean.TRUE.equals(query.getExpectNoAnswer()) ? 1 : 0)
                .eq(query.getEnabled() != null, PersonalKnowledgeEvalCase::getEnabled, query.getEnabled())
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(PersonalKnowledgeEvalCase::getCaseId, query.getKeyword())
                        .or()
                        .like(PersonalKnowledgeEvalCase::getQueryText, query.getKeyword())
                        .or()
                        .like(PersonalKnowledgeEvalCase::getNote, query.getKeyword()))
                .orderByDesc(PersonalKnowledgeEvalCase::getUpdatedAt);
    }

    private List<PersonalKnowledgeEvalCase> loadRunCases(Long userId, KnowledgeEvalRunRequestDTO request) {
        int size = request.getLimit() == null ? 100 : Math.max(1, Math.min(request.getLimit(), 500));
        List<PersonalKnowledgeEvalCase> cases = evalCaseMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeEvalCase>()
                .eq(PersonalKnowledgeEvalCase::getUserId, userId)
                .in(request.getCaseIds() != null && !request.getCaseIds().isEmpty(), PersonalKnowledgeEvalCase::getId, request.getCaseIds())
                .eq(Boolean.TRUE.equals(request.getOnlyEnabled()), PersonalKnowledgeEvalCase::getEnabled, 1)
                .orderByAsc(PersonalKnowledgeEvalCase::getId)
                .last("limit " + size));
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "No knowledge eval cases matched");
        }
        return cases;
    }

    private KnowledgeEvaluationDTO.Sample toEvaluationSample(PersonalKnowledgeEvalCase item) {
        KnowledgeEvaluationDTO.Sample sample = new KnowledgeEvaluationDTO.Sample();
        sample.setCaseId(item.getCaseId());
        sample.setQuery(item.getQueryText());
        sample.setExpectedDocumentId(item.getExpectedDocumentId());
        sample.setExpectedDocumentTitle(item.getExpectedDocumentTitle());
        sample.setExpectedDocumentType(item.getExpectedDocumentType());
        sample.setExpectNoAnswer(item.getExpectNoAnswer() != null && item.getExpectNoAnswer() == 1);
        sample.setNote(item.getNote());
        return sample;
    }

    private PersonalKnowledgeEvalResult toResult(Long userId, Long runId, PersonalKnowledgeEvalCase sourceCase,
                                                 KnowledgeEvaluationVO.Item item) {
        PersonalKnowledgeEvalResult result = new PersonalKnowledgeEvalResult();
        result.setUserId(userId);
        result.setRunId(runId);
        result.setEvalCaseId(sourceCase == null ? null : sourceCase.getId());
        result.setCaseId(item.getCaseId());
        result.setQueryText(item.getQuery());
        result.setExpectedDocumentId(item.getExpectedDocumentId());
        result.setExpectedDocumentTitle(item.getExpectedDocumentTitle());
        result.setExpectedDocumentType(item.getExpectedDocumentType());
        result.setExpectNoAnswer(Boolean.TRUE.equals(item.getExpectNoAnswer()) ? 1 : 0);
        result.setPassed(Boolean.TRUE.equals(item.getPassed()) ? 1 : 0);
        result.setTopDocumentId(item.getTopDocumentId());
        result.setTopTitle(item.getTopTitle());
        result.setTopDocumentType(item.getTopDocumentType());
        result.setTopScore(item.getTopScore());
        result.setReferenceCount(item.getReferenceCount());
        result.setCitationValid(Boolean.TRUE.equals(item.getCitationValid()) ? 1 : 0);
        result.setAnswerGrounded(Boolean.TRUE.equals(item.getAnswerGrounded()) ? 1 : 0);
        result.setAnswerExcerpt(item.getAnswerExcerpt());
        result.setCitationWarning(item.getCitationWarning());
        result.setFailureReason(item.getFailureReason());
        result.setNote(item.getNote());
        result.setReferencesJson(writeJson(item.getReferences()));
        return result;
    }

    private Map<String, PersonalKnowledgeEvalCase> caseIdMap(List<PersonalKnowledgeEvalCase> cases) {
        Map<String, PersonalKnowledgeEvalCase> result = new LinkedHashMap<>();
        for (PersonalKnowledgeEvalCase item : cases) {
            result.put(item.getCaseId(), item);
        }
        return result;
    }

    private PersonalKnowledgeEvalCase ownedCase(Long userId, Long id) {
        PersonalKnowledgeEvalCase item = evalCaseMapper.selectOne(new LambdaQueryWrapper<PersonalKnowledgeEvalCase>()
                .eq(PersonalKnowledgeEvalCase::getId, id)
                .eq(PersonalKnowledgeEvalCase::getUserId, userId)
                .last("limit 1"));
        if (item == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Knowledge eval case not found");
        }
        return item;
    }

    private KnowledgeEvalCaseVO toCaseVO(PersonalKnowledgeEvalCase item) {
        KnowledgeEvalCaseVO vo = new KnowledgeEvalCaseVO();
        vo.setId(item.getId());
        vo.setCaseId(item.getCaseId());
        vo.setQuery(item.getQueryText());
        vo.setExpectedDocumentId(item.getExpectedDocumentId());
        vo.setExpectedDocumentTitle(item.getExpectedDocumentTitle());
        vo.setExpectedDocumentType(item.getExpectedDocumentType());
        vo.setExpectNoAnswer(item.getExpectNoAnswer() != null && item.getExpectNoAnswer() == 1);
        vo.setNote(item.getNote());
        vo.setEnabled(item.getEnabled());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private KnowledgeEvalRunVO toRunVO(PersonalKnowledgeEvalRun run, boolean includeResults) {
        KnowledgeEvalRunVO vo = new KnowledgeEvalRunVO();
        vo.setId(run.getId());
        vo.setRunNo(run.getRunNo());
        vo.setStatus(run.getStatus());
        vo.setSampleCount(run.getSampleCount());
        vo.setEvaluatedCount(run.getEvaluatedCount());
        vo.setPassedCount(run.getPassedCount());
        vo.setFailedCount(run.getFailedCount());
        vo.setPassRate(run.getPassRate());
        vo.setLimit(run.getLimitCount());
        vo.setMinScore(run.getMinScore());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setCreatedAt(run.getCreatedAt());
        vo.setUpdatedAt(run.getUpdatedAt());
        if (includeResults) {
            vo.setResults(evalResultMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeEvalResult>()
                            .eq(PersonalKnowledgeEvalResult::getRunId, run.getId())
                            .eq(PersonalKnowledgeEvalResult::getUserId, run.getUserId())
                            .orderByAsc(PersonalKnowledgeEvalResult::getId))
                    .stream()
                    .map(this::toResultVO)
                    .toList());
        }
        return vo;
    }

    private KnowledgeEvalRunVO.ResultItem toResultVO(PersonalKnowledgeEvalResult result) {
        KnowledgeEvalRunVO.ResultItem vo = new KnowledgeEvalRunVO.ResultItem();
        vo.setId(result.getId());
        vo.setEvalCaseId(result.getEvalCaseId());
        vo.setCaseId(result.getCaseId());
        vo.setQuery(result.getQueryText());
        vo.setExpectedDocumentId(result.getExpectedDocumentId());
        vo.setExpectedDocumentTitle(result.getExpectedDocumentTitle());
        vo.setExpectedDocumentType(result.getExpectedDocumentType());
        vo.setExpectNoAnswer(result.getExpectNoAnswer() != null && result.getExpectNoAnswer() == 1);
        vo.setPassed(result.getPassed() != null && result.getPassed() == 1);
        vo.setTopDocumentId(result.getTopDocumentId());
        vo.setTopTitle(result.getTopTitle());
        vo.setTopDocumentType(result.getTopDocumentType());
        vo.setTopScore(result.getTopScore());
        vo.setReferenceCount(result.getReferenceCount());
        vo.setCitationValid(result.getCitationValid() != null && result.getCitationValid() == 1);
        vo.setAnswerGrounded(result.getAnswerGrounded() != null && result.getAnswerGrounded() == 1);
        vo.setAnswerExcerpt(result.getAnswerExcerpt());
        vo.setCitationWarning(result.getCitationWarning());
        vo.setFailureReason(result.getFailureReason());
        vo.setNote(result.getNote());
        vo.setReferences(readReferences(result.getReferencesJson()));
        vo.setCreatedAt(result.getCreatedAt());
        return vo;
    }

    private String defaultCaseId(String query) {
        return "RAG-" + Math.abs(query.trim().hashCode());
    }

    private int normalizeLimit(Integer limit) {
        return limit == null ? knowledgeProperties.safeAskDefaultLimit() : Math.max(1, Math.min(limit, 50));
    }

    private Double normalizeScore(Double score) {
        if (score == null) {
            return knowledgeProperties.safeAskMinScore();
        }
        return Math.min(Math.max(score, 0D), 1D);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            log.warn("Knowledge eval references serialize failed", ex);
            return "[]";
        }
    }

    private List<KnowledgeSearchResultVO> readReferences(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(List.class, KnowledgeSearchResultVO.class));
        } catch (Exception ex) {
            log.warn("Knowledge eval references parse failed", ex);
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
