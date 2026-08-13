package com.codecoachai.resume.careercontact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.careercontact.dto.CareerActivityRecordDTO;
import com.codecoachai.resume.careercontact.dto.CareerActivitySaveDTO;
import com.codecoachai.resume.careercontact.entity.CareerActivity;
import com.codecoachai.resume.careercontact.entity.CareerActivityEvent;
import com.codecoachai.resume.careercontact.entity.CareerContact;
import com.codecoachai.resume.careercontact.mapper.CareerActivityEventMapper;
import com.codecoachai.resume.careercontact.mapper.CareerActivityMapper;
import com.codecoachai.resume.careercontact.mapper.CareerContactApplicationMapper;
import com.codecoachai.resume.careercontact.mapper.CareerContactMapper;
import com.codecoachai.resume.careercontact.mapper.CareerInterviewRoundContactMapper;
import com.codecoachai.resume.careercontact.service.CareerCommunicationDraftGenerator;
import com.codecoachai.resume.careercontact.service.impl.CareerContactServiceImpl;
import com.codecoachai.resume.careercontact.vo.CareerContactReminderCandidateVO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class CareerContactServiceImplTest {
    @Mock
    private CareerContactMapper contactMapper;
    @Mock
    private CareerContactApplicationMapper contactApplicationMapper;
    @Mock
    private CareerActivityMapper activityMapper;
    @Mock
    private CareerActivityEventMapper activityEventMapper;
    @Mock
    private CareerInterviewRoundContactMapper roundContactMapper;
    @Mock
    private JobApplicationMapper applicationMapper;
    @Mock
    private ObjectProvider<CareerCommunicationDraftGenerator> generatorProvider;

    private CareerContactServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(10L).username("contact-user").build());
        service = new CareerContactServiceImpl(contactMapper, contactApplicationMapper, activityMapper,
                activityEventMapper, roundContactMapper, applicationMapper, generatorProvider);
        JobApplication application = new JobApplication();
        application.setId(7L);
        application.setUserId(10L);
        lenient().when(applicationMapper.selectOne(any())).thenReturn(application);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void rejectsUnmaskedContactHints() {
        com.codecoachai.resume.careercontact.dto.CareerContactSaveDTO request =
                new com.codecoachai.resume.careercontact.dto.CareerContactSaveDTO();
        request.setApplicationId(7L);
        request.setDisplayName("Recruiter");
        request.setMaskedContactHint("recruiter@example.com");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createContact(request));

        assertTrue(exception.getMessage().contains("遮罩"));
    }

    @Test
    void reusesActivityForSameIdempotencyPayloadAndRejectsDifferentPayload() {
        CareerActivity[] stored = new CareerActivity[1];
        CareerActivitySaveDTO request = activity("first summary");
        when(activityMapper.selectByIdempotency(any(), any())).thenAnswer(invocation -> stored[0]);
        when(activityMapper.insert(any(CareerActivity.class))).thenAnswer(invocation -> {
            CareerActivity inserted = invocation.getArgument(0);
            inserted.setId(21L);
            stored[0] = inserted;
            return 1;
        });

        service.createActivity(7L, request);
        service.createActivity(7L, request);
        verify(activityMapper).insert(any(CareerActivity.class));

        CareerActivitySaveDTO changed = activity("changed summary");
        assertThrows(BusinessException.class, () -> service.createActivity(7L, changed));
    }

    @Test
    void includesOverdueFollowUpAndUsesReminderDateForDailyIdentity() {
        CareerActivity activity = new CareerActivity();
        activity.setId(31L);
        activity.setUserId(10L);
        activity.setApplicationId(7L);
        activity.setContactId(41L);
        activity.setSubject("确认面试时间");
        activity.setSummary("需要再次确认面试安排");
        activity.setNextFollowUpAt(LocalDateTime.of(2026, 7, 19, 10, 0));
        CareerContact contact = new CareerContact();
        contact.setId(41L);
        contact.setUserId(10L);
        contact.setDisplayName("招聘联系人");
        when(activityMapper.selectDueFollowUps(eq(10L), any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(activity));
        when(contactMapper.selectById(41L)).thenReturn(contact);

        List<CareerContactReminderCandidateVO> result = service.listReminderCandidates(
                10L, LocalDate.of(2026, 7, 20), LocalDateTime.of(2026, 7, 20, 9, 0));

        assertEquals(1, result.size());
        assertEquals("41", result.get(0).getBizId());
        assertEquals(LocalDate.of(2026, 7, 20), result.get(0).getPlanDate());
        assertTrue(result.get(0).getTitle().contains("招聘联系人"));
    }

    @Test
    void concurrentRecordActivityReturnsWinningIdempotentResult() {
        CareerActivity activity = new CareerActivity();
        activity.setId(51L);
        activity.setUserId(10L);
        activity.setApplicationId(7L);
        activity.setStatus("READY");
        when(activityMapper.selectOne(any())).thenReturn(activity);
        when(activityMapper.updateById(activity)).thenReturn(1);

        AtomicReference<CareerActivityEvent> attempted = new AtomicReference<>();
        when(activityEventMapper.selectByIdempotency(eq(10L), eq(51L), any()))
                .thenAnswer(invocation -> attempted.get());
        when(activityEventMapper.insert(any(CareerActivityEvent.class))).thenAnswer(invocation -> {
            attempted.set(invocation.getArgument(0));
            throw new DuplicateKeyException("concurrent winner");
        });

        CareerActivityRecordDTO request = new CareerActivityRecordDTO();
        request.setIdempotencyKey("record-key");

        assertEquals("RECORDED", service.recordActivity(51L, request).getStatus());
    }

    private CareerActivitySaveDTO activity(String summary) {
        CareerActivitySaveDTO request = new CareerActivitySaveDTO();
        request.setActivityType("EMAIL");
        request.setSubject("Follow up");
        request.setSummary(summary);
        request.setIdempotencyKey("activity-key");
        return request;
    }
}
