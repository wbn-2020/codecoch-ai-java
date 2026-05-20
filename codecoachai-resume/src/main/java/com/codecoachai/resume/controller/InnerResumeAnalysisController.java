package com.codecoachai.resume.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简历解析任务相关 inner 接口，供 codecoachai-task 异步消费者调用。
 *
 * <ul>
 *   <li>GET  /inner/resumes/analysis-records/{id}/raw         拉取 rawText + 元数据</li>
 *   <li>POST /inner/resumes/analysis-records/{id}/complete-parse 回写解析结果</li>
 * </ul>
 */
@Slf4j
@Tag(name = "Inner-简历解析任务")
@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/resumes/analysis-records")
public class InnerResumeAnalysisController {

    private final ResumeAnalysisRecordMapper analysisRecordMapper;

    @Operation(summary = "获取简历解析任务原始数据（rawText + 元信息）")
    @GetMapping("/{id}/raw")
    public Result<RawVO> getAnalysisRaw(@PathVariable("id") Long id) {
        ResumeAnalysisRecord record = analysisRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "analysis record not found: " + id);
        }
        RawVO vo = new RawVO();
        vo.setId(record.getId());
        vo.setFileId(record.getFileId());
        vo.setUserId(record.getUserId());
        vo.setRawText(record.getRawText());
        vo.setParseStatus(record.getParseStatus());
        return Result.success(vo);
    }

    @Operation(summary = "回写 AI 解析结果（task-service 调用）")
    @PostMapping("/{id}/complete-parse")
    public Result<Void> completeParse(@PathVariable("id") Long id, @Valid @RequestBody CompleteDTO dto) {
        ResumeAnalysisRecord existing = analysisRecordMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "analysis record not found: " + id);
        }
        String status = StringUtils.hasText(dto.getParseStatus()) ? dto.getParseStatus() : "SUCCESS";

        LambdaUpdateWrapper<ResumeAnalysisRecord> upd = new LambdaUpdateWrapper<ResumeAnalysisRecord>()
                .eq(ResumeAnalysisRecord::getId, id)
                .set(ResumeAnalysisRecord::getParseStatus, status)
                .set(ResumeAnalysisRecord::getUpdatedAt, LocalDateTime.now());
        if (StringUtils.hasText(dto.getStructuredJson())) {
            upd.set(ResumeAnalysisRecord::getStructuredJson, dto.getStructuredJson());
        }
        if (StringUtils.hasText(dto.getErrorMessage())) {
            upd.set(ResumeAnalysisRecord::getErrorMessage, dto.getErrorMessage());
        }
        analysisRecordMapper.update(null, upd);
        log.info("Resume analysis record {} updated to {}", id, status);
        return Result.success();
    }

    @Data
    public static class RawVO {
        private Long id;
        private Long fileId;
        private Long userId;
        private String rawText;
        private String originalFilename;
        private String fileExt;
        private String parseStatus;
    }

    @Data
    public static class CompleteDTO {
        private String structuredJson;
        private String parseStatus;
        private String errorMessage;
        private String modelTrace;
    }
}
