package com.codecoachai.task.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.task.feign.dto.CompleteResumeParseDTO;
import com.codecoachai.task.feign.dto.JobDescriptionParseDTO;
import com.codecoachai.task.feign.vo.JobDescriptionAnalysisVO;
import com.codecoachai.task.feign.vo.ResumeAnalysisRawVO;
import com.codecoachai.task.feign.vo.ResumeJobMatchSubmitVO;
import com.codecoachai.task.feign.vo.ResumeOptimizeSubmitVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 调用 resume-service 的 inner 接口。
 *
 * 异步消费者使用此客户端：
 *  - 拉取待解析的简历元数据 + 抽取后的纯文本
 *  - 把 AI 解析结果回写
 */
@FeignClient(name = "codecoachai-resume", contextId = "taskResumeFeignClient")
public interface ResumeFeignClient {

    /**
     * 获取简历解析任务所需的原始数据（rawText + 元信息）。
     * 由 resume-service 提供 inner 接口；如不存在则需在 resume-service 补一个。
     */
    @GetMapping("/inner/resumes/analysis-records/{id}/raw")
    Result<ResumeAnalysisRawVO> getAnalysisRaw(@PathVariable("id") Long analysisRecordId);

    /**
     * 写回解析结果。
     */
    @PostMapping("/inner/resumes/analysis-records/{id}/complete-parse")
    Result<Void> completeParse(@PathVariable("id") Long analysisRecordId,
                               @RequestBody CompleteResumeParseDTO dto);

    @PostMapping("/inner/resume-job-match/reports/{id}/execute")
    Result<ResumeJobMatchSubmitVO> executeJobMatchReport(@PathVariable("id") Long reportId);

    @PostMapping("/inner/resumes/optimize-records/{recordId}/execute")
    Result<ResumeOptimizeSubmitVO> executeResumeOptimize(@PathVariable("recordId") Long recordId);

    @PostMapping("/inner/job-targets/users/{userId}/{id}/parse")
    Result<JobDescriptionAnalysisVO> executeJobDescriptionParse(@PathVariable("userId") Long userId,
                                                                @PathVariable("id") Long targetJobId,
                                                                @RequestBody JobDescriptionParseDTO dto);
}
