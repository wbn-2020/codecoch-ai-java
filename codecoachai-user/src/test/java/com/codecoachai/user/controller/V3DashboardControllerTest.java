package com.codecoachai.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.codecoachai.user.domain.vo.V3DashboardVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class V3DashboardControllerTest {

    @Test
    void nextActionsUseCurrentResumeMatchRouteWhenTargetJobExistsButNoMatchReport() throws Exception {
        V3DashboardController controller = new V3DashboardController(nullJdbcTemplate(), new ObjectMapper());
        V3DashboardVO dashboard = new V3DashboardVO();
        V3DashboardVO.TargetJobCardVO targetJob = new V3DashboardVO.TargetJobCardVO();
        targetJob.setId(11L);
        dashboard.setCurrentTargetJob(targetJob);

        List<V3DashboardVO.NextActionVO> actions = invokeNextActions(controller, dashboard);

        V3DashboardVO.NextActionVO runMatch = actions.stream()
                .filter(action -> "RUN_MATCH".equals(action.getActionType()))
                .findFirst()
                .orElseThrow();
        assertEquals("/resume-match", runMatch.getTargetPath());
    }

    @Test
    void nextActionsUseCanonicalSkillProfilePageRoute() throws Exception {
        V3DashboardController controller = new V3DashboardController(nullJdbcTemplate(), new ObjectMapper());
        V3DashboardVO dashboard = new V3DashboardVO();
        V3DashboardVO.TargetJobCardVO targetJob = new V3DashboardVO.TargetJobCardVO();
        targetJob.setId(11L);
        dashboard.setCurrentTargetJob(targetJob);

        V3DashboardVO.MatchSummaryVO match = new V3DashboardVO.MatchSummaryVO();
        match.setReportId(22L);
        match.setTargetJobId(11L);
        match.setStatus("SUCCESS");
        match.setTrustStatus("VERIFIED");
        match.setFallback(false);
        match.setSchemaWarningCount(0);
        dashboard.setLatestMatch(match);

        List<V3DashboardVO.NextActionVO> actions = invokeNextActions(controller, dashboard);

        assertEquals("/skill-profile", action(actions, "GENERATE_PROFILE").getTargetPath());
        assertEquals("/skill-profile", action(actions, "REVIEW_GAPS").getTargetPath());
        assertFalse(actions.stream()
                .map(V3DashboardVO.NextActionVO::getTargetPath)
                .anyMatch(path -> path != null && path.startsWith("/skill-profiles")));
    }

    private static V3DashboardVO.NextActionVO action(
            List<V3DashboardVO.NextActionVO> actions,
            String actionType) {
        return actions.stream()
                .filter(action -> actionType.equals(action.getActionType()))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<V3DashboardVO.NextActionVO> invokeNextActions(
            V3DashboardController controller,
            V3DashboardVO dashboard) throws Exception {
        Method method = V3DashboardController.class.getDeclaredMethod("nextActions", V3DashboardVO.class);
        method.setAccessible(true);
        return (List<V3DashboardVO.NextActionVO>) method.invoke(controller, dashboard);
    }

    private static JdbcTemplate nullJdbcTemplate() {
        return null;
    }
}
