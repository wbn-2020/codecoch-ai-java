package com.codecoachai.resume.careercampaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.careercampaign.CareerCampaignModels.CampaignView;
import com.codecoachai.resume.careercampaign.CareerCampaignModels.SaveRequest;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class CareerCampaignServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock
    private CareerCampaignMapper campaignMapper;
    @Mock
    private CareerCampaignEventMapper eventMapper;
    @Mock
    private JobApplicationMapper applicationMapper;

    private CareerCampaignServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(CareerCampaign.class);
        initTableInfo(CareerCampaignEvent.class);
        initTableInfo(JobApplication.class);
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(USER_ID)
                .username("campaign-user")
                .build());
        service = new CareerCampaignServiceImpl(campaignMapper, eventMapper, applicationMapper);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void activateCompleteAndArchiveRequireExpectedVersionAndIdempotencyKey() {
        CareerCampaign campaign = campaign("DRAFT", 1, USER_ID);
        when(campaignMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(campaign);

        assertThrows(BusinessException.class,
                () -> service.activate(1L, null, "activate-key", null));
        assertThrows(BusinessException.class,
                () -> service.activate(1L, 1, null, null));

        campaign.setStatus("ACTIVE");
        assertThrows(BusinessException.class,
                () -> service.complete(1L, false, null, "complete-key", null));
        assertThrows(BusinessException.class,
                () -> service.complete(1L, false, 1, null, null));

        campaign.setStatus("COMPLETED");
        assertThrows(BusinessException.class,
                () -> service.archive(1L, null, "archive-key", null));
        assertThrows(BusinessException.class,
                () -> service.archive(1L, 1, null, null));

        verify(campaignMapper, never()).transition(any(), any(), any(), any(), any());
        verify(eventMapper, never()).insert(any(CareerCampaignEvent.class));
    }

    @Test
    void createWithoutIdempotencyKeyRemainsBackwardCompatible() {
        SaveRequest request = new SaveRequest();
        request.setName("春季求职");
        when(campaignMapper.insert(any(CareerCampaign.class))).thenAnswer(invocation -> {
            CareerCampaign campaign = invocation.getArgument(0);
            campaign.setId(1L);
            return 1;
        });
        when(applicationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        CampaignView result = service.create(request);

        assertEquals("DRAFT", result.getStatus());
        verify(eventMapper).insert(argThat((CareerCampaignEvent event) ->
                event.getIdempotencyKeyHash() == null && event.getRequestHash() != null));
    }

    @Test
    void replaysSameIdempotencyKeyAndPayloadWithoutWritingAgain() {
        CareerCampaign active = campaign("ACTIVE", 1, USER_ID);
        CareerCampaign completed = campaign("COMPLETED", 2, USER_ID);
        CareerCampaignEvent[] storedEvent = new CareerCampaignEvent[1];

        when(campaignMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(active, completed, completed);
        when(eventMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> storedEvent[0]);
        when(applicationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(campaignMapper.transition(any(), any(), any(), any(), any())).thenReturn(1);
        when(eventMapper.insert(any(CareerCampaignEvent.class))).thenAnswer(invocation -> {
            storedEvent[0] = invocation.getArgument(0);
            return 1;
        });

        CampaignView first = service.complete(1L, false, 1, "complete-key", "本周完成");
        CampaignView replay = service.complete(1L, false, 1, "complete-key", "本周完成");

        assertEquals(first, replay);
        verify(campaignMapper).transition(1L, USER_ID, "ACTIVE", "COMPLETED", 1);
        verify(eventMapper).insert(any(CareerCampaignEvent.class));
    }

    @Test
    void rejectsSameIdempotencyKeyWithDifferentPayload() {
        CareerCampaign active = campaign("ACTIVE", 1, USER_ID);
        CareerCampaign completed = campaign("COMPLETED", 2, USER_ID);
        CareerCampaignEvent[] storedEvent = new CareerCampaignEvent[1];

        when(campaignMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(active, completed, completed);
        when(eventMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> storedEvent[0]);
        when(applicationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(campaignMapper.transition(any(), any(), any(), any(), any())).thenReturn(1);
        when(eventMapper.insert(any(CareerCampaignEvent.class))).thenAnswer(invocation -> {
            storedEvent[0] = invocation.getArgument(0);
            return 1;
        });

        service.complete(1L, false, 1, "complete-key", "本周完成");

        assertThrows(BusinessException.class,
                () -> service.complete(1L, false, 1, "complete-key", "改写后的备注"));

        verify(campaignMapper).transition(1L, USER_ID, "ACTIVE", "COMPLETED", 1);
        verify(eventMapper).insert(any(CareerCampaignEvent.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DECLINED", "ACCEPTED", "WITHDRAWN"})
    void terminalApplicationStatusesDoNotBlockCompletion(String applicationStatus) {
        CareerCampaign active = campaign("ACTIVE", 1, USER_ID);
        CareerCampaign completed = campaign("COMPLETED", 2, USER_ID);
        AtomicInteger countCalls = new AtomicInteger();

        when(campaignMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(active, completed);
        when(eventMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(applicationMapper.selectCount(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            if (countCalls.getAndIncrement() == 0) {
                LambdaQueryWrapper<JobApplication> query = invocation.getArgument(0);
                assertFalse(query.getParamNameValuePairs().values().stream()
                        .anyMatch(value -> Objects.equals(value, applicationStatus)
                                || value instanceof Collection<?> values
                                && values.contains(applicationStatus)));
            }
            return 0L;
        });
        when(campaignMapper.transition(any(), any(), any(), any(), any())).thenReturn(1);

        CampaignView result = service.complete(
                1L, false, 1, "complete-" + applicationStatus, null);

        assertEquals("COMPLETED", result.getStatus());
        verify(campaignMapper).transition(1L, USER_ID, "ACTIVE", "COMPLETED", 1);
    }

    @Test
    void rejectsCampaignOwnedByAnotherUser() {
        when(campaignMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.activate(99L, 1, "activate-key", null));

        ArgumentCaptor<LambdaQueryWrapper<CareerCampaign>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(campaignMapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("user_id"));
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("id"));
        verify(campaignMapper, never()).transition(any(), any(), any(), any(), any());
        verify(eventMapper, never()).insert(any(CareerCampaignEvent.class));
    }

    private static CareerCampaign campaign(String status, int lockVersion, long userId) {
        CareerCampaign campaign = new CareerCampaign();
        campaign.setId(1L);
        campaign.setUserId(userId);
        campaign.setName("求职周期");
        campaign.setStatus(status);
        campaign.setLockVersion(lockVersion);
        campaign.setDeleted(0);
        return campaign;
    }
}
