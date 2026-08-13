package com.codecoachai.resume.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.web.handler.GlobalExceptionHandler;
import com.codecoachai.resume.domain.vo.ResumeClaimAuditVO;
import com.codecoachai.resume.service.ResumeClaimAuditService;
import com.codecoachai.resume.service.ResumeSuggestionService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ResumeSuggestionAuditControllerTest {

    @Mock
    private ResumeSuggestionService suggestionService;
    @Mock
    private ResumeClaimAuditService claimAuditService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ResumeSuggestionAuditController controller =
                new ResumeSuggestionAuditController(suggestionService, claimAuditService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exposesClaimAuditGetAndPostRoutes() throws Exception {
        ResumeClaimAuditVO audit = audit(11L);
        when(claimAuditService.list(17L)).thenReturn(List.of(audit));
        when(claimAuditService.list(null)).thenReturn(List.of());
        when(claimAuditService.detail(11L)).thenReturn(audit);
        when(claimAuditService.audit(2L)).thenReturn(audit);

        mockMvc.perform(get("/resume-claim-audits").param("resumeId", "17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(11));
        mockMvc.perform(get("/resume-claim-audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/resume-claim-audits/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11));
        mockMvc.perform(post("/resume-claim-audits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeVersionId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11));

        verify(claimAuditService).list(17L);
        verify(claimAuditService).list(null);
        verify(claimAuditService).detail(11L);
        verify(claimAuditService).audit(2L);
    }

    @Test
    void missingResumeVersionIdReturnsValidationError() throws Exception {
        mockMvc.perform(post("/resume-claim-audits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(claimAuditService, never()).audit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invalidIdsReturnParameterError() throws Exception {
        mockMvc.perform(get("/resume-claim-audits/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        mockMvc.perform(get("/resume-claim-audits").param("resumeId", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    void missingOrForeignAuditReturnsHttp404() throws Exception {
        when(claimAuditService.detail(404L))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Resume claim audit does not exist"));

        mockMvc.perform(get("/resume-claim-audits/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    private ResumeClaimAuditVO audit(Long id) {
        ResumeClaimAuditVO audit = new ResumeClaimAuditVO();
        audit.setId(id);
        audit.setResumeId(1L);
        audit.setResumeVersionId(2L);
        audit.setAuditVersion("CLAIM_AUDIT_V1");
        audit.setStatus("COMPLETED");
        audit.setClaimCount(0);
        audit.setVerifiedCount(0);
        audit.setPartialCount(0);
        audit.setUnsupportedCount(0);
        audit.setRiskCount(0);
        audit.setFindings(List.of());
        return audit;
    }
}
