package com.codecoachai.ai.agent.feign;

import com.codecoachai.ai.agent.feign.vo.PracticeRecordEvidenceVO;
import com.codecoachai.common.core.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "codecoachai-question", contextId = "questionPracticeEvidenceFeignClient")
public interface QuestionPracticeEvidenceFeignClient {

    @GetMapping("/inner/practice-records/users/{userId}/{recordId}/agent-evidence")
    Result<PracticeRecordEvidenceVO> getPracticeRecordEvidence(@PathVariable("userId") Long userId,
                                                               @PathVariable("recordId") Long recordId);
}
