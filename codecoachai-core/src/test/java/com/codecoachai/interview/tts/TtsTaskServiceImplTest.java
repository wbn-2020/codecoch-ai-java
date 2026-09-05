package com.codecoachai.interview.tts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.config.InterviewVoiceProviderProperties;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TtsTaskServiceImplTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    TtsTaskServiceImplTest() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(7L).build());
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        LoginUserContext.clear();
    }

    @Test
    void mockTaskCanBeCancelled() throws Exception {
        TtsTaskServiceImpl service = serviceWithProvider("MOCK", 500);
        TtsTaskCreateDTO dto = request(2000L);

        TtsTaskVO created = service.create(dto);
        TtsTaskVO cancelled = service.cancel(created.getTaskId());

        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals("TTS_CANCELLED", cancelled.getErrorCode());
    }

    @Test
    void mockTaskExposesTimeoutAsTerminalStatus() throws Exception {
        TtsTaskServiceImpl service = serviceWithProvider("MOCK", 500);
        TtsTaskCreateDTO dto = request(100L);

        TtsTaskVO created = service.create(dto);
        TtsTaskVO terminal = awaitTerminal(service, created.getTaskId());

        assertEquals("TIMED_OUT", terminal.getStatus());
        assertEquals("TTS_TIMEOUT", terminal.getErrorCode());
    }

    @Test
    void blankClientProviderDoesNotSilentlyEnableMockTts() {
        TtsTaskServiceImpl service = serviceWithProvider(null, 0);
        TtsTaskCreateDTO dto = request(1000L);
        dto.setProvider(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(dto));

        assertTrue(error.getMessage().contains("TTS_PROVIDER_UNCONFIGURED"));
    }

    @Test
    void deployConfigurationOverridesClientProviderSelection() {
        TtsTaskServiceImpl service = serviceWithProvider("MOCK", 0);
        TtsTaskCreateDTO dto = request(1000L);
        dto.setProvider("CLIENT_OVERRIDE");

        TtsTaskVO created = service.create(dto);

        assertEquals("MOCK", created.getProvider());
    }

    private TtsTaskServiceImpl serviceWithProvider(String provider, long delayMs) {
        InterviewVoiceProviderProperties properties = new InterviewVoiceProviderProperties();
        properties.getTts().setProvider(provider);
        return new TtsTaskServiceImpl(List.of(new MockTtsProvider(delayMs)), executor, properties);
    }

    private TtsTaskCreateDTO request(long timeoutMs) {
        TtsTaskCreateDTO dto = new TtsTaskCreateDTO();
        dto.setText("mock synthesis");
        dto.setProvider("MOCK");
        dto.setTimeoutMs(timeoutMs);
        return dto;
    }

    private TtsTaskVO awaitTerminal(TtsTaskService service, String taskId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            TtsTaskVO task = service.get(taskId);
            if (!List.of("QUEUED", "RUNNING").contains(task.getStatus())) {
                return task;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("TTS task did not become terminal");
    }
}
