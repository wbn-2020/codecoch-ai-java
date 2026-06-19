package com.codecoachai.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
