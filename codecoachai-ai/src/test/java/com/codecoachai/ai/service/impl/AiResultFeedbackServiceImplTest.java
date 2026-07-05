package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.domain.dto.AiResultFeedbackCreateDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.entity.AiResultFeedback;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.mapper.AiResultFeedbackMapper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiResultFeedbackServiceImplTest {

    @Mock
    private AiResultFeedbackMapper feedbackMapper;
    @Mock
    private AiCallLogMapper aiCallLogMapper;

    @InjectMocks
    private AiResultFeedbackServiceImpl service;

    @Test
    void createRejectsAiCallLogOwnedByDifferentUser() {
        AiCallLog log = new AiCallLog();
        log.setId(900L);
        log.setUserId(99L);
        when(aiCallLogMapper.selectById(900L)).thenReturn(log);

        AiResultFeedbackCreateDTO dto = baseDto();
        dto.setAiCallLogId(900L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(10L, dto));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("AI 调用记录"));
        verify(feedbackMapper, never()).insert(any(AiResultFeedback.class));
    }

    @Test
    void createAllowsFeedbackWithoutAiCallLogId() {
        AiResultFeedbackCreateDTO dto = baseDto();

        service.create(10L, dto);

        ArgumentCaptor<AiResultFeedback> captor = ArgumentCaptor.forClass(AiResultFeedback.class);
        verify(feedbackMapper).insert(captor.capture());
        assertEquals(10L, captor.getValue().getUserId());
        assertEquals("INACCURATE", captor.getValue().getFeedbackType());
        verify(aiCallLogMapper, never()).selectById(any());
    }

    @Test
    void createAllowsAiCallLogOwnedByUser() {
        AiCallLog log = new AiCallLog();
        log.setId(900L);
        log.setUserId(10L);
        when(aiCallLogMapper.selectById(900L)).thenReturn(log);

        AiResultFeedbackCreateDTO dto = baseDto();
        dto.setAiCallLogId(900L);

        service.create(10L, dto);

        ArgumentCaptor<AiResultFeedback> captor = ArgumentCaptor.forClass(AiResultFeedback.class);
        verify(feedbackMapper).insert(captor.capture());
        assertEquals(900L, captor.getValue().getAiCallLogId());
        assertEquals(10L, captor.getValue().getUserId());
    }

    @Test
    void createRejectsFeedbackWithoutSceneOrPagePath() {
        AiResultFeedbackCreateDTO dto = baseDto();
        dto.setScene(" ");
        dto.setPagePath(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(10L, dto));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(feedbackMapper, never()).insert(any(AiResultFeedback.class));
    }

    @Test
    void createRejectsFeedbackWithoutBusinessOrAiCallTrace() {
        AiResultFeedbackCreateDTO dto = baseDto();
        dto.setBizType(null);
        dto.setBizId(null);
        dto.setAiCallLogId(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(10L, dto));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(feedbackMapper, never()).insert(any(AiResultFeedback.class));
    }

    private AiResultFeedbackCreateDTO baseDto() {
        AiResultFeedbackCreateDTO dto = new AiResultFeedbackCreateDTO();
        dto.setScene("INTERVIEW_REPORT");
        dto.setBizType("INTERVIEW_REPORT");
        dto.setBizId(100L);
        dto.setFeedbackType("INACCURATE");
        dto.setRating(2);
        dto.setComment("The answer does not match my interview context.");
        dto.setPagePath("/interview/report/100");
        return dto;
    }
}
