package com.codecoachai.ai.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.router.AiModelRouter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiCallLogServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        if (TableInfoHelper.getTableInfo(AiCallLog.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    AiCallLog.class);
        }
    }

    @Mock
    private AiModelRouter aiModelRouter;
    @Mock
    private AiCallLogMapper aiCallLogMapper;

    @Test
    void outcomeWithoutExecutionSourcePreservesTheRecordedSource() {
        AiCallLogService service = new AiCallLogService(aiModelRouter, aiCallLogMapper);

        service.markDeliveryOutcome(12L, null, "COMPLETE", null, "resume-job-match-v1", "CONSUMABLE");

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(aiCallLogMapper).update(isNull(), wrapperCaptor.capture());
        assertFalse(((LambdaUpdateWrapper<?>) wrapperCaptor.getValue()).getSqlSet().contains("execution_source"));
    }

    @Test
    void explicitExecutionSourceIsPersistedWithTheOutcome() {
        AiCallLogService service = new AiCallLogService(aiModelRouter, aiCallLogMapper);

        service.markDeliveryOutcome(12L, "FALLBACK_MODEL", "DEGRADED",
                "MATCH_REPORT_FALLBACK", "resume-job-match-v1", "REJECTED");

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(aiCallLogMapper).update(isNull(), wrapperCaptor.capture());
        assertTrue(((LambdaUpdateWrapper<?>) wrapperCaptor.getValue()).getSqlSet().contains("execution_source"));
    }
}
