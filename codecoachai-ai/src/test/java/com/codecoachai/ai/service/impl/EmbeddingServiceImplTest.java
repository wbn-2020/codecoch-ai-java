package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.client.ProviderAiCaller;
import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.ai.domain.dto.EmbeddingRequestDTO;
import com.codecoachai.ai.domain.enums.AiFailureType;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingServiceImplTest {

    @Test
    void mockModeBlocksEmbeddingBeforeAnyProviderRequest() {
        AiProperties properties = new AiProperties();
        properties.setMockEnabled(true);
        ProviderAiCaller caller = mock(ProviderAiCaller.class);

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> service(caller, properties).embed(request()));

        assertEquals(AiFailureType.CONFIG_ERROR, exception.getFailureType());
        verifyNoInteractions(caller);
    }

    @Test
    void disabledServiceBlocksEmbeddingBeforeAnyProviderRequest() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(false);
        ProviderAiCaller caller = mock(ProviderAiCaller.class);

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> service(caller, properties).embed(request()));

        assertEquals(AiFailureType.CONFIG_ERROR, exception.getFailureType());
        verifyNoInteractions(caller);
    }

    private static EmbeddingServiceImpl service(ProviderAiCaller caller, AiProperties properties) {
        return new EmbeddingServiceImpl(
                caller,
                properties,
                new AiRouterProperties(),
                mock(AiCallLogMapper.class),
                new ObjectMapper());
    }

    private static EmbeddingRequestDTO request() {
        EmbeddingRequestDTO dto = new EmbeddingRequestDTO();
        dto.setTexts(List.of("候选人具备 Java 服务端开发经验"));
        return dto;
    }
}
