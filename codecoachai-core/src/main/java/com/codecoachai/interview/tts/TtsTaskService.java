package com.codecoachai.interview.tts;

public interface TtsTaskService {

    TtsTaskVO create(TtsTaskCreateDTO dto);

    TtsTaskVO get(String taskId);

    TtsTaskVO cancel(String taskId);
}
