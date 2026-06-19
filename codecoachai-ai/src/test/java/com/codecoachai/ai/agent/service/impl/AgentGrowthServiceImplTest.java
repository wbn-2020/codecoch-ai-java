package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.domain.dto.AgentMemoryCreateDTO;
import com.codecoachai.ai.agent.domain.entity.AgentMemory;
import com.codecoachai.ai.agent.domain.vo.memory.AgentMemoryVO;
import com.codecoachai.ai.agent.mapper.AgentMemoryMapper;
import com.codecoachai.ai.agent.mapper.AgentReviewMapper;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mapper.ReadinessScoreRecordMapper;
import com.codecoachai.ai.agent.mapper.SkillGrowthSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentGrowthServiceImplTest {

    @Mock
    private AgentTaskMapper agentTaskMapper;
    @Mock
    private AgentRunMapper agentRunMapper;
    @Mock
    private AgentReviewMapper agentReviewMapper;
    @Mock
    private SkillGrowthSnapshotMapper skillGrowthSnapshotMapper;
    @Mock
    private ReadinessScoreRecordMapper readinessScoreRecordMapper;
    @Mock
    private AgentMemoryMapper agentMemoryMapper;

    @Test
    void createMemoryStoresRequestedOwnerAndDefaults() {
        AgentGrowthServiceImpl service = service();
        AgentMemoryCreateDTO dto = new AgentMemoryCreateDTO();
        dto.setContent("复盘时更关注 MySQL 索引题。");

        AgentMemoryVO vo = service.createMemory(10L, dto);

        ArgumentCaptor<AgentMemory> memoryCaptor = ArgumentCaptor.forClass(AgentMemory.class);
        verify(agentMemoryMapper).insert(memoryCaptor.capture());
        AgentMemory memory = memoryCaptor.getValue();
        assertEquals(10L, memory.getUserId());
        assertEquals("USER_NOTE", memory.getMemoryType());
        assertEquals("复盘时更关注 MySQL 索引题。", memory.getContent());
        assertEquals("MANUAL", memory.getSourceType());
        assertEquals(BigDecimal.valueOf(0.9), memory.getConfidence());
        assertEquals(1, memory.getEnabled());
        assertEquals("复盘时更关注 MySQL 索引题。", vo.getContent());
    }

    @Test
    void setMemoryEnabledRejectsOtherUsersMemory() {
        AgentGrowthServiceImpl service = service();
        when(agentMemoryMapper.selectById(99L)).thenReturn(memory(99L, 20L, 1));

        assertThrows(IllegalArgumentException.class, () -> service.setMemoryEnabled(10L, 99L, false));
        verify(agentMemoryMapper, never()).updateById(any(AgentMemory.class));
    }

    @Test
    void setMemoryEnabledUpdatesOwnedMemoryOnly() {
        AgentGrowthServiceImpl service = service();
        when(agentMemoryMapper.selectById(99L))
                .thenReturn(memory(99L, 10L, 1))
                .thenReturn(memory(99L, 10L, 0));

        AgentMemoryVO vo = service.setMemoryEnabled(10L, 99L, false);

        ArgumentCaptor<AgentMemory> memoryCaptor = ArgumentCaptor.forClass(AgentMemory.class);
        verify(agentMemoryMapper).updateById(memoryCaptor.capture());
        assertEquals(0, memoryCaptor.getValue().getEnabled());
        assertEquals(0, vo.getEnabled());
    }

    @Test
    void deleteMemoryRejectsOtherUsersMemory() {
        AgentGrowthServiceImpl service = service();
        when(agentMemoryMapper.selectById(99L)).thenReturn(memory(99L, 20L, 1));

        assertThrows(IllegalArgumentException.class, () -> service.deleteMemory(10L, 99L));
        verify(agentMemoryMapper, never()).deleteById(99L);
    }

    @Test
    void deleteMemoryDeletesOwnedMemoryOnly() {
        AgentGrowthServiceImpl service = service();
        when(agentMemoryMapper.selectById(99L)).thenReturn(memory(99L, 10L, 1));

        service.deleteMemory(10L, 99L);

        verify(agentMemoryMapper).deleteById(99L);
    }

    private AgentGrowthServiceImpl service() {
        return new AgentGrowthServiceImpl(
                agentTaskMapper,
                agentRunMapper,
                agentReviewMapper,
                skillGrowthSnapshotMapper,
                readinessScoreRecordMapper,
                agentMemoryMapper,
                new ObjectMapper());
    }

    private AgentMemory memory(Long id, Long userId, Integer enabled) {
        AgentMemory memory = new AgentMemory();
        memory.setId(id);
        memory.setUserId(userId);
        memory.setMemoryType("WEAKNESS");
        memory.setContent("薄弱项");
        memory.setSourceType("MANUAL");
        memory.setConfidence(BigDecimal.valueOf(0.8));
        memory.setEnabled(enabled);
        return memory;
    }
}
