package com.codecoachai.resume.careercalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.EventSave;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.EventView;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.ImportedEvent;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.careerimport.CsvCodec;
import com.codecoachai.resume.careerimport.IcsCodec;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

@ExtendWith(MockitoExtension.class)
class CareerCalendarServiceImplTest {

    @Mock
    private CareerCalendarEventMapper eventMapper;
    @Mock
    private JobApplicationMapper applicationMapper;

    private CareerCalendarServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(CareerCalendarEvent.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    CareerCalendarEvent.class);
        }
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(10L).username("calendar-user").build());
        service = new CareerCalendarServiceImpl(eventMapper, applicationMapper, new CsvCodec(), new IcsCodec());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void storesUtcAndReturnsOriginalTimezone() {
        when(eventMapper.insert(any(CareerCalendarEvent.class))).thenAnswer(invocation -> {
            CareerCalendarEvent event = invocation.getArgument(0);
            event.setId(9L);
            return 1;
        });
        EventSave request = new EventSave();
        request.setTitle("Recruiter follow-up");
        request.setStartsAt(LocalDateTime.of(2026, 7, 11, 9, 0));
        request.setEndsAt(LocalDateTime.of(2026, 7, 11, 10, 0));
        request.setTimezone("Asia/Shanghai");

        EventView result = service.create(request);

        ArgumentCaptor<CareerCalendarEvent> captor = ArgumentCaptor.forClass(CareerCalendarEvent.class);
        verify(eventMapper).insert(captor.capture());
        assertEquals(LocalDateTime.of(2026, 7, 11, 1, 0), captor.getValue().getStartsAtUtc());
        assertEquals("Asia/Shanghai", result.getTimezone());
        assertEquals(Instant.parse("2026-07-11T01:00:00Z"), result.getStartsAtUtc());
    }

    @Test
    void icsExportUsesUnambiguousUtcAndDeclaresCalendarTimezone() {
        CareerCalendarEvent event = new CareerCalendarEvent();
        event.setId(9L);
        event.setUserId(10L);
        event.setTitle("Technical interview");
        event.setStatus("CONFIRMED");
        event.setTimezone("America/New_York");
        event.setStartsAtUtc(LocalDateTime.of(2026, 7, 11, 14, 0));
        event.setEndsAtUtc(LocalDateTime.of(2026, 7, 11, 15, 0));
        when(eventMapper.selectList(any())).thenReturn(List.of(event));

        String ics = new String(service.exportIcs(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                "Asia/Shanghai"), StandardCharsets.UTF_8);

        assertTrue(ics.contains("X-WR-TIMEZONE:Asia/Shanghai"));
        assertTrue(ics.contains("DTSTART:20260711T140000Z"));
        assertTrue(ics.contains("X-CODECOACHAI-TIMEZONE:America/New_York"));
    }

    @Test
    void rejectsCalendarWindowsLongerThan366Days() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2027-01-03T00:00:00Z");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.list(from, to));

        assertTrue(exception.getMessage().contains("366"));
    }

    @Test
    void rejectsMoreThan5000MatchingEventsBeforeExportEncoding() {
        CareerCalendarEvent event = new CareerCalendarEvent();
        event.setId(9L);
        event.setUserId(10L);
        event.setTitle("Follow-up");
        event.setTimezone("Asia/Shanghai");
        event.setStartsAtUtc(LocalDateTime.of(2026, 7, 11, 1, 0));
        event.setEndsAtUtc(LocalDateTime.of(2026, 7, 11, 2, 0));
        when(eventMapper.selectList(any())).thenReturn(Collections.nCopies(5001, event));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.exportCsv(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z")));

        assertTrue(exception.getMessage().contains("5000"));
    }

    @Test
    void createImportedDoesNotRollBackForDuplicateKeyException() throws Exception {
        Method method = CareerCalendarServiceImpl.class.getMethod(
                "createImported", Long.class, ImportedEvent.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertTrue(List.of(transactional.noRollbackFor()).contains(DuplicateKeyException.class));
    }

    @Test
    void transactionInterceptorCommitsCreateImportedDuplicateKeyException() {
        DuplicateKeyException conflict = new DuplicateKeyException("external UID race");
        when(eventMapper.insert(any(CareerCalendarEvent.class))).thenThrow(conflict);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        TransactionInterceptor interceptor = new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(service);
        proxyFactory.addAdvice(interceptor);
        CareerCalendarService proxy = (CareerCalendarService) proxyFactory.getProxy();
        ImportedEvent event = new ImportedEvent(
                null,
                "Technical interview",
                "INTERVIEW",
                LocalDateTime.of(2026, 7, 13, 9, 0),
                LocalDateTime.of(2026, 7, 13, 10, 0),
                "Asia/Shanghai",
                false,
                null,
                null,
                "CONFIRMED",
                "ICS",
                "vevent:1",
                "case-sensitive-UID",
                36L);

        assertThrows(DuplicateKeyException.class, () -> proxy.createImported(10L, event));

        verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(transactionStatus);
    }
}
