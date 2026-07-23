package com.codecoachai.resume.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.resume.careerimport.entity.CareerImportBatch;
import com.codecoachai.resume.mapper.careerimport.CareerImportBatchMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerImportRecoveryTaskTest {

    @Mock
    private CareerImportBatchMapper batchMapper;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(CareerImportBatch.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    CareerImportBatch.class);
        }
    }

    @Test
    void marksOnlyStaleRunningBatchesAsFailedWithoutDeletingThem() {
        CareerImportRecoveryTask task = new CareerImportRecoveryTask(batchMapper, 30);
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 9, 0);
        when(batchMapper.update(isNull(), any())).thenReturn(5);

        int recovered = task.recoverStaleImports(now);

        assertEquals(5, recovered);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<CareerImportBatch>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(batchMapper).update(isNull(), wrapperCaptor.capture());
        LambdaUpdateWrapper<CareerImportBatch> wrapper = wrapperCaptor.getValue();
        String sql = wrapper.getSqlSet().toLowerCase() + " " + wrapper.getSqlSegment().toLowerCase();
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("updated_at"));
        assertTrue(sql.contains("deleted"));
        var parameters = ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) wrapper)
                .getParamNameValuePairs().values();
        assertTrue(parameters.contains("FAILED"));
        assertTrue(parameters.contains("RUNNING"));
        assertTrue(parameters.contains(0));
        assertTrue(parameters.contains(now));
        assertTrue(parameters.contains(now.minusMinutes(30)));
    }

    @Test
    void rejectsUnsafeRecoveryTimeouts() {
        assertThrows(IllegalArgumentException.class,
                () -> new CareerImportRecoveryTask(batchMapper, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new CareerImportRecoveryTask(batchMapper, 1441));
    }
}
