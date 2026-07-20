package com.codecoachai.resume.careerresearch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.codecoachai.resume.careerresearch.entity.CareerResearchSnapshot;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchReportMapper;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchSnapshotMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class CareerResearchMapperContractTest {
    @Test
    void generationClaimUsesLeaseAndDoesNotUseMaxVersion() throws Exception {
        Method method = CareerResearchReportMapper.class.getMethod(
                "claimGeneration", Long.class, Long.class, String.class,
                java.time.LocalDateTime.class, java.time.LocalDateTime.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        assertTrue(sql.contains("generation_claim_token"));
        assertTrue(sql.contains("generation_claimed_at"));
        assertTrue(sql.contains("lock_version"));
        assertFalse(sql.contains("max("));
    }

    @Test
    void snapshotJsonIsMarkedInsertOnly() throws Exception {
        Field field = CareerResearchSnapshot.class.getDeclaredField("snapshotJson");
        TableField tableField = field.getAnnotation(TableField.class);
        assertTrue(tableField != null);
        assertTrue(tableField.updateStrategy() == FieldStrategy.NEVER);
    }

    @Test
    void winnerReadIsScopedByOwnerReportAndSourceSetHash() throws Exception {
        Method method = CareerResearchSnapshotMapper.class.getMethod(
                "selectWinner", Long.class, Long.class, String.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        assertTrue(sql.contains("user_id ="));
        assertTrue(sql.contains("report_id ="));
        assertTrue(sql.contains("source_set_hash ="));
        assertTrue(sql.contains("deleted = 0"));
    }

    @Test
    void fallbackReasonUsesExplicitDatabaseColumn() throws Exception {
        Field field = CareerResearchSnapshot.class.getDeclaredField("fallbackReason");
        assertTrue("fallback".equals(field.getAnnotation(TableField.class).value()));
    }
}
