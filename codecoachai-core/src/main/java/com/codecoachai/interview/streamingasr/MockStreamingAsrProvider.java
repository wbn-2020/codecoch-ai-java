package com.codecoachai.interview.streamingasr;

import com.codecoachai.interview.voice.task.ProviderExecutionContext;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "codecoachai.interview.voice.streaming-asr",
        name = "provider",
        havingValue = "MOCK")
public class MockStreamingAsrProvider implements StreamingAsrProvider {

    @Override
    public String providerCode() {
        return "MOCK";
    }

    @Override
    public StreamingAsrProviderSession open(StreamingAsrOpenRequest request, ProviderExecutionContext context) {
        context.checkActive();
        return new Session(request, context);
    }

    private static final class Session implements StreamingAsrProviderSession {

        private final StreamingAsrOpenRequest request;
        private final ProviderExecutionContext context;
        private long expectedSequence;
        private long acceptedChunks;
        private long acceptedBytes;
        private String status = "OPEN";

        private Session(StreamingAsrOpenRequest request, ProviderExecutionContext context) {
            this.request = request;
            this.context = context;
        }

        @Override
        public synchronized StreamingAsrSnapshot accept(StreamingAsrAudioChunk chunk) {
            requireOpen();
            context.checkActive();
            if (chunk.getSequence() != expectedSequence) {
                throw new IllegalArgumentException("Audio chunk sequence is not contiguous");
            }
            if (chunk.getAudio() == null || chunk.getAudio().length == 0) {
                throw new IllegalArgumentException("Audio chunk cannot be empty");
            }
            expectedSequence++;
            acceptedChunks++;
            acceptedBytes += chunk.getAudio().length;
            if (chunk.isEndOfStream()) {
                return complete();
            }
            return snapshot();
        }

        @Override
        public synchronized StreamingAsrSnapshot complete() {
            requireOpen();
            context.checkActive();
            status = "COMPLETED";
            return snapshot();
        }

        @Override
        public synchronized StreamingAsrSnapshot cancel() {
            if ("OPEN".equals(status)) {
                context.cancel();
                status = "CANCELLED";
            }
            return snapshot();
        }

        @Override
        public synchronized StreamingAsrSnapshot snapshot() {
            if ("OPEN".equals(status)) {
                context.checkActive();
            }
            String transcript = transcript();
            boolean completed = "COMPLETED".equals(status);
            return StreamingAsrSnapshot.builder()
                    .provider("MOCK")
                    .status(status)
                    .partialTranscript(completed ? null : partial(transcript))
                    .finalTranscript(completed ? transcript : null)
                    .timestampMode(request.isMockTimestampsAvailable() ? "WORD" : "NONE")
                    .words(completed ? words(transcript) : List.of())
                    .acceptedChunks(acceptedChunks)
                    .acceptedBytes(acceptedBytes)
                    .errorCode("CANCELLED".equals(status) ? "ASR_STREAM_CANCELLED" : null)
                    .errorMessage("CANCELLED".equals(status) ? "Streaming ASR session was cancelled" : null)
                    .build();
        }

        private void requireOpen() {
            if (!"OPEN".equals(status)) {
                throw new IllegalStateException("Streaming ASR session is already terminal");
            }
        }

        private String transcript() {
            return request.getMockTranscript() == null || request.getMockTranscript().isBlank()
                    ? "mock streaming transcript"
                    : request.getMockTranscript().trim();
        }

        private String partial(String transcript) {
            if (acceptedChunks == 0) {
                return "";
            }
            int length = (int) Math.min(transcript.length(),
                    Math.max(1, acceptedChunks * transcript.length() / 3));
            return transcript.substring(0, length);
        }

        private List<StreamingAsrWord> words(String transcript) {
            if (!request.isMockTimestampsAvailable()) {
                return List.of();
            }
            String[] tokens = transcript.split("\\s+");
            List<StreamingAsrWord> words = new ArrayList<>(tokens.length);
            long cursor = 0;
            for (String token : tokens) {
                long end = cursor + 300;
                words.add(StreamingAsrWord.builder()
                        .text(token)
                        .startMs(cursor)
                        .endMs(end)
                        .build());
                cursor = end + 100;
            }
            return List.copyOf(words);
        }
    }
}
