package com.codecoachai.interview.service;

import com.codecoachai.interview.domain.dto.AsrRequest;
import com.codecoachai.interview.domain.vo.AsrResult;

public interface AsrService {

    AsrResult transcribe(AsrRequest request);
}
