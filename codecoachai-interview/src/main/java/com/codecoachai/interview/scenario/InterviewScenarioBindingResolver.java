package com.codecoachai.interview.scenario;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the scenario version a cloned interview must rebind to reproduce the source
 * round's rubric. Sessions do not carry the scenario version themselves — it only lives in
 * {@code interview_scenario_binding} — which is why config-copying flows historically lost
 * it and produced rubric-incomparable rounds.
 */
@Component
@RequiredArgsConstructor
public class InterviewScenarioBindingResolver {

    private final InterviewScenarioBindingMapper bindingMapper;
    private final ScenarioRubricService scenarioRubricService;

    /**
     * Returns the source session's bound scenario version id, or null when the session has
     * no binding. Historical clone flows accept PUBLISHED and RETIRED versions, while DRAFT,
     * deleted and missing versions remain invalid. Strict callers receive a BusinessException;
     * lenient callers receive null.
     */
    public Long reusableScenarioVersionId(Long sessionId, Long userId, boolean strict) {
        InterviewScenarioBinding binding = bindingMapper.selectOne(
                new LambdaQueryWrapper<InterviewScenarioBinding>()
                        .eq(InterviewScenarioBinding::getSessionId, sessionId)
                        .eq(InterviewScenarioBinding::getUserId, userId)
                        .eq(InterviewScenarioBinding::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (binding == null) {
            return null;
        }
        try {
            scenarioRubricService.getCloneableScenarioVersion(binding.getScenarioVersionId());
            return binding.getScenarioVersionId();
        } catch (BusinessException ex) {
            if (strict) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "源场次绑定的场景版本不可用于历史克隆");
            }
            return null;
        }
    }
}
