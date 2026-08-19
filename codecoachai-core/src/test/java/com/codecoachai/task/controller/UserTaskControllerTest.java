package com.codecoachai.task.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTaskControllerTest {

    @Mock
    private AsyncTaskMapper asyncTaskMapper;

    private UserTaskController controller;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(AsyncTask.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    AsyncTask.class);
        }
    }

    @BeforeEach
    void setUp() {
        controller = new UserTaskController(asyncTaskMapper);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1001L);
        LoginUserContext.setLoginUser(loginUser);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void pageTasksFiltersExactlyByExecutionIdAndReturnsIt() {
        AsyncTask task = new AsyncTask();
        task.setId(9L);
        task.setUserId(1001L);
        task.setExecutionId("execution-jd-9");
        task.setMessageId("message-jd-9");
        task.setBizType("job-target.parse");
        task.setBizId("88");
        task.setStatus("PENDING");
        Page<AsyncTask> resultPage = Page.of(1, 20);
        resultPage.setRecords(List.of(task));
        resultPage.setTotal(1);
        when(asyncTaskMapper.selectPage(any(Page.class), any())).thenReturn(resultPage);

        var result = controller.pageTasks(
                1L, null, 20L, null, null, null,
                " execution-jd-9 ", null, null, null, null);

        assertEquals("execution-jd-9", result.getData().getRecords().get(0).getExecutionId());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AsyncTask>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(asyncTaskMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        Wrapper<AsyncTask> wrapper = wrapperCaptor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("execution_id ="));
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs()
                .containsValue("execution-jd-9"));
    }
}
