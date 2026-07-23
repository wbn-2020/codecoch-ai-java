package com.codecoachai.interview.scenario;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/admin/interview-definition")
public class ScenarioRubricAdminController {

    private static final String READ_PERMISSION = "admin:interview:list";
    private static final String WRITE_PERMISSION = "admin:interview:write";

    private final ScenarioRubricService service;
    private final AdminPermissionGuard permissionGuard;

    @PostMapping("/rubric-versions")
    public Result<RubricVersionVO> createRubric(@Valid @RequestBody RubricVersionCreateDTO dto) {
        permissionGuard.require(WRITE_PERMISSION);
        return Result.success(service.createRubricVersion(dto));
    }

    @PostMapping("/rubric-versions/{id}/publish")
    public Result<RubricVersionVO> publishRubric(@PathVariable Long id) {
        permissionGuard.require(WRITE_PERMISSION);
        return Result.success(service.publishRubricVersion(id));
    }

    @GetMapping("/rubric-versions")
    public Result<List<RubricVersionVO>> listRubrics(@RequestParam String rubricCode) {
        permissionGuard.require(READ_PERMISSION);
        return Result.success(service.listRubricVersions(rubricCode));
    }

    @PostMapping("/scenario-versions")
    public Result<ScenarioVersionVO> createScenario(@Valid @RequestBody ScenarioVersionCreateDTO dto) {
        permissionGuard.require(WRITE_PERMISSION);
        return Result.success(service.createScenarioVersion(dto));
    }

    @PostMapping("/scenario-versions/{id}/publish")
    public Result<ScenarioVersionVO> publishScenario(@PathVariable Long id) {
        permissionGuard.require(WRITE_PERMISSION);
        return Result.success(service.publishScenarioVersion(id));
    }

    @GetMapping("/scenario-versions")
    public Result<List<ScenarioVersionVO>> listScenarios(@RequestParam String scenarioCode) {
        permissionGuard.require(READ_PERMISSION);
        return Result.success(service.listScenarioVersions(scenarioCode));
    }
}
