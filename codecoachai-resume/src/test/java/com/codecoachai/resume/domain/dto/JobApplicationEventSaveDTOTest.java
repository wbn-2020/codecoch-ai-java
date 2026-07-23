package com.codecoachai.resume.domain.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class JobApplicationEventSaveDTOTest {

    @Test
    void deserializesFrontendEventTimeFormat() throws Exception {
        JobApplicationEventSaveDTO dto = read("2026-07-19 15:00:00");

        assertEquals(LocalDateTime.of(2026, 7, 19, 15, 0), dto.getEventTime());
        assertEquals("{\"source\":\"APPLICATION_POST_SUBMISSION_ASSISTANT\"}", dto.getReviewJson());
    }

    @Test
    void deserializesInternalIsoEventTimeFormat() throws Exception {
        JobApplicationEventSaveDTO dto = read("2026-07-19T15:00:00");

        assertEquals(LocalDateTime.of(2026, 7, 19, 15, 0), dto.getEventTime());
    }

    private JobApplicationEventSaveDTO read(String eventTime) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return objectMapper.readValue("""
                {
                  "eventType": "INTERVIEW_FEEDBACK_REVIEW",
                  "eventTime": "%s",
                  "summary": "Interview feedback review",
                  "reviewJson": "{\\"source\\":\\"APPLICATION_POST_SUBMISSION_ASSISTANT\\"}"
                }
                """.formatted(eventTime), JobApplicationEventSaveDTO.class);
    }
}
