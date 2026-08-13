package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.enums.ResumeParseStatus;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.dto.ParseResumeDTO;
import com.codecoachai.resume.feign.vo.InnerFileDownloadVO;
import com.codecoachai.resume.feign.vo.ParseResumeVO;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.service.FileContentService;
import com.codecoachai.resume.service.ResumeAnalysisParseService;
import com.codecoachai.resume.service.extractor.ResumeTextExtractorDispatcher;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAnalysisParseServiceImpl implements ResumeAnalysisParseService {

    private static final int DEFAULT_BATCH_SIZE = 5;
    private static final int MAX_BATCH_SIZE = 20;
    private static final String DEFAULT_ERROR_MESSAGE = "简历解析失败，请稍后重试";

    private final ResumeAnalysisRecordMapper analysisRecordMapper;
    private final FileContentService fileContentService;
    private final ResumeTextExtractorDispatcher textExtractorDispatcher;
    private final AiFeignClient aiFeignClient;

    @Override
    public void parsePendingRecords(int batchSize) {
        int actualBatchSize = batchSize > 0 ? Math.min(batchSize, MAX_BATCH_SIZE) : DEFAULT_BATCH_SIZE;
        List<ResumeAnalysisRecord> records = analysisRecordMapper.selectList(new LambdaQueryWrapper<ResumeAnalysisRecord>()
                .eq(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.PENDING.getCode())
                .eq(ResumeAnalysisRecord::getDeleted, CommonConstants.NO)
                .orderByAsc(ResumeAnalysisRecord::getCreatedAt)
                .last("limit " + actualBatchSize));
        for (ResumeAnalysisRecord record : records) {
            try {
                parseOne(record);
            } catch (RuntimeException ex) {
                Long analysisRecordId = record == null ? null : record.getId();
                Long fileId = record == null ? null : record.getFileId();
                log.error("Resume analysis record parse failed, analysisRecordId={}, fileId={}",
                        analysisRecordId, fileId, ex);
            }
        }
    }

    public void parseOne(ResumeAnalysisRecord record) {
        validateClaimableRecord(record);
        if (!claim(record.getId())) {
            log.debug("Resume analysis record claim skipped, analysisRecordId={}, fileId={}",
                    record.getId(), record.getFileId());
            return;
        }
        String stage = "VALIDATE_RECORD";
        try {
            validateClaimedRecord(record);
            stage = "DOWNLOAD_FILE";
            InnerFileDownloadVO file = fileContentService.downloadResumeFile(record.getFileId(), record.getUserId());
            stage = "EXTRACT_TEXT";
            String rawText = textExtractorDispatcher.extract(file.getFileExt(), file.getContent());
            stage = "AI_PARSE";
            String structuredJson = parseByAi(record, file, rawText);
            stage = "UPDATE_WAIT_CONFIRM";
            markWaitConfirm(record, rawText, structuredJson);
        } catch (RuntimeException ex) {
            markFailed(record, stage, ex);
            throw ex;
        }
    }

    private void validateClaimableRecord(ResumeAnalysisRecord record) {
        if (record == null || record.getId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历解析记录不能为空");
        }
    }

    private void validateClaimedRecord(ResumeAnalysisRecord record) {
        if (record.getFileId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历解析文件信息缺失");
        }
        if (record.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历解析用户信息缺失");
        }
    }

    private boolean claim(Long analysisRecordId) {
        int affectedRows = analysisRecordMapper.update(null, new LambdaUpdateWrapper<ResumeAnalysisRecord>()
                .set(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.PARSING.getCode())
                .set(ResumeAnalysisRecord::getErrorMessage, null)
                .eq(ResumeAnalysisRecord::getId, analysisRecordId)
                .eq(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.PENDING.getCode())
                .eq(ResumeAnalysisRecord::getDeleted, CommonConstants.NO));
        return affectedRows == 1;
    }

    private String parseByAi(ResumeAnalysisRecord record, InnerFileDownloadVO file, String rawText) {
        ParseResumeDTO dto = new ParseResumeDTO();
        dto.setAnalysisRecordId(record.getId());
        dto.setUserId(record.getUserId());
        dto.setRawText(rawText);
        dto.setOriginalFilename(file.getOriginalFilename());
        dto.setFileExt(file.getFileExt());
        ParseResumeVO vo = FeignResultUtils.unwrap(aiFeignClient.parseResume(dto));
        if (vo == null || !StringUtils.hasText(vo.getStructuredJson())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 简历解析结果为空，请稍后重试");
        }
        return vo.getStructuredJson();
    }

    private void markWaitConfirm(ResumeAnalysisRecord record, String rawText, String structuredJson) {
        int affectedRows = analysisRecordMapper.update(null, new LambdaUpdateWrapper<ResumeAnalysisRecord>()
                .set(ResumeAnalysisRecord::getRawText, rawText)
                .set(ResumeAnalysisRecord::getStructuredJson, structuredJson)
                .set(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.WAIT_CONFIRM.getCode())
                .set(ResumeAnalysisRecord::getErrorMessage, null)
                .eq(ResumeAnalysisRecord::getId, record.getId())
                .eq(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.PARSING.getCode())
                .eq(ResumeAnalysisRecord::getDeleted, CommonConstants.NO));
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "简历解析状态更新失败，请稍后重试");
        }
    }

    private void markFailed(ResumeAnalysisRecord record, String stage, RuntimeException ex) {
        String errorMessage = resumeParseFailureMessage();
        int affectedRows = analysisRecordMapper.update(null, new LambdaUpdateWrapper<ResumeAnalysisRecord>()
                .set(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.FAILED.getCode())
                .set(ResumeAnalysisRecord::getErrorMessage, errorMessage)
                .eq(ResumeAnalysisRecord::getId, record.getId())
                .eq(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.PARSING.getCode())
                .eq(ResumeAnalysisRecord::getDeleted, CommonConstants.NO));
        log.warn("Resume analysis record marked failed, analysisRecordId={}, fileId={}, stage={}, affectedRows={}",
                record.getId(), record.getFileId(), stage, affectedRows);
    }

    private String resumeParseFailureMessage() {
        return DEFAULT_ERROR_MESSAGE;
    }
}
