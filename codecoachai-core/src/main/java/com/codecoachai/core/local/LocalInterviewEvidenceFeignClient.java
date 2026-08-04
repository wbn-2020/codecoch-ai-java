package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.controller.InnerInterviewReportController;
import com.codecoachai.resume.feign.InterviewEvidenceFeignClient;
import com.codecoachai.resume.feign.vo.InterviewWeaknessSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalInterviewEvidenceFeignClient implements InterviewEvidenceFeignClient {

    private final InnerInterviewReportController innerInterviewReportController;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<InterviewWeaknessSummaryVO> weaknessSummary(Long userId, Integer days) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(userId, "userId");
            return resultMapper.value(
                    innerInterviewReportController.weaknessSummary(userId, days),
                    InterviewWeaknessSummaryVO.class);
        });
    }
}
