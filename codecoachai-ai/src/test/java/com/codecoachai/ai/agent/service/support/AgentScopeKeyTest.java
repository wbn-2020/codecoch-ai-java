package com.codecoachai.ai.agent.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgentScopeKeyTest {

    @Test
    void writesCanonicalAndReadsLegacyAliases() {
        assertEquals("TARGET_JOB:42", AgentScopeKey.write(42L));
        assertEquals("GLOBAL", AgentScopeKey.normalize("ALL"));
        assertEquals("TARGET_JOB:42", AgentScopeKey.normalize("JOB:42"));
        assertEquals("TARGET_JOB:42", AgentScopeKey.normalize("42"));
        assertTrue(AgentScopeKey.readAliases("42").contains("JOB:42"));
        assertTrue(AgentScopeKey.readAliases("TARGET_JOB:42").contains("42"));
    }
}
