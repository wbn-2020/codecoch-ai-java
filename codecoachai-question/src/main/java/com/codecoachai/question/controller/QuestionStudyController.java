package com.codecoachai.question.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.UserQuestionRecord;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.UserQuestionRecordMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户学习辅助 Controller。
 * 提供每日推荐、薄弱分析、错题重刷、专项练习生成。
 */
@Tag(name = "学习辅助")
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class QuestionStudyController {

    private final QuestionMapper questionMapper;
    private final UserQuestionRecordMapper userQuestionRecordMapper;

    // ==================== 每日推荐题目 ====================

    @Operation(summary = "每日推荐题目")
    @GetMapping("/daily-recommend")
    public Result<List<Question>> dailyRecommend(
            @RequestParam(defaultValue = "10") Integer count) {
        Long userId = SecurityAssert.requireLoginUserId();

        // 策略：优先推荐用户未做过的高频题 + 薄弱知识点相关题
        // 1. 获取用户已做过的题目ID
        List<UserQuestionRecord> records = userQuestionRecordMapper.selectList(
                new LambdaQueryWrapper<UserQuestionRecord>()
                        .eq(UserQuestionRecord::getUserId, userId)
                        .select(UserQuestionRecord::getQuestionId));
        List<Long> doneIds = records.stream().map(UserQuestionRecord::getQuestionId).toList();

        // 2. 查询未做过的高频题
        LambdaQueryWrapper<Question> query = new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, 1)
                .eq(Question::getIsHighFrequency, 1)
                .notIn(!doneIds.isEmpty(), Question::getId, doneIds)
                .last("limit " + count);
        List<Question> recommended = questionMapper.selectList(query);

        // 3. 不够则补充普通题
        if (recommended.size() < count) {
            int remaining = count - recommended.size();
            List<Long> excludeIds = new ArrayList<>(doneIds);
            excludeIds.addAll(recommended.stream().map(Question::getId).toList());
            List<Question> extra = questionMapper.selectList(
                    new LambdaQueryWrapper<Question>()
                            .eq(Question::getStatus, 1)
                            .notIn(!excludeIds.isEmpty(), Question::getId, excludeIds)
                            .last("limit " + remaining));
            recommended.addAll(extra);
        }

        return Result.success(recommended);
    }

    // ==================== 薄弱知识点分析 ====================

    @Operation(summary = "薄弱知识点分析")
    @GetMapping("/weakness-analysis")
    public Result<WeaknessAnalysisVO> weaknessAnalysis() {
        Long userId = SecurityAssert.requireLoginUserId();

        // 查询用户所有答题记录
        List<UserQuestionRecord> records = userQuestionRecordMapper.selectList(
                new LambdaQueryWrapper<UserQuestionRecord>()
                        .eq(UserQuestionRecord::getUserId, userId));

        if (records.isEmpty()) {
            WeaknessAnalysisVO vo = new WeaknessAnalysisVO();
            vo.setTotalAnswered(0);
            vo.setCorrectRate(0);
            vo.setWeakCategories(List.of());
            vo.setWeakDifficulties(List.of());
            return Result.success(vo);
        }

        // 按分类统计错误率
        Map<Long, int[]> categoryStats = new HashMap<>(); // categoryId -> [total, wrong]
        List<Long> questionIds = records.stream().map(UserQuestionRecord::getQuestionId).distinct().toList();
        Map<Long, Question> questionMap = questionMapper.selectList(
                        new LambdaQueryWrapper<Question>().in(Question::getId, questionIds))
                .stream().collect(Collectors.toMap(Question::getId, q -> q));

        int totalAnswered = records.size();
        int totalCorrect = 0;

        for (UserQuestionRecord r : records) {
            Question q = questionMap.get(r.getQuestionId());
            if (q == null) continue;
            Long catId = q.getCategoryId();
            if (catId == null) catId = 0L;
            categoryStats.computeIfAbsent(catId, k -> new int[]{0, 0});
            categoryStats.get(catId)[0]++;
            if (Integer.valueOf(1).equals(r.getWrong())) {
                categoryStats.get(catId)[1]++;
            } else {
                totalCorrect++;
            }
        }

        // 找出错误率最高的分类
        List<WeakCategoryVO> weakCategories = categoryStats.entrySet().stream()
                .filter(e -> e.getValue()[0] >= 3) // 至少做过3题
                .map(e -> {
                    WeakCategoryVO wc = new WeakCategoryVO();
                    wc.setCategoryId(e.getKey());
                    wc.setTotalCount(e.getValue()[0]);
                    wc.setWrongCount(e.getValue()[1]);
                    wc.setWrongRate(Math.round(e.getValue()[1] * 100.0 / e.getValue()[0]));
                    return wc;
                })
                .sorted((a, b) -> Long.compare(b.getWrongRate(), a.getWrongRate()))
                .limit(5)
                .toList();

        WeaknessAnalysisVO vo = new WeaknessAnalysisVO();
        vo.setTotalAnswered(totalAnswered);
        vo.setCorrectRate(totalAnswered > 0 ? Math.round(totalCorrect * 100.0 / totalAnswered) : 0);
        vo.setWeakCategories(weakCategories);
        vo.setWeakDifficulties(List.of()); // 可扩展
        return Result.success(vo);
    }

    // ==================== 错题重刷 ====================

    @Operation(summary = "错题重刷（获取错题列表用于重新练习）")
    @GetMapping("/wrong-records/retry")
    public Result<List<Question>> wrongRetry(
            @RequestParam(defaultValue = "10") Integer count,
            @RequestParam(required = false) Long categoryId) {
        Long userId = SecurityAssert.requireLoginUserId();

        // 查询用户错题记录
        LambdaQueryWrapper<UserQuestionRecord> recordQuery = new LambdaQueryWrapper<UserQuestionRecord>()
                .eq(UserQuestionRecord::getUserId, userId)
                .eq(UserQuestionRecord::getWrong, 1)
                .select(UserQuestionRecord::getQuestionId);
        List<Long> wrongIds = userQuestionRecordMapper.selectList(recordQuery)
                .stream().map(UserQuestionRecord::getQuestionId).distinct().toList();

        if (wrongIds.isEmpty()) {
            return Result.success(List.of());
        }

        // 查询题目
        LambdaQueryWrapper<Question> qQuery = new LambdaQueryWrapper<Question>()
                .in(Question::getId, wrongIds)
                .eq(Question::getStatus, 1)
                .eq(categoryId != null, Question::getCategoryId, categoryId)
                .last("limit " + count);
        List<Question> questions = questionMapper.selectList(qQuery);

        // 随机打乱
        List<Question> shuffled = new ArrayList<>(questions);
        Collections.shuffle(shuffled);
        return Result.success(shuffled);
    }

    // ==================== 专项练习生成 ====================

    @Operation(summary = "专项练习生成（根据主题/知识点从题库抽题）")
    @PostMapping("/practice/generate")
    public Result<List<Question>> generatePractice(@Valid @RequestBody PracticeGenerateDTO dto) {
        SecurityAssert.requireLoginUserId();

        // 从题库中按条件抽题
        LambdaQueryWrapper<Question> query = new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, 1)
                .like(Question::getTitle, dto.getTopic())
                .eq(dto.getDifficulty() != null, Question::getDifficulty, dto.getDifficulty())
                .eq(dto.getCategoryId() != null, Question::getCategoryId, dto.getCategoryId())
                .last("limit " + (dto.getCount() != null ? dto.getCount() : 10));
        List<Question> questions = questionMapper.selectList(query);

        // 如果按标题匹配不够，尝试按 content 匹配
        if (questions.size() < (dto.getCount() != null ? dto.getCount() : 10)) {
            List<Long> existIds = questions.stream().map(Question::getId).toList();
            int remaining = (dto.getCount() != null ? dto.getCount() : 10) - questions.size();
            List<Question> extra = questionMapper.selectList(
                    new LambdaQueryWrapper<Question>()
                            .eq(Question::getStatus, 1)
                            .like(Question::getContent, dto.getTopic())
                            .notIn(!existIds.isEmpty(), Question::getId, existIds)
                            .eq(dto.getDifficulty() != null, Question::getDifficulty, dto.getDifficulty())
                            .last("limit " + remaining));
            questions.addAll(extra);
        }

        // 随机打乱
        List<Question> shuffled = new ArrayList<>(questions);
        Collections.shuffle(shuffled);
        return Result.success(shuffled);
    }

    // ==================== DTO / VO ====================

    @Data
    public static class PracticeGenerateDTO {
        @NotBlank(message = "练习主题不能为空")
        private String topic;
        private String difficulty;
        private Long categoryId;
        private Integer count;
    }

    @Data
    public static class WeaknessAnalysisVO {
        private int totalAnswered;
        private long correctRate;
        private List<WeakCategoryVO> weakCategories;
        private List<String> weakDifficulties;
    }

    @Data
    public static class WeakCategoryVO {
        private Long categoryId;
        private int totalCount;
        private int wrongCount;
        private long wrongRate;
    }
}
