package com.codecoachai.task.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.task.domain.entity.AsyncTask;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AsyncTaskMapperLeaseContractTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        if (TableInfoHelper.getTableInfo(AsyncTask.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    AsyncTask.class);
        }
    }

    @Test
    void entityMapsPersistentTokenToLeaseTokenColumn() {
        var leaseTokenField = TableInfoHelper.getTableInfo(AsyncTask.class)
                .getFieldList()
                .stream()
                .filter(field -> field.getProperty().equals("leaseToken"))
                .findFirst()
                .orElseThrow();

        assertEquals("lease_token", leaseTokenField.getColumn());
    }

    @Test
    void readyAndExpiredClaimsReplaceThePersistedTokenWithCas() {
        String ready = updateSql("claimReadyTask");
        assertTrue(ready.contains("lease_token = #{newleasetoken}"), ready);
        assertTrue(ready.contains("status = #{expectedstatus}"), ready);
        assertTrue(ready.contains("lease_token <=> #{expectedleasetoken}"), ready);
        assertFalse(ready.contains("started_at <=>"), ready);

        String expired = updateSql("stealRunningTask");
        assertTrue(expired.contains("status = 'running'"), expired);
        assertTrue(expired.contains("lease_token = #{expectedleasetoken}"), expired);
        assertTrue(expired.contains("lease_token = #{newleasetoken}"), expired);
        assertTrue(expired.contains("started_at is null or started_at <= #{expiresbefore}"), expired);
        assertFalse(expired.contains("updated_at <=>"), expired);
    }

    @Test
    void ownerVerificationAndRenewalUseOnlyTheLeaseTokenAsFence() {
        String verification = selectSql("verifyLeaseOwner");
        assertTrue(verification.contains("status = 'running'"), verification);
        assertTrue(verification.contains("lease_token = #{leasetoken}"), verification);
        assertFalse(verification.contains("started_at ="), verification);

        String renewal = updateSql("renewLease");
        assertTrue(renewal.contains("set started_at = #{now}"), renewal);
        assertTrue(renewal.contains("lease_token = #{leasetoken}"), renewal);
        assertFalse(renewal.contains("and started_at ="), renewal);
    }

    @Test
    void everyRunningExitIsTokenGuardedAndClearsOwnership() {
        for (String methodName : List.of(
                "markSuccess",
                "markTerminalFailed",
                "markRetryableFailed",
                "markRetryExhaustedDead",
                "markDead")) {
            String sql = updateSql(methodName);
            assertTrue(sql.contains("status = 'running'"), methodName + ": " + sql);
            assertTrue(sql.contains("lease_token = #{leasetoken}"), methodName + ": " + sql);
            assertTrue(sql.contains("lease_token = null"), methodName + ": " + sql);
            assertFalse(sql.contains("and started_at ="), methodName + ": " + sql);
        }

        String retryable = updateSql("markRetryableFailed");
        assertTrue(retryable.contains("started_at = null"), retryable);
    }

    @Test
    void manualRetryMutationsRequireAndRetainNullToken() {
        for (String methodName : List.of(
                "prepareManualRetry",
                "markManualRetryDispatchFailed")) {
            String sql = updateSql(methodName);
            assertTrue(
                    sql.contains("lease_token <=> #{expectedleasetoken}"),
                    methodName + ": " + sql);
            assertTrue(sql.contains("lease_token = null"), methodName + ": " + sql);
        }
    }

    @Test
    void terminalAndManualRetryTransitionsMaintainGovernanceLifecycle() {
        String success = updateSql("markSuccess");
        assertTrue(success.contains("governance_status = 'resolved'"), success);

        String terminal = updateSql("markTerminalFailed");
        assertTrue(terminal.contains("governance_status = 'manual_action_required'"), terminal);

        String retryable = updateSql("markRetryableFailed");
        assertTrue(retryable.contains("governance_status = 'unassessed'"), retryable);

        String exhausted = updateSql("markRetryExhaustedDead");
        assertTrue(exhausted.contains("governance_status = 'manual_action_required'"), exhausted);

        String manualRetry = updateSql("prepareManualRetry");
        assertTrue(manualRetry.contains("governance_status = 'retrying'"), manualRetry);

        String parentDispatch = updateSql("markManualRetryParentDispatched");
        assertTrue(parentDispatch.contains("retry_count = retry_count + 1"), parentDispatch);
        assertTrue(parentDispatch.contains("execution_id = coalesce"), parentDispatch);
        assertTrue(parentDispatch.contains("retry_preview_hash = null"), parentDispatch);
        assertTrue(parentDispatch.contains("#{childexecutionid}"), parentDispatch);
        assertTrue(parentDispatch.contains("#{childattemptno}"), parentDispatch);

        String dispatchFailure = updateSql("markManualRetryDispatchFailed");
        assertTrue(dispatchFailure.contains("governance_status = 'manual_action_required'"), dispatchFailure);

        String parentDispatchFailure = updateSql("markManualRetryParentDispatchFailed");
        assertTrue(parentDispatchFailure.contains("governance_status = 'manual_action_required'"),
                parentDispatchFailure);
        assertTrue(parentDispatchFailure.contains("#{childexecutionid}"), parentDispatchFailure);

        String pendingCompletion = updateSql("completePendingTask");
        assertTrue(pendingCompletion.contains("governance_status = case"), pendingCompletion);
    }

    @Test
    void governanceClassificationUsesUpdatedAtCompareAndSet() {
        String sql = updateSql("updateGovernance");
        assertTrue(sql.contains("updated_at <=> #{expectedupdatedat}"), sql);
        assertTrue(sql.contains("retry_preview_hash = #{previewhash}"), sql);
        assertTrue(sql.contains("governance_updated_at = #{governanceupdatedat}"), sql);
    }

    private static String updateSql(String methodName) {
        Update annotation = method(methodName).getAnnotation(Update.class);
        if (annotation == null) {
            throw new AssertionError(methodName + " has no @Update contract");
        }
        return normalize(annotation.value());
    }

    private static String selectSql(String methodName) {
        Select annotation = method(methodName).getAnnotation(Select.class);
        if (annotation == null) {
            throw new AssertionError(methodName + " has no @Select contract");
        }
        return normalize(annotation.value());
    }

    private static Method method(String methodName) {
        return Arrays.stream(AsyncTaskMapper.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing mapper method " + methodName));
    }

    private static String normalize(String[] sql) {
        return String.join(" ", sql)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
