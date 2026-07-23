package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.dto.AgentContextUsageReferenceRecordDTO;
import com.codecoachai.ai.agent.domain.vo.impact.AgentContextImpactPreviewVO;
import java.util.List;

public interface AgentContextUsageReferenceService {

    void record(AgentContextUsageReferenceRecordDTO record);

    void recordAll(List<AgentContextUsageReferenceRecordDTO> records);

    AgentContextImpactPreviewVO previewKnowledgeDocument(Long userId, Long documentId);

    AgentContextImpactPreviewVO previewKnowledgeChunk(Long userId, Long chunkId);

    AgentContextImpactPreviewVO previewMemory(Long userId, Long memoryId);
}
