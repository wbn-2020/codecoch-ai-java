package com.codecoachai.interview.service.impl;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.feign.ResumeFeignClient;
import com.codecoachai.interview.feign.vo.InnerResumeDetailVO;
import com.codecoachai.interview.feign.vo.InnerResumeProjectVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterviewReportContextTest {
    @Test
    void realBuilderIncludesResumeProjectsAndTrainingContext() {
        ResumeFeignClient resumes = mock(ResumeFeignClient.class);
        var builder = new InterviewReportAsyncService(null, null, null, null, null, resumes,
                null, null, null, new ObjectMapper(), null, null, null);
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setResumeId(3L);
        session.setIndustryContext("Industry evidence");
        session.setTargetSkillCodes("[\"JAVA\"]");
        session.setProjectEvidenceIds("[81]");
        session.setTrainingScene("PROJECT");
        InnerResumeDetailVO resume = new InnerResumeDetailVO();
        resume.setSummary("Resume evidence");
        InnerResumeProjectVO project = new InnerResumeProjectVO();
        project.setProjectName("Project sample");
        project.setTechnicalDifficulties("Concurrent inventory updates");
        resume.setProjects(List.of(project));
        when(resumes.getResume(3L)).thenReturn(Result.success(resume));
        InterviewMessage evaluation = new InterviewMessage();
        evaluation.setAiScore(85);
        evaluation.setRole("AI");
        var dto = builder.buildReportDTO(session, List.of(evaluation));
        assertEquals("Resume evidence", dto.getResumeContent());
        assertTrue(dto.getProjectContent().contains("Concurrent inventory updates"));
        assertEquals("Industry evidence", dto.getIndustryContext());
        assertEquals(List.of("JAVA"), dto.getTargetSkillCodes());
        assertEquals(List.of(81L), dto.getProjectEvidenceIds());
        assertEquals("PROJECT", dto.getTrainingScene());
        assertTrue(dto.getMessages().get(0).contains("85"));
        var context = new ObjectMapper().convertValue(dto,
                com.codecoachai.task.feign.vo.InterviewReportContextVO.class);
        var taskDto = new com.codecoachai.task.feign.dto.GenerateReportDTO();
        org.springframework.beans.BeanUtils.copyProperties(context, taskDto);
        assertEquals(new ObjectMapper().valueToTree(dto), new ObjectMapper().valueToTree(taskDto));
    }
}
