package com.codecoachai.resume.experimentv2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.mapper.experimentv2.ExperimentAssignmentMapper;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ExperimentV2MapperContractTest {

    @Test
    void assignmentWinnerReadIsAUserScopedLockingCurrentRead() throws Exception {
        Select select = ExperimentAssignmentMapper.class
                .getMethod(
                        "selectActiveWinnerForUpdate",
                        Long.class,
                        Long.class,
                        Long.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", select.value())
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();

        assertTrue(sql.contains("from job_experiment_assignment"), sql);
        assertTrue(sql.contains("user_id ="), sql);
        assertTrue(sql.contains("hypothesis_id ="), sql);
        assertTrue(sql.contains("application_id ="), sql);
        assertTrue(sql.contains("deleted = 0"), sql);
        assertTrue(sql.contains("limit 1 for update"), sql);
    }
}
