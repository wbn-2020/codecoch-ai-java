package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ResumeSuggestionLockingReadContractTest {

    @Test
    void parentResumeReadIsOwnerScopedAndLocking() throws Exception {
        String sql = selectSql(ResumeMapper.class, "lockOwnedResume");

        assertTrue(sql.contains("from resume"), sql);
        assertTrue(sql.contains("id ="), sql);
        assertTrue(sql.contains("user_id ="), sql);
        assertTrue(sql.contains("deleted = 0"), sql);
        assertTrue(sql.contains("for update"), sql);
    }

    @Test
    void currentVersionReadIsOwnerScopedAndLocking() throws Exception {
        String sql = selectSql(ResumeVersionMapper.class, "selectCurrentForUpdate");

        assertTrue(sql.contains("from resume_version"), sql);
        assertTrue(sql.contains("user_id ="), sql);
        assertTrue(sql.contains("resume_id ="), sql);
        assertTrue(sql.contains("current_flag = 1"), sql);
        assertTrue(sql.contains("deleted = 0"), sql);
        assertTrue(sql.contains("limit 1 for update"), sql);
    }

    @Test
    void latestVersionNumberReadUsesLockingCurrentRead() throws Exception {
        String sql = selectSql(ResumeVersionMapper.class, "selectLatestForUpdate");

        assertTrue(sql.contains("from resume_version"), sql);
        assertTrue(sql.contains("user_id ="), sql);
        assertTrue(sql.contains("resume_id ="), sql);
        assertTrue(sql.contains("deleted = 0"), sql);
        assertTrue(sql.contains("order by version_no desc, id desc"), sql);
        assertTrue(sql.contains("limit 1 for update"), sql);
    }

    @Test
    void liveProjectReadIsDeterministicAndLocking() throws Exception {
        String sql = selectSql(ResumeProjectMapper.class, "selectActiveByResumeIdForUpdate");

        assertTrue(sql.contains("from resume_project"), sql);
        assertTrue(sql.contains("resume_id ="), sql);
        assertTrue(sql.contains("deleted = 0"), sql);
        assertTrue(sql.contains("order by sort_order, sort, id"), sql);
        assertTrue(sql.endsWith("for update"), sql);
    }

    private static String selectSql(Class<?> mapperType, String methodName) throws Exception {
        Method method = Arrays.stream(mapperType.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElse(null);
        assertNotNull(method, mapperType.getSimpleName() + "." + methodName + " must exist");
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select, mapperType.getSimpleName() + "." + methodName + " must use explicit @Select SQL");
        return String.join(" ", select.value())
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
