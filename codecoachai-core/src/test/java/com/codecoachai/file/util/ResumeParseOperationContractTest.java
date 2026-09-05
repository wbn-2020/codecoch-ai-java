package com.codecoachai.file.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResumeParseOperationContractTest {

    @Test
    void cancelledOperationIsTerminalAndCannotBeRetried() {
        ResumeParseOperationContract.State state =
                ResumeParseOperationContract.from("cancelled");

        assertEquals("CANCELLED", state.operationStatus());
        assertFalse(state.cancellable());
        assertFalse(state.retryable());
        assertFalse(state.exposesError());
    }

    @Test
    void failedOperationRemainsRetryableAndExposesItsError() {
        ResumeParseOperationContract.State state =
                ResumeParseOperationContract.from("FAILED");

        assertEquals("FAILED", state.operationStatus());
        assertFalse(state.cancellable());
        assertTrue(state.retryable());
        assertTrue(state.exposesError());
    }
}
