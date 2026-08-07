package com.codecoachai.interview.scenario;

import com.codecoachai.common.core.domain.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/interview-scenarios")
public class InterviewScenarioController {

    private final ScenarioRubricService service;

    @GetMapping("/current")
    public Result<ScenarioVersionVO> current(@RequestParam String scenarioCode) {
        return Result.success(service.getCurrentScenario(scenarioCode));
    }

    @PostMapping("/sessions/{sessionId}/binding")
    public Result<ScenarioBindingVO> bind(@PathVariable Long sessionId,
                                          @Valid @RequestBody ScenarioBindingCreateDTO dto) {
        return Result.success(service.bindScenario(sessionId, dto));
    }

    @GetMapping("/sessions/{sessionId}/binding")
    public Result<ScenarioBindingVO> binding(@PathVariable Long sessionId) {
        return Result.success(service.getBinding(sessionId));
    }
}
