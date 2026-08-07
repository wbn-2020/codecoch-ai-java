package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.vo.InnerProjectEvidenceAgentContextVO;
import com.codecoachai.resume.domain.vo.InnerProjectEvidenceTrainingContextVO;
import com.codecoachai.resume.service.ProjectEvidenceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/project-evidence")
public class InnerProjectEvidenceController {

    private final ProjectEvidenceService projectEvidenceService;

    @GetMapping("/users/{userId}/agent-context")
    public Result<List<InnerProjectEvidenceAgentContextVO>> agentContext(@PathVariable Long userId) {
        return Result.success(projectEvidenceService.listAgentContextForUser(userId));
    }

    @GetMapping("/users/{userId}/training-context")
    public Result<List<InnerProjectEvidenceTrainingContextVO>> trainingContext(@PathVariable Long userId,
                                                                              @RequestParam(required = false) List<Long> ids) {
        return Result.success(projectEvidenceService.listTrainingContextForUser(userId, ids));
    }
}
