package com.codecoachai.interview.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class ScenarioRubricServiceImplTest {

    @Mock
    private InterviewScenarioVersionMapper scenarioMapper;
    @Mock
    private InterviewRubricVersionMapper rubricMapper;
    @Mock
    private InterviewScenarioBindingMapper bindingMapper;
    @Mock
    private InterviewSessionMapper sessionMapper;

    private ObjectMapper objectMapper;
    private ScenarioRubricServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        init(InterviewRubricVersion.class);
        init(InterviewScenarioVersion.class);
        init(InterviewScenarioBinding.class);
        init(InterviewSession.class);
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new ScenarioRubricServiceImpl(
                scenarioMapper, rubricMapper, bindingMapper, sessionMapper, objectMapper);
        LoginUserContext.setLoginUser(LoginUser.builder().userId(10L).username("tester").build());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void createsNextImmutableRubricVersion() {
        InterviewRubricVersion latest = new InterviewRubricVersion();
        latest.setRubricCode("JAVA_BACKEND");
        latest.setVersionNo(3);
        when(rubricMapper.selectOne(any())).thenReturn(latest);
        when(rubricMapper.insert(any(InterviewRubricVersion.class))).thenAnswer(invocation -> {
            InterviewRubricVersion value = invocation.getArgument(0);
            value.setId(44L);
            return 1;
        });
        RubricVersionCreateDTO dto = new RubricVersionCreateDTO();
        dto.setRubricCode("java_backend");
        dto.setRubricName("Java Backend");
        ArrayNode dimensions = objectMapper.createArrayNode();
        ObjectNode dimension = objectMapper.createObjectNode();
        dimension.put("code", "TECHNICAL_DEPTH");
        dimension.put("weight", 100);
        dimensions.add(dimension);
        dto.setDimensions(dimensions);

        RubricVersionVO result = service.createRubricVersion(dto);

        assertEquals(44L, result.getRubricVersionId());
        assertEquals(4, result.getVersionNo());
        assertEquals(RubricVersionStatus.DRAFT.name(), result.getVersionStatus());
        ArgumentCaptor<Wrapper<InterviewRubricVersion>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(rubricMapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().toUpperCase().contains("FOR UPDATE"));
    }

    @Test
    void rejectsRubricWeightsThatDoNotSumToOneHundred() {
        RubricVersionCreateDTO dto = rubricDto(dimensions(
                dimension("TECHNICAL_DEPTH", 40),
                dimension("COMMUNICATION", 40)));

        assertThrows(BusinessException.class, () -> service.createRubricVersion(dto));
        verify(rubricMapper, never()).insert(any(InterviewRubricVersion.class));
    }

    @Test
    void rejectsDuplicateRubricDimensionCodesIgnoringCase() {
        RubricVersionCreateDTO dto = rubricDto(dimensions(
                dimension("TECHNICAL_DEPTH", 50),
                dimension("technical_depth", 50)));

        assertThrows(BusinessException.class, () -> service.createRubricVersion(dto));
        verify(rubricMapper, never()).insert(any(InterviewRubricVersion.class));
    }

    @Test
    void rejectsDuplicateScenarioStageCodesAndInvalidBudgets() {
        ScenarioVersionCreateDTO dto = new ScenarioVersionCreateDTO();
        dto.setScenarioCode("TECHNICAL");
        dto.setScenarioName("Technical");
        dto.setRubricVersionId(44L);
        dto.setScript(script(
                1, 10,
                stage("JAVA", 1, 6, true, 1),
                stage("java", 0, 5, false, 1)));

        assertThrows(BusinessException.class, () -> service.createScenarioVersion(dto));
        verify(scenarioMapper, never()).insert(any(InterviewScenarioVersion.class));
        verify(rubricMapper, never()).selectById(any());
    }

    @Test
    void rejectsFollowUpLimitWhenDisabledOrOutsideAllowedRange() {
        ScenarioVersionCreateDTO dto = new ScenarioVersionCreateDTO();
        dto.setScenarioCode("TECHNICAL");
        dto.setScenarioName("Technical");
        dto.setRubricVersionId(44L);
        dto.setScript(script(
                1, 10,
                stage("JAVA", 1, 5, false, 1)));

        assertThrows(BusinessException.class, () -> service.createScenarioVersion(dto));
        verify(scenarioMapper, never()).insert(any(InterviewScenarioVersion.class));
    }

    @Test
    void rejectsFollowUpLimitAboveFive() {
        ScenarioVersionCreateDTO dto = new ScenarioVersionCreateDTO();
        dto.setScenarioCode("TECHNICAL");
        dto.setScenarioName("Technical");
        dto.setRubricVersionId(44L);
        dto.setScript(script(
                1, 10,
                stage("JAVA", 1, 5, true, 6)));

        assertThrows(BusinessException.class, () -> service.createScenarioVersion(dto));
        verify(scenarioMapper, never()).insert(any(InterviewScenarioVersion.class));
    }

    @Test
    void rejectsNonBooleanAllowFollowUp() {
        ScenarioVersionCreateDTO dto = new ScenarioVersionCreateDTO();
        dto.setScenarioCode("TECHNICAL");
        dto.setScenarioName("Technical");
        dto.setRubricVersionId(44L);
        ObjectNode invalidStage = stage("JAVA", 1, 5, true, 1);
        invalidStage.put("allowFollowUp", "true");
        dto.setScript(script(1, 10, invalidStage));

        assertThrows(BusinessException.class, () -> service.createScenarioVersion(dto));
        verify(scenarioMapper, never()).insert(any(InterviewScenarioVersion.class));
        verify(rubricMapper, never()).selectById(any());
    }

    @Test
    void createsNextImmutableScenarioVersionWithCodeLock() {
        InterviewScenarioVersion latest = new InterviewScenarioVersion();
        latest.setScenarioCode("TECHNICAL");
        latest.setVersionNo(2);
        when(scenarioMapper.selectOne(any())).thenReturn(latest);
        when(rubricMapper.selectById(44L)).thenReturn(publishedRubric());
        when(scenarioMapper.insert(any(InterviewScenarioVersion.class))).thenAnswer(invocation -> {
            InterviewScenarioVersion value = invocation.getArgument(0);
            value.setId(55L);
            return 1;
        });
        ScenarioVersionCreateDTO dto = new ScenarioVersionCreateDTO();
        dto.setScenarioCode("technical");
        dto.setScenarioName("Technical");
        dto.setRubricVersionId(44L);
        dto.setScript(script(1, 10, stage("JAVA", 1, 5, false, 0)));

        ScenarioVersionVO result = service.createScenarioVersion(dto);

        assertEquals(55L, result.getScenarioVersionId());
        assertEquals(3, result.getVersionNo());
        ArgumentCaptor<Wrapper<InterviewScenarioVersion>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(scenarioMapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().toUpperCase().contains("FOR UPDATE"));
    }

    @Test
    void rejectsNonPositiveScenarioAndStageBudgets() {
        ScenarioVersionCreateDTO dto = new ScenarioVersionCreateDTO();
        dto.setScenarioCode("TECHNICAL");
        dto.setScenarioName("Technical");
        dto.setRubricVersionId(44L);
        dto.setScript(script(
                0, 10,
                stage("JAVA", 0, 0, true, 1)));

        assertThrows(BusinessException.class, () -> service.createScenarioVersion(dto));
        verify(scenarioMapper, never()).insert(any(InterviewScenarioVersion.class));
    }

    @Test
    void rejectsScenarioStageBudgetsThatExceedTotals() {
        ScenarioVersionCreateDTO dto = new ScenarioVersionCreateDTO();
        dto.setScenarioCode("TECHNICAL");
        dto.setScenarioName("Technical");
        dto.setRubricVersionId(44L);
        dto.setScript(script(
                2, 10,
                stage("JAVA", 2, 6, true, 1),
                stage("DATABASE", 1, 5, true, 1)));

        assertThrows(BusinessException.class, () -> service.createScenarioVersion(dto));
        verify(scenarioMapper, never()).insert(any(InterviewScenarioVersion.class));
    }

    @Test
    void publishingInvalidStoredRubricDoesNotRetireCurrentVersion() {
        InterviewRubricVersion draft = new InterviewRubricVersion();
        draft.setId(44L);
        draft.setRubricCode("CORE");
        draft.setVersionStatus(RubricVersionStatus.DRAFT.name());
        draft.setDimensionsJson("[{\"code\":\"A\",\"weight\":40},{\"code\":\"B\",\"weight\":40}]");
        when(rubricMapper.selectById(44L)).thenReturn(draft);

        assertThrows(BusinessException.class, () -> service.publishRubricVersion(44L));

        verify(rubricMapper, never()).update(any(), any());
    }

    @Test
    void publishingRubricRequiresExactlyOneUpdatedRow() {
        InterviewRubricVersion draft = new InterviewRubricVersion();
        draft.setId(44L);
        draft.setRubricCode("CORE");
        draft.setVersionStatus(RubricVersionStatus.DRAFT.name());
        draft.setDimensionsJson("[{\"code\":\"A\",\"weight\":100}]");
        when(rubricMapper.selectById(44L)).thenReturn(draft);
        when(rubricMapper.selectOne(any())).thenReturn(draft, draft);
        when(rubricMapper.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.publishRubricVersion(44L));
        verify(rubricMapper, times(1)).update(any(), any());
    }

    @Test
    void publishingRubricLocksCodeAndConditionallyUpdatesTargetBeforeRetiringPreviousVersion() {
        InterviewRubricVersion draft = new InterviewRubricVersion();
        draft.setId(44L);
        draft.setRubricCode("CORE");
        draft.setVersionStatus(RubricVersionStatus.DRAFT.name());
        draft.setDimensionsJson("[{\"code\":\"A\",\"weight\":100}]");
        when(rubricMapper.selectById(44L)).thenReturn(draft);
        when(rubricMapper.selectOne(any())).thenReturn(draft);
        when(rubricMapper.update(any(), any())).thenReturn(1);

        service.publishRubricVersion(44L);

        InOrder order = inOrder(rubricMapper);
        order.verify(rubricMapper, times(2)).selectOne(any());
        order.verify(rubricMapper, times(2)).update(any(), any());
        ArgumentCaptor<Wrapper<InterviewRubricVersion>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(rubricMapper, times(2)).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getAllValues().stream()
                .allMatch(query -> query.getSqlSegment().toUpperCase().contains("FOR UPDATE")));
        ArgumentCaptor<Wrapper<InterviewRubricVersion>> updateCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(rubricMapper, times(2)).update(isNull(), updateCaptor.capture());
        assertTrue(wrapperValues(updateCaptor.getAllValues().get(0))
                .contains(RubricVersionStatus.DRAFT.name()));
    }

    @Test
    void publishingInvalidStoredScenarioDoesNotRetireCurrentVersion() {
        InterviewScenarioVersion draft = new InterviewScenarioVersion();
        draft.setId(55L);
        draft.setScenarioCode("TECHNICAL");
        draft.setRubricVersionId(44L);
        draft.setVersionStatus(ScenarioVersionStatus.DRAFT.name());
        draft.setScriptJson("{\"questionBudget\":1,\"timeBudgetMinutes\":10,"
                + "\"stages\":[{\"code\":\"JAVA\",\"questionCount\":1,"
                + "\"timeBudgetMinutes\":5,\"allowFollowUp\":false,\"maxFollowUpCount\":1}]}");
        when(scenarioMapper.selectById(55L)).thenReturn(draft);

        assertThrows(BusinessException.class, () -> service.publishScenarioVersion(55L));

        verify(scenarioMapper, never()).update(any(), any());
    }

    @Test
    void publishingScenarioRequiresExactlyOneUpdatedRow() {
        InterviewScenarioVersion draft = new InterviewScenarioVersion();
        draft.setId(55L);
        draft.setScenarioCode("TECHNICAL");
        draft.setRubricVersionId(44L);
        draft.setVersionStatus(ScenarioVersionStatus.DRAFT.name());
        draft.setScriptJson("{\"questionBudget\":1,\"timeBudgetMinutes\":10,"
                + "\"stages\":[{\"code\":\"JAVA\",\"questionCount\":1,"
                + "\"timeBudgetMinutes\":5,\"allowFollowUp\":false,\"maxFollowUpCount\":0}]}");
        when(scenarioMapper.selectById(55L)).thenReturn(draft);
        when(scenarioMapper.selectOne(any())).thenReturn(draft, draft);
        when(rubricMapper.selectById(44L)).thenReturn(publishedRubric());
        when(scenarioMapper.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.publishScenarioVersion(55L));
        verify(scenarioMapper, times(1)).update(any(), any());
    }

    @Test
    void publishingScenarioLocksCodeAndConditionallyUpdatesTargetBeforeRetiringPreviousVersion() {
        InterviewScenarioVersion draft = validDraftScenario();
        when(scenarioMapper.selectById(55L)).thenReturn(draft);
        when(scenarioMapper.selectOne(any())).thenReturn(draft);
        when(rubricMapper.selectById(44L)).thenReturn(publishedRubric());
        when(scenarioMapper.update(any(), any())).thenReturn(1);

        service.publishScenarioVersion(55L);

        InOrder order = inOrder(scenarioMapper);
        order.verify(scenarioMapper, times(2)).selectOne(any());
        order.verify(scenarioMapper, times(2)).update(any(), any());
        ArgumentCaptor<Wrapper<InterviewScenarioVersion>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(scenarioMapper, times(2)).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getAllValues().stream()
                .allMatch(query -> query.getSqlSegment().toUpperCase().contains("FOR UPDATE")));
        ArgumentCaptor<Wrapper<InterviewScenarioVersion>> updateCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(scenarioMapper, times(2)).update(isNull(), updateCaptor.capture());
        assertTrue(wrapperValues(updateCaptor.getAllValues().get(0))
                .contains(ScenarioVersionStatus.DRAFT.name()));
    }

    @Test
    void duplicateBindingInsertRecoversCurrentWinnerForSamePayload() {
        InterviewSession session = new InterviewSession();
        session.setId(99L);
        session.setUserId(10L);
        session.setDeleted(0);
        when(sessionMapper.selectOne(any())).thenReturn(session);
        InterviewScenarioVersion scenario = publishedScenario(55L, 44L);
        when(scenarioMapper.selectById(55L)).thenReturn(scenario);
        InterviewScenarioBinding winner = binding(77L, 99L, 55L, 44L, "USER_SELECTED");
        when(bindingMapper.selectOne(any())).thenReturn(null, winner);
        when(bindingMapper.insert(any(InterviewScenarioBinding.class)))
                .thenThrow(new DuplicateKeyException("uk_isb_session"));
        ScenarioBindingCreateDTO dto = bindingDto(55L, "USER_SELECTED");

        ScenarioBindingVO result = service.bindScenario(99L, dto);

        assertEquals(77L, result.getBindingId());
        ArgumentCaptor<Wrapper<InterviewScenarioBinding>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(bindingMapper, times(2)).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getAllValues().get(1).getSqlSegment().toUpperCase().contains("FOR UPDATE"));
    }

    @Test
    void existingBindingWithDifferentNormalizedSourceIsRejected() {
        InterviewSession session = new InterviewSession();
        session.setId(99L);
        session.setUserId(10L);
        session.setDeleted(0);
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(bindingMapper.selectOne(any()))
                .thenReturn(binding(77L, 99L, 55L, 44L, "SYSTEM_ASSIGNED"));

        assertThrows(BusinessException.class,
                () -> service.bindScenario(99L, bindingDto(55L, "USER_SELECTED")));
    }

    private RubricVersionCreateDTO rubricDto(ArrayNode dimensions) {
        RubricVersionCreateDTO dto = new RubricVersionCreateDTO();
        dto.setRubricCode("CORE");
        dto.setRubricName("Core");
        dto.setDimensions(dimensions);
        return dto;
    }

    private ArrayNode dimensions(ObjectNode... values) {
        ArrayNode result = objectMapper.createArrayNode();
        for (ObjectNode value : values) {
            result.add(value);
        }
        return result;
    }

    private ObjectNode dimension(String code, int weight) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("code", code);
        result.put("weight", weight);
        return result;
    }

    private ObjectNode stage(String code, int questionCount, int timeBudget, boolean allowFollowUp,
                             int maxFollowUpCount) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("code", code);
        result.put("questionCount", questionCount);
        result.put("timeBudgetMinutes", timeBudget);
        result.put("allowFollowUp", allowFollowUp);
        result.put("maxFollowUpCount", maxFollowUpCount);
        return result;
    }

    private ObjectNode script(int questionBudget, int timeBudget, ObjectNode... stages) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("questionBudget", questionBudget);
        result.put("timeBudgetMinutes", timeBudget);
        ArrayNode stageArray = result.putArray("stages");
        for (ObjectNode stage : stages) {
            stageArray.add(stage);
        }
        return result;
    }

    private InterviewRubricVersion publishedRubric() {
        InterviewRubricVersion rubric = new InterviewRubricVersion();
        rubric.setId(44L);
        rubric.setVersionStatus(RubricVersionStatus.PUBLISHED.name());
        return rubric;
    }

    private InterviewScenarioVersion validDraftScenario() {
        InterviewScenarioVersion draft = publishedScenario(55L, 44L);
        draft.setScenarioCode("TECHNICAL");
        draft.setVersionStatus(ScenarioVersionStatus.DRAFT.name());
        draft.setScriptJson("{\"questionBudget\":1,\"timeBudgetMinutes\":10,"
                + "\"stages\":[{\"code\":\"JAVA\",\"questionCount\":1,"
                + "\"timeBudgetMinutes\":5,\"allowFollowUp\":false,\"maxFollowUpCount\":0}]}");
        return draft;
    }

    private InterviewScenarioVersion publishedScenario(Long id, Long rubricId) {
        InterviewScenarioVersion scenario = new InterviewScenarioVersion();
        scenario.setId(id);
        scenario.setRubricVersionId(rubricId);
        scenario.setVersionStatus(ScenarioVersionStatus.PUBLISHED.name());
        return scenario;
    }

    private InterviewScenarioBinding binding(
            Long id, Long sessionId, Long scenarioId, Long rubricId, String source) {
        InterviewScenarioBinding binding = new InterviewScenarioBinding();
        binding.setId(id);
        binding.setUserId(10L);
        binding.setSessionId(sessionId);
        binding.setScenarioVersionId(scenarioId);
        binding.setRubricVersionId(rubricId);
        binding.setBindingSource(source);
        return binding;
    }

    private ScenarioBindingCreateDTO bindingDto(Long scenarioId, String source) {
        ScenarioBindingCreateDTO dto = new ScenarioBindingCreateDTO();
        dto.setScenarioVersionId(scenarioId);
        dto.setBindingSource(source);
        return dto;
    }

    private List<Object> wrapperValues(Wrapper<?> wrapper) {
        if (wrapper instanceof AbstractWrapper<?, ?, ?> query) {
            query.getSqlSegment();
            return new ArrayList<>(query.getParamNameValuePairs().values());
        }
        return List.of();
    }

    private static void init(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }
}
