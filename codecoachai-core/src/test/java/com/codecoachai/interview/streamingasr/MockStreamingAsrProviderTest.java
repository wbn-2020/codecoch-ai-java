package com.codecoachai.interview.streamingasr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.interview.voice.task.ProviderExecutionContext;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MockStreamingAsrProviderTest {

    @Test
    void completedSnapshotHasNoSyntheticWordsWhenTimestampsAreUnavailable() {
        MockStreamingAsrProvider provider = new MockStreamingAsrProvider();
        StreamingAsrProviderSession session = provider.open(StreamingAsrOpenRequest.builder()
                        .sessionId("stream-1")
                        .mockTranscript("clear final transcript")
                        .mockTimestampsAvailable(false)
                        .build(),
                new ProviderExecutionContext("stream-1", Duration.ofSeconds(1)));

        session.accept(StreamingAsrAudioChunk.builder()
                .sequence(0)
                .audio(new byte[]{1, 2, 3})
                .build());
        StreamingAsrSnapshot result = session.complete();

        assertEquals("COMPLETED", result.getStatus());
        assertEquals("NONE", result.getTimestampMode());
        assertTrue(result.getWords().isEmpty());
        assertEquals("clear final transcript", result.getFinalTranscript());
    }

    @Test
    void cancelMakesSessionTerminal() {
        MockStreamingAsrProvider provider = new MockStreamingAsrProvider();
        StreamingAsrProviderSession session = provider.open(StreamingAsrOpenRequest.builder()
                        .sessionId("stream-2")
                        .build(),
                new ProviderExecutionContext("stream-2", Duration.ofSeconds(1)));

        StreamingAsrSnapshot result = session.cancel();

        assertEquals("CANCELLED", result.getStatus());
        assertEquals("ASR_STREAM_CANCELLED", result.getErrorCode());
    }
}
