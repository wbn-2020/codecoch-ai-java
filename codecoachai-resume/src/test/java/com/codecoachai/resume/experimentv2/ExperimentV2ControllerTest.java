package com.codecoachai.resume.experimentv2;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.AttributionView;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.HypothesisUpdate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.HypothesisView;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentV2ControllerTest {

    @Mock
    private ExperimentV2Service experimentV2Service;

    private ExperimentV2Controller controller;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(10L)
                .username("experiment-user")
                .build());
        controller = new ExperimentV2Controller(experimentV2Service);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void listsCurrentUserHypothesesWithFiltersAndLimit() {
        List<HypothesisView> expected = List.of(new HypothesisView());
        when(experimentV2Service.listHypotheses("running", "backend", 91L, 25)).thenReturn(expected);

        List<HypothesisView> actual = controller.listHypotheses("running", "backend", 91L, 25).getData();

        assertSame(expected, actual);
        verify(experimentV2Service).listHypotheses("running", "backend", 91L, 25);
    }

    @Test
    void putAndPatchUseTheSameLifecycleAwareUpdateContract() {
        HypothesisUpdate request = new HypothesisUpdate();
        request.setStatus("PAUSED");
        HypothesisView expected = new HypothesisView();
        when(experimentV2Service.updateHypothesis(7L, request)).thenReturn(expected);

        assertSame(expected, controller.updateHypothesis(7L, request).getData());
        assertSame(expected, controller.patchHypothesis(7L, request).getData());

        verify(experimentV2Service, org.mockito.Mockito.times(2)).updateHypothesis(7L, request);
    }

    @Test
    void readsLatestAndBoundedAttributionHistory() {
        AttributionView latest = new AttributionView();
        List<AttributionView> history = List.of(latest);
        when(experimentV2Service.getLatestAttribution(9L)).thenReturn(latest);
        when(experimentV2Service.listAttributions(9L, 20)).thenReturn(history);

        assertSame(latest, controller.getLatestAttribution(9L).getData());
        assertSame(history, controller.listAttributions(9L, 20).getData());

        verify(experimentV2Service).getLatestAttribution(9L);
        verify(experimentV2Service).listAttributions(9L, 20);
    }
}
