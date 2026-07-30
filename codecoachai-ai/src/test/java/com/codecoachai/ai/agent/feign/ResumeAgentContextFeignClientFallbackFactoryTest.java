package com.codecoachai.ai.agent.feign;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.codecoachai.ai.agent.domain.context.SkillGapAgentContextVO;
import com.codecoachai.common.core.domain.Result;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeAgentContextFeignClientFallbackFactoryTest {

    @Test
    void skillGapFallbackReturnsFailureInsteadOfIndistinguishableEmptySuccess() {
        ResumeAgentContextFeignClient client =
                new ResumeAgentContextFeignClientFallbackFactory().create(new IllegalStateException("timeout"));

        Result<List<SkillGapAgentContextVO>> result = client.listSkillGapAgentContext(1L, 2L);

        assertFalse(result.isSuccess());
        assertNull(result.getData());
    }
}
