package com.codecoachai.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.core.local.LocalInterviewResumeFeignClient;
import com.codecoachai.core.local.LocalResultMapper;
import com.codecoachai.interview.feign.dto.JobApplicationEventSaveDTO;
import com.codecoachai.interview.feign.vo.InnerJobApplicationPackageVO;
import com.codecoachai.interview.feign.vo.InnerProjectEvidenceTrainingContextVO;
import com.codecoachai.interview.feign.vo.InnerTargetJobVO;
import com.codecoachai.resume.domain.vo.TargetJobVO;
import com.codecoachai.resume.service.JobApplicationPackageService;
import com.codecoachai.resume.service.ProjectEvidenceService;
import com.codecoachai.resume.service.ResumeJobMatchService;
import com.codecoachai.resume.service.ResumeService;
import com.codecoachai.resume.service.SkillProfileService;
import com.codecoachai.resume.service.TargetJobService;
import com.codecoachai.resume.service.V4ResumeCareerService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.validation.Validation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalInterviewResumeFeignClientSemanticTest {

    private ResumeService resumeService;
    private JobApplicationPackageService jobApplicationPackageService;
    private V4ResumeCareerService v4ResumeCareerService;
    private TargetJobService targetJobService;
    private ResumeJobMatchService resumeJobMatchService;
    private SkillProfileService skillProfileService;
    private ProjectEvidenceService projectEvidenceService;
    private LocalInterviewResumeFeignClient client;

    @BeforeEach
    void setUp() {
        LoginUserContext.clear();
        resumeService = mock(ResumeService.class);
        jobApplicationPackageService = mock(JobApplicationPackageService.class);
        v4ResumeCareerService = mock(V4ResumeCareerService.class);
        targetJobService = mock(TargetJobService.class);
        resumeJobMatchService = mock(ResumeJobMatchService.class);
        skillProfileService = mock(SkillProfileService.class);
        projectEvidenceService = mock(ProjectEvidenceService.class);
        LocalResultMapper resultMapper = new LocalResultMapper(
                new ObjectMapper()
                        .findAndRegisterModules()
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
                Validation.buildDefaultValidatorFactory().getValidator());
        client = new LocalInterviewResumeFeignClient(
                resumeService,
                jobApplicationPackageService,
                v4ResumeCareerService,
                targetJobService,
                resumeJobMatchService,
                skillProfileService,
                projectEvidenceService,
                resultMapper);
    }

    @AfterEach
    void clearLoginContext() {
        LoginUserContext.clear();
    }

    @Test
    void applicationPackageRequiresLoginBeforeLoadingOwnedData() {
        Result<InnerJobApplicationPackageVO> result = client.getApplicationPackage(42L);

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), result.getCode());
        verifyNoInteractions(jobApplicationPackageService);
    }

    @Test
    void applicationPackageOwnershipFailureRemainsABusinessResult() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(7L).build());
        when(jobApplicationPackageService.detail(42L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "application package not owned"));

        Result<InnerJobApplicationPackageVO> result = client.getApplicationPackage(42L);

        assertEquals(ErrorCode.FORBIDDEN.getCode(), result.getCode());
        assertEquals("application package not owned", result.getMessage());
        verify(jobApplicationPackageService).detail(42L);
    }

    @Test
    void targetJobPassesIdBeforeUserIdAndMapsTheServiceVo() {
        TargetJobVO serviceVo = new TargetJobVO();
        serviceVo.setId(42L);
        serviceVo.setUserId(7L);
        serviceVo.setJobTitle("Backend Engineer");
        when(targetJobService.getTargetJobForUser(42L, 7L)).thenReturn(serviceVo);

        Result<InnerTargetJobVO> result = client.getTargetJob(7L, 42L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(42L, result.getData().getId());
        assertEquals(7L, result.getData().getUserId());
        assertEquals("Backend Engineer", result.getData().getJobTitle());
        verify(targetJobService).getTargetJobForUser(42L, 7L);
    }

    @Test
    void applicationEventPreservesUserAndApplicationOrderAndMapsTheDto() {
        LocalDateTime eventTime = LocalDateTime.of(2026, 8, 4, 10, 30);
        JobApplicationEventSaveDTO dto = new JobApplicationEventSaveDTO();
        dto.setEventType("INTERVIEW");
        dto.setEventTime(eventTime);
        dto.setSummary("Technical interview");
        dto.setReview(Map.of("score", 88));
        ArgumentCaptor<com.codecoachai.resume.domain.dto.JobApplicationEventSaveDTO> dtoCaptor =
                ArgumentCaptor.forClass(com.codecoachai.resume.domain.dto.JobApplicationEventSaveDTO.class);

        Result<Void> result = client.createApplicationEvent(7L, 42L, dto);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(v4ResumeCareerService).createApplicationEventForUser(eq(7L), eq(42L), dtoCaptor.capture());
        assertEquals("INTERVIEW", dtoCaptor.getValue().getEventType());
        assertEquals(eventTime, dtoCaptor.getValue().getEventTime());
        assertEquals("Technical interview", dtoCaptor.getValue().getSummary());
        assertEquals(88, dtoCaptor.getValue().getReview().get("score"));
        assertNotSame(dto, dtoCaptor.getValue());
    }

    @Test
    void projectEvidenceListMapsEveryServiceVoAndKeepsUserAndIds() {
        List<Long> ids = List.of(11L, 12L);
        com.codecoachai.resume.domain.vo.InnerProjectEvidenceTrainingContextVO first =
                new com.codecoachai.resume.domain.vo.InnerProjectEvidenceTrainingContextVO();
        first.setProjectEvidenceId(11L);
        first.setTitle("Payment migration");
        first.setTopSkillNames(List.of("Java", "MySQL"));
        com.codecoachai.resume.domain.vo.InnerProjectEvidenceTrainingContextVO second =
                new com.codecoachai.resume.domain.vo.InnerProjectEvidenceTrainingContextVO();
        second.setProjectEvidenceId(12L);
        second.setTitle("Search platform");
        when(projectEvidenceService.listTrainingContextForUser(7L, ids))
                .thenReturn(List.of(first, second));

        Result<List<InnerProjectEvidenceTrainingContextVO>> result =
                client.listProjectEvidenceTrainingContext(7L, ids);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().size());
        assertEquals(11L, result.getData().get(0).getProjectEvidenceId());
        assertEquals("Payment migration", result.getData().get(0).getTitle());
        assertEquals(List.of("Java", "MySQL"), result.getData().get(0).getTopSkillNames());
        assertEquals(12L, result.getData().get(1).getProjectEvidenceId());
        assertNotSame(first, result.getData().get(0));
        verify(projectEvidenceService).listTrainingContextForUser(7L, ids);
    }
}
