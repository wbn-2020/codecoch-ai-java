package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.PortfolioRehearsalSessionSaveDTO;
import com.codecoachai.resume.domain.entity.PortfolioRehearsalSession;
import com.codecoachai.resume.domain.vo.PortfolioRehearsalSessionVO;
import com.codecoachai.resume.mapper.PortfolioRehearsalSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class PortfolioRehearsalSessionServiceImplTest {

    @Mock
    private PortfolioRehearsalSessionMapper sessionMapper;

    private PortfolioRehearsalSessionServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        if (TableInfoHelper.getTableInfo(PortfolioRehearsalSession.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    PortfolioRehearsalSession.class);
        }
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(10L)
                .username("rehearsal-user")
                .build());
        service = new PortfolioRehearsalSessionServiceImpl(sessionMapper, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void currentReturnsDefaultEmptySessionWhenNonePersisted() {
        when(sessionMapper.selectOne(any())).thenReturn(null);

        PortfolioRehearsalSessionVO vo = service.current();

        assertEquals(0, vo.getActiveNodeIndex());
        assertEquals(0, vo.getElapsedSeconds());
        assertTrue(vo.getCompletedNodeIds().isEmpty());
    }

    @Test
    void saveInsertsWhenNoSessionExistsAndReturnsPersistedProgress() {
        when(sessionMapper.selectOne(any())).thenReturn(null);

        PortfolioRehearsalSessionVO vo = service.save(saveDto("deep", 2, 42, List.of("deep-loop", "deep-ability")));

        assertEquals("deep", vo.getActiveRouteKey());
        assertEquals(2, vo.getActiveNodeIndex());
        assertEquals(42, vo.getElapsedSeconds());
        assertEquals(List.of("deep-loop", "deep-ability"), vo.getCompletedNodeIds());

        ArgumentCaptor<PortfolioRehearsalSession> captor =
                ArgumentCaptor.forClass(PortfolioRehearsalSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertEquals(10L, captor.getValue().getUserId());
        assertTrue(captor.getValue().getCompletedNodeIds().contains("deep-loop"));
        verify(sessionMapper, never()).updateById(any(PortfolioRehearsalSession.class));
    }

    @Test
    void saveUpdatesExistingSessionInPlace() {
        PortfolioRehearsalSession existing = new PortfolioRehearsalSession();
        existing.setId(7L);
        existing.setUserId(10L);
        existing.setActiveRouteKey("quick");
        existing.setActiveNodeIndex(0);
        existing.setElapsedSeconds(0);
        existing.setCompletedNodeIds("[]");
        when(sessionMapper.selectOne(any())).thenReturn(existing);

        PortfolioRehearsalSessionVO vo = service.save(saveDto("technical", 1, 15, List.of("tech-boundary")));

        assertEquals("technical", vo.getActiveRouteKey());
        assertEquals(1, vo.getActiveNodeIndex());
        verify(sessionMapper).updateById(existing);
        verify(sessionMapper, never()).insert(any(PortfolioRehearsalSession.class));
    }

    @Test
    void saveDeduplicatesAndTrimsCompletedNodeIds() {
        when(sessionMapper.selectOne(any())).thenReturn(null);

        PortfolioRehearsalSessionVO vo = service.save(
                saveDto("quick", 0, 0, List.of(" quick-a ", "quick-a", "quick-b", "  ")));

        assertEquals(List.of("quick-a", "quick-b"), vo.getCompletedNodeIds());
    }

    @Test
    void saveRejectsCompletedNodeIdOverflow() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            ids.add("node-" + i);
        }

        assertThrows(BusinessException.class, () -> service.save(saveDto("quick", 0, 0, ids)));
    }

    @Test
    void resetClearsProgressForExistingSession() {
        PortfolioRehearsalSession existing = new PortfolioRehearsalSession();
        existing.setId(7L);
        existing.setUserId(10L);
        existing.setActiveRouteKey("deep");
        existing.setActiveNodeIndex(3);
        existing.setElapsedSeconds(120);
        existing.setCompletedNodeIds("[\"deep-loop\"]");
        when(sessionMapper.selectOne(any())).thenReturn(existing);

        PortfolioRehearsalSessionVO vo = service.reset();

        assertEquals(0, vo.getActiveNodeIndex());
        assertEquals(0, vo.getElapsedSeconds());
        assertTrue(vo.getCompletedNodeIds().isEmpty());
        verify(sessionMapper).updateById(existing);
    }

    private static PortfolioRehearsalSessionSaveDTO saveDto(String routeKey, int nodeIndex,
                                                            int elapsed, List<String> completed) {
        PortfolioRehearsalSessionSaveDTO dto = new PortfolioRehearsalSessionSaveDTO();
        dto.setActiveRouteKey(routeKey);
        dto.setActiveNodeIndex(nodeIndex);
        dto.setElapsedSeconds(elapsed);
        dto.setCompletedNodeIds(new java.util.ArrayList<>(completed));
        return dto;
    }
}
