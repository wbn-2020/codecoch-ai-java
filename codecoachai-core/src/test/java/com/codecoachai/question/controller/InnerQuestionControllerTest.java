package com.codecoachai.question.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.codecoachai.question.domain.entity.QuestionReview;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionReviewMapper;
import com.codecoachai.question.service.QuestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InnerQuestionControllerTest {

    @Mock
    private QuestionService questionService;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionReviewMapper questionReviewMapper;

    private InnerQuestionController controller;

    @BeforeEach
    void setUp() {
        controller = new InnerQuestionController(questionService, questionMapper, questionReviewMapper, new ObjectMapper());
    }

    @Test
    void saveDraftsPersistsMinimizedRawReviewMetadataInsteadOfFullAiPayload() {
        AtomicLong idSequence = new AtomicLong(8000L);
        doAnswer(invocation -> {
            QuestionReview review = invocation.getArgument(0);
            review.setId(idSequence.incrementAndGet());
            return 1;
        }).when(questionReviewMapper).insert(any(QuestionReview.class));

        InnerQuestionController.SaveQuestionDraftsDTO dto = new InnerQuestionController.SaveQuestionDraftsDTO();
        dto.setBatchId("QG-inner-001");
        dto.setCreatedBy(3001L);
        dto.setAiCallLogId(902L);
        dto.setRawAiResultJson("{\"provider\":\"secret task raw\",\"email\":\"zhangsan@example.com\"}");
        dto.setQuestions(List.of(
                draft("Explain JVM GC", "Compare young GC and full GC."),
                draft("Explain MySQL index", "Describe clustered and secondary indexes.")
        ));

        var result = controller.saveDrafts(dto).getData();

        ArgumentCaptor<QuestionReview> reviewCaptor = ArgumentCaptor.forClass(QuestionReview.class);
        verify(questionReviewMapper, times(2)).insert(reviewCaptor.capture());
        for (QuestionReview review : reviewCaptor.getAllValues()) {
            String storedRaw = review.getRawAiResultJson();
            assertTrue(storedRaw.contains("\"storageMode\":\"MINIMIZED_METADATA\""));
            assertTrue(storedRaw.contains("\"aiCallLogId\":902"));
            assertTrue(storedRaw.contains("\"batchId\":\"QG-inner-001\""));
            assertTrue(storedRaw.contains("\"questionCount\":2"));
            assertTrue(storedRaw.contains("\"questionReviewRawStored\":false"));
            assertFalse(storedRaw.contains("secret task raw"));
            assertFalse(storedRaw.contains("zhangsan@example.com"));
        }
        assertEquals(2, result.getSavedCount());
        assertEquals(List.of(8001L, 8002L), result.getReviewIds());
    }

    private static InnerQuestionController.QuestionDraftItem draft(String title, String content) {
        InnerQuestionController.QuestionDraftItem item = new InnerQuestionController.QuestionDraftItem();
        item.setTitle(title);
        item.setContent(content);
        return item;
    }
}
