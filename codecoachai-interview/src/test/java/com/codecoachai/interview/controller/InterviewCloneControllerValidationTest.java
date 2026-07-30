package com.codecoachai.interview.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codecoachai.interview.domain.dto.InterviewRemediationCreateDTO;
import com.codecoachai.interview.domain.dto.InterviewReplayCreateDTO;
import com.codecoachai.interview.domain.vo.InterviewRemediationVO;
import com.codecoachai.interview.domain.vo.InterviewReplayVO;
import com.codecoachai.interview.service.InterviewRemediationService;
import com.codecoachai.interview.service.InterviewReplayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InterviewCloneControllerValidationTest {

    @Mock
    private InterviewReplayService replayService;
    @Mock
    private InterviewRemediationService remediationService;

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new InterviewReplayController(replayService),
                        new InterviewRemediationController(remediationService))
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void replayCreateRejectsUnicodeIdempotencyKeyBeforeServiceInvocation()
            throws Exception {
        mockMvc.perform(post("/interviews/100/replays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"再练-token"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(replayService);
    }

    @Test
    void remediationCreateRejectsWhitespaceIdempotencyKeyBeforeServiceInvocation()
            throws Exception {
        mockMvc.perform(post("/interview-remediations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceReportId":88,
                                  "sourceRequirementIds":[7,9],
                                  "practicePurpose":"补强缓存一致性追问",
                                  "strongRemediation":false,
                                  "idempotencyKey":" remediation-token"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(remediationService);
    }

    @Test
    void replayCreateAcceptsDocumentedAsciiTokenFormat() throws Exception {
        when(replayService.create(
                eq(100L), any(InterviewReplayCreateDTO.class)))
                .thenReturn(new InterviewReplayVO());

        mockMvc.perform(post("/interviews/100/replays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"Replay_2026.07:26-001"}
                                """))
                .andExpect(status().isOk());

        verify(replayService)
                .create(eq(100L), any(InterviewReplayCreateDTO.class));
    }

    @Test
    void remediationCreateAcceptsDocumentedAsciiTokenFormat()
            throws Exception {
        when(remediationService.create(
                any(InterviewRemediationCreateDTO.class)))
                .thenReturn(new InterviewRemediationVO());

        mockMvc.perform(post("/interview-remediations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceReportId":88,
                                  "sourceRequirementIds":[7,9],
                                  "practicePurpose":"补强缓存一致性追问",
                                  "strongRemediation":true,
                                  "idempotencyKey":"Remediation_2026.07:26-001"
                                }
                                """))
                .andExpect(status().isOk());

        verify(remediationService)
                .create(any(InterviewRemediationCreateDTO.class));
    }
}
