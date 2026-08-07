package com.codecoachai.question.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.question.domain.entity.PracticeRecord;
import com.codecoachai.question.domain.vo.PracticeRecordAgentEvidenceVO;
import com.codecoachai.question.mapper.PracticeRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/practice-records")
public class InnerPracticeRecordController {

    private final PracticeRecordMapper practiceRecordMapper;

    @GetMapping("/users/{userId}/{recordId}/agent-evidence")
    public Result<PracticeRecordAgentEvidenceVO> getAgentEvidence(@PathVariable Long userId,
                                                                  @PathVariable Long recordId) {
        PracticeRecord record = practiceRecordMapper.selectOne(new LambdaQueryWrapper<PracticeRecord>()
                .eq(PracticeRecord::getId, recordId)
                .eq(PracticeRecord::getUserId, userId)
                .eq(PracticeRecord::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "practice record evidence not found");
        }
        PracticeRecordAgentEvidenceVO vo = new PracticeRecordAgentEvidenceVO();
        vo.setId(record.getId());
        vo.setUserId(record.getUserId());
        vo.setQuestionId(record.getQuestionId());
        vo.setSourceType(record.getSourceType());
        vo.setSourceId(record.getSourceId());
        vo.setReviewStatus(record.getReviewStatus());
        vo.setCreatedAt(record.getCreatedAt());
        return Result.success(vo);
    }
}
