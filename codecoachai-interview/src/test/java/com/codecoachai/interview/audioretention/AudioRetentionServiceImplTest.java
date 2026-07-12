package com.codecoachai.interview.audioretention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.InterviewVoiceSubmission;
import com.codecoachai.interview.feign.FileFeignClient;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mapper.InterviewVoiceSubmissionMapper;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AudioRetentionServiceImplTest {

    @Mock
    private InterviewAudioRetentionRecordMapper retentionMapper;
    @Mock
    private InterviewAudioCleanupRecordMapper cleanupMapper;
    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewVoiceSubmissionMapper submissionMapper;
    @Mock
    private FileFeignClient fileFeignClient;

    private AudioRetentionServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        init(InterviewAudioRetentionRecord.class);
        init(InterviewAudioCleanupRecord.class);
        init(InterviewSession.class);
        init(InterviewVoiceSubmission.class);
    }

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        service = new AudioRetentionServiceImpl(
                retentionMapper, cleanupMapper, sessionMapper, submissionMapper, fileFeignClient, directExecutor);
        LoginUserContext.setLoginUser(LoginUser.builder().userId(7L).build());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void rejectsCleanupBeforeRetentionDeadline() {
        InterviewSession session = new InterviewSession();
        session.setId(3L);
        session.setUserId(7L);
        when(sessionMapper.selectOne(any())).thenReturn(session);
        InterviewAudioRetentionRecord record = new InterviewAudioRetentionRecord();
        record.setId(11L);
        record.setUserId(7L);
        record.setSessionId(3L);
        record.setRetainUntil(LocalDateTime.now().plusDays(1));
        record.setLegalHold(Boolean.FALSE);
        record.setRetentionStatus("RETAINED");
        when(retentionMapper.selectOne(any())).thenReturn(record);

        assertThrows(BusinessException.class,
                () -> service.requestCleanup(3L, 11L, new AudioCleanupRequestDTO()));
    }

    @Test
    void remoteFileConfirmedAbsentConvergesRetentionToDeleted() {
        InterviewSession session = new InterviewSession();
        session.setId(3L);
        session.setUserId(7L);
        when(sessionMapper.selectOne(any())).thenReturn(session);

        InterviewAudioRetentionRecord record = new InterviewAudioRetentionRecord();
        record.setId(11L);
        record.setUserId(7L);
        record.setSessionId(3L);
        record.setVoiceSubmissionId(17L);
        record.setFileId(23L);
        record.setRetainUntil(LocalDateTime.now().minusMinutes(1));
        record.setLegalHold(Boolean.FALSE);
        record.setRetentionStatus("RETAINED");
        when(retentionMapper.selectOne(any())).thenReturn(record);
        when(cleanupMapper.selectOne(any())).thenReturn(null);
        when(fileFeignClient.delete(23L, 7L, "INTERVIEW_VOICE"))
                .thenReturn(Result.fail(ErrorCode.PARAM_ERROR.getCode(), "file is absent"));
        when(fileFeignClient.detail(23L, 7L, "INTERVIEW_VOICE"))
                .thenReturn(Result.fail(ErrorCode.PARAM_ERROR.getCode(), "file is absent"));

        AudioCleanupTaskVO task = service.requestCleanup(3L, 11L, new AudioCleanupRequestDTO());

        assertEquals("SUCCEEDED", task.getCleanupStatus());
        assertEquals("DELETED", record.getRetentionStatus());
        verify(submissionMapper).update(eq(null), any());
    }

    @Test
    void repeatedCleanupRequestReturnsLatestSuccessAfterRetentionIsDeleted() {
        InterviewSession session = new InterviewSession();
        session.setId(3L);
        session.setUserId(7L);
        when(sessionMapper.selectOne(any())).thenReturn(session);

        InterviewAudioRetentionRecord record = new InterviewAudioRetentionRecord();
        record.setId(11L);
        record.setUserId(7L);
        record.setSessionId(3L);
        record.setRetainUntil(LocalDateTime.now().minusMinutes(1));
        record.setLegalHold(Boolean.FALSE);
        record.setRetentionStatus("DELETED");
        when(retentionMapper.selectOne(any())).thenReturn(record);

        InterviewAudioCleanupRecord cleanup = new InterviewAudioCleanupRecord();
        cleanup.setRetentionRecordId(11L);
        cleanup.setCleanupTaskId("audio-cleanup-complete");
        cleanup.setAttemptNo(1);
        cleanup.setCleanupStatus("SUCCEEDED");
        when(cleanupMapper.selectOne(any())).thenReturn(cleanup);

        AudioCleanupTaskVO task = service.requestCleanup(3L, 11L, new AudioCleanupRequestDTO());

        assertEquals("audio-cleanup-complete", task.getCleanupTaskId());
        assertEquals("SUCCEEDED", task.getCleanupStatus());
    }

    private static void init(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }
}
