package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.PracticeReviewDTO;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServiceImplPracticeReviewContractTest {

    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Mock
    private PromptRenderService promptRenderService;
    @Mock
    private AiCallLogService aiCallLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiServiceImpl service;

    @BeforeEach
    void setUp() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.setMockEnabled(false);
        aiProperties.setProvider("openai-compatible");
        aiProperties.setModel("test-model");
        when(promptRenderService.render(any(String.class), any(String.class), anyMap()))
                .thenAnswer(invocation -> PromptRenderResult.builder()
                        .scene(invocation.getArgument(0))
                        .renderedPrompt("rendered prompt")
                        .inputVariablesJson("{}")
                        .modelParamsJson("{}")
                        .promptHash("hash")
                        .fallbackUsed(false)
                        .build());
        service = new AiServiceImpl(
                aiCallLogMapper,
                promptRenderService,
                aiCallLogService,
                aiProperties,
                objectMapper);
    }

    @Test
    void realReviewRequiresJsonObjectRoot() throws Exception {
        ObjectNode review = validReview();
        stubProvider(objectMapper.createArrayNode().add(review));

        assertThrows(BusinessException.class, () -> service.reviewPractice(practiceReviewDTO()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"85\"", "85.0", "-1", "101"})
    void realReviewRejectsNonIntegerOrOutOfRangeScore(String scoreJson) throws Exception {
        ObjectNode review = validReview();
        review.set("score", objectMapper.readTree(scoreJson));
        stubProvider(review);

        assertThrows(BusinessException.class, () -> service.reviewPractice(practiceReviewDTO()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "score",
            "level",
            "summary",
            "strengths",
            "weaknesses",
            "improvementSuggestions",
            "referenceComparison",
            "knowledgeGaps",
            "suggestedFollowUps"
    })
    void realReviewRejectsEveryMissingFixedField(String fieldName) {
        ObjectNode review = validReview();
        review.remove(fieldName);
        stubProvider(review);

        assertThrows(BusinessException.class, () -> service.reviewPractice(practiceReviewDTO()));
    }

    @Test
    void realReviewRejectsUnexpectedField() {
        ObjectNode review = validReview();
        review.put("comment", "额外字段");
        stubProvider(review);

        assertThrows(BusinessException.class, () -> service.reviewPractice(practiceReviewDTO()));
    }

    @ParameterizedTest
    @MethodSource("wrongFieldTypes")
    void realReviewRejectsWrongFixedFieldType(String fieldName, JsonNode invalidValue) {
        ObjectNode review = validReview();
        review.set(fieldName, invalidValue);
        stubProvider(review);

        assertThrows(BusinessException.class, () -> service.reviewPractice(practiceReviewDTO()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "strengths",
            "weaknesses",
            "improvementSuggestions",
            "knowledgeGaps",
            "suggestedFollowUps"
    })
    void realReviewRejectsBlankArrayElement(String fieldName) {
        ObjectNode review = validReview();
        review.set(fieldName, objectMapper.createArrayNode().add("   "));
        stubProvider(review);

        assertThrows(BusinessException.class, () -> service.reviewPractice(practiceReviewDTO()));
    }

    @Test
    void scoreOverridesConflictingProviderLevelAndMasteryStatus() {
        ObjectNode review = validReview();
        review.put("score", 42);
        review.put("level", "优秀");
        stubProvider(review);

        var result = service.reviewPractice(practiceReviewDTO());

        assertEquals(42, result.getScore());
        assertEquals("WEAK", result.getLevel());
        assertEquals("NOT_MASTERED", result.getMasteryStatus());
    }

    @Test
    void realReviewRejectsLevelOutsideEstablishedExactMappings() {
        ObjectNode review = validReview();
        review.put("level", "总体良好");
        stubProvider(review);

        assertThrows(BusinessException.class, () -> service.reviewPractice(practiceReviewDTO()));
    }

    @Test
    void overlappingFragmentsFromOneChinesePhraseDoNotCountAsIndependentSupport() {
        ObjectNode review = validReview();
        review.put("summary", "事务失效属于常见问题，整体回答覆盖了主要原因。");
        review.set("strengths", array("说明了事务失效"));
        review.set("weaknesses", array("相关内容仍需补充"));
        review.set("improvementSuggestions", array("补充具体边界"));
        review.put("referenceComparison", "与参考答案围绕事务失效的方向一致。");
        review.set("knowledgeGaps", array("事务失效原因"));
        review.set("suggestedFollowUps", array("请继续说明事务失效。"));
        stubProvider(review);

        assertThrows(BusinessException.class, () -> service.reviewPractice(practiceReviewDTO()));
    }

    @Test
    void supportEvidenceWithoutTitleOrKnowledgeAnchorIsRejected() {
        ObjectNode review = validReview();
        review.put("summary", "回答指出自调用和异常被吞的影响，整体方向较为完整。");
        review.set("strengths", array("说明了自调用的影响"));
        review.set("weaknesses", array("异常被吞的影响仍需补充"));
        review.set("improvementSuggestions", array("补充受检异常与回滚配置"));
        review.put("referenceComparison", "与参考答案中的自调用和异常处理结论一致。");
        review.set("knowledgeGaps", array("受检异常回滚"));
        review.set("suggestedFollowUps", array("请说明异常被吞为何影响回滚。"));
        stubProvider(review);

        assertThrows(BusinessException.class, () -> service.reviewPractice(practiceReviewDTO()));
    }

    @Test
    void titleAnchorAndIndependentReferenceSupportAreAccepted() {
        stubProvider(validReview());

        var result = service.reviewPractice(practiceReviewDTO());

        assertEquals(85, result.getScore());
        assertEquals("GOOD", result.getLevel());
        assertEquals("MASTERED", result.getMasteryStatus());
    }

    private static Stream<Arguments> wrongFieldTypes() {
        ObjectMapper mapper = new ObjectMapper();
        return Stream.of(
                Arguments.of("level", mapper.createArrayNode()),
                Arguments.of("summary", mapper.createArrayNode()),
                Arguments.of("referenceComparison", mapper.createObjectNode()),
                Arguments.of("strengths", mapper.getNodeFactory().textNode("优点")),
                Arguments.of("weaknesses", mapper.createObjectNode()),
                Arguments.of("improvementSuggestions", mapper.getNodeFactory().textNode("建议")),
                Arguments.of("knowledgeGaps", mapper.getNodeFactory().nullNode()),
                Arguments.of("suggestedFollowUps", mapper.getNodeFactory().numberNode(1)));
    }

    private ObjectNode validReview() {
        ObjectNode review = objectMapper.createObjectNode();
        review.put("score", 85);
        review.put("level", "良好");
        review.put("summary", "回答准确说明了事务失效与代理边界的关系，并指出自调用会绕过代理。");
        review.set("strengths", array("明确识别了事务失效的代理边界"));
        review.set("weaknesses", array("异常被吞时的回滚行为还可继续展开"));
        review.set("improvementSuggestions", array("补充受检异常与 rollbackFor 的关系"));
        review.put("referenceComparison", "与参考答案中自调用绕过代理的结论一致，异常处理边界仍可补充。");
        review.set("knowledgeGaps", array("受检异常的事务回滚规则"));
        review.set("suggestedFollowUps", array("请说明自调用为何无法触发事务代理。"));
        return review;
    }

    private ArrayNode array(String value) {
        return objectMapper.createArrayNode().add(value);
    }

    private void stubProvider(JsonNode response) {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent(response.toString());
        routeResult.setAiCallLogId(9001L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);
    }

    private PracticeReviewDTO practiceReviewDTO() {
        PracticeReviewDTO dto = new PracticeReviewDTO();
        dto.setUserId(27L);
        dto.setRecordId(55L);
        dto.setQuestionId(9700403L);
        dto.setQuestionTitle("Spring 事务在什么情况下会失效？");
        dto.setQuestionContent("请说明自调用和异常处理对事务边界的影响。");
        dto.setKnowledgePoint("事务失效与代理边界");
        dto.setReferenceAnswer("自调用绕过代理，异常被吞会导致事务无法按预期回滚。");
        dto.setAnalysis("重点识别代理边界、异常类型和真实回滚行为。");
        dto.setAnswerContent("Spring 事务依赖 AOP 代理，自调用会绕过代理，异常被吞也不会按预期回滚。");
        dto.setAnswerDurationSeconds(120);
        return dto;
    }
}
