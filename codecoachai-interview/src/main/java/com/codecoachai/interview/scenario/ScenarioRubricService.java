package com.codecoachai.interview.scenario;

import java.util.List;

public interface ScenarioRubricService {

    RubricVersionVO createRubricVersion(RubricVersionCreateDTO dto);

    RubricVersionVO publishRubricVersion(Long versionId);

    List<RubricVersionVO> listRubricVersions(String rubricCode);

    ScenarioVersionVO createScenarioVersion(ScenarioVersionCreateDTO dto);

    ScenarioVersionVO publishScenarioVersion(Long versionId);

    List<ScenarioVersionVO> listScenarioVersions(String scenarioCode);

    ScenarioVersionVO getCurrentScenario(String scenarioCode);

    ScenarioVersionVO getPublishedScenarioVersion(Long scenarioVersionId);

    ScenarioVersionVO getCloneableScenarioVersion(Long scenarioVersionId);

    ScenarioBindingVO bindScenario(Long sessionId, ScenarioBindingCreateDTO dto);

    ScenarioBindingVO bindCloneScenario(Long sessionId, Long scenarioVersionId);

    ScenarioBindingVO getBinding(Long sessionId);
}
