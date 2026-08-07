package com.codecoachai.interview.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class InterviewReportTransactionService {

    @Transactional(rollbackFor = Exception.class)
    public void completeReportSuccess(Runnable action) {
        action.run();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void completeReportFailed(Runnable action) {
        action.run();
    }
}
