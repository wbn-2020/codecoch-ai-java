package com.codecoachai.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.ai.domain.dto.AiResultFeedbackCreateDTO;
import com.codecoachai.ai.domain.entity.AiResultFeedback;
import com.codecoachai.ai.domain.vo.AiResultFeedbackStatsVO;
import com.codecoachai.ai.domain.vo.AiResultFeedbackStatsVO.FeedbackTypeCount;
import com.codecoachai.ai.domain.vo.AiResultFeedbackVO;
import com.codecoachai.ai.mapper.AiResultFeedbackMapper;
import com.codecoachai.ai.service.AiResultFeedbackService;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiResultFeedbackServiceImpl implements AiResultFeedbackService {

    private static final int MAX_TEXT_LENGTH = 500;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "HELPFUL", "NOT_HELPFUL", "INACCURATE", "NOT_MY_EXPERIENCE",
            "HALLUCINATION", "IRRELEVANT", "OUTDATED", "OTHER"
    );
    private static final Set<String> NEGATIVE_TYPES = Set.of(
            "NOT_HELPFUL", "INACCURATE", "NOT_MY_EXPERIENCE", "HALLUCINATION", "IRRELEVANT", "OUTDATED"
    );

    private final AiResultFeedbackMapper feedbackMapper;

    @Override
    public AiResultFeedbackVO create(Long userId, AiResultFeedbackCreateDTO dto) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (dto == null || !StringUtils.hasText(dto.getFeedbackType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择反馈类型");
        }
        String feedbackType = normalizeType(dto.getFeedbackType());
        if (!ALLOWED_TYPES.contains(feedbackType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "暂不支持的反馈类型");
        }

        AiResultFeedback feedback = new AiResultFeedback();
        feedback.setUserId(userId);
        feedback.setScene(limit(firstText(dto.getScene(), "GENERAL"), 64));
        feedback.setBizType(limit(dto.getBizType(), 64));
        feedback.setBizId(dto.getBizId());
        feedback.setAiCallLogId(dto.getAiCallLogId());
        feedback.setFeedbackType(feedbackType);
        feedback.setRating(normalizeRating(dto.getRating()));
        feedback.setComment(limit(dto.getComment(), MAX_TEXT_LENGTH));
        feedback.setPagePath(limit(dto.getPagePath(), 255));
        feedbackMapper.insert(feedback);
        return toVO(feedback);
    }

    @Override
    public AiResultFeedbackStatsVO stats(Integer days) {
        int rangeDays = days == null || days <= 0 ? 30 : Math.min(days, 180);
        LocalDateTime start = LocalDateTime.now().minusDays(rangeDays);
        List<AiResultFeedback> items = feedbackMapper.selectList(new LambdaQueryWrapper<AiResultFeedback>()
                .ge(AiResultFeedback::getCreatedAt, start));
        Map<String, Long> byType = items.stream()
                .map(item -> firstText(item.getFeedbackType(), "UNKNOWN"))
                .collect(Collectors.groupingBy(item -> item, Collectors.counting()));

        AiResultFeedbackStatsVO vo = new AiResultFeedbackStatsVO();
        vo.setTotalFeedbackCount((long) items.size());
        vo.setInaccurateCount(byType.getOrDefault("INACCURATE", 0L));
        vo.setHallucinationCount(byType.getOrDefault("HALLUCINATION", 0L));
        vo.setNotMyExperienceCount(byType.getOrDefault("NOT_MY_EXPERIENCE", 0L));
        long negativeCount = byType.entrySet().stream()
                .filter(entry -> NEGATIVE_TYPES.contains(entry.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        vo.setNegativeFeedbackCount(negativeCount);
        vo.setNegativeFeedbackRate(items.isEmpty() ? 0.0 : negativeCount * 1.0 / items.size());
        vo.setTypeDistribution(byType.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    FeedbackTypeCount count = new FeedbackTypeCount();
                    count.setFeedbackType(entry.getKey());
                    count.setCount(entry.getValue());
                    return count;
                })
                .toList());
        return vo;
    }

    private AiResultFeedbackVO toVO(AiResultFeedback feedback) {
        AiResultFeedbackVO vo = new AiResultFeedbackVO();
        vo.setId(feedback.getId());
        vo.setUserId(feedback.getUserId());
        vo.setScene(feedback.getScene());
        vo.setBizType(feedback.getBizType());
        vo.setBizId(feedback.getBizId());
        vo.setAiCallLogId(feedback.getAiCallLogId());
        vo.setFeedbackType(feedback.getFeedbackType());
        vo.setRating(feedback.getRating());
        vo.setComment(feedback.getComment());
        vo.setPagePath(feedback.getPagePath());
        vo.setCreatedAt(feedback.getCreatedAt());
        return vo;
    }

    private String normalizeType(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Integer normalizeRating(Integer rating) {
        if (rating == null) {
            return null;
        }
        return Math.max(1, Math.min(5, rating));
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
