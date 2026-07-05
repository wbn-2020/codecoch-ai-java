package com.codecoachai.question.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionCategory;
import com.codecoachai.question.domain.entity.QuestionRelation;
import com.codecoachai.question.domain.entity.UserQuestionRecord;
import com.codecoachai.question.domain.enums.QuestionRelationStatus;
import com.codecoachai.question.domain.enums.QuestionRelationType;
import com.codecoachai.question.mapper.QuestionCategoryMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionRelationMapper;
import com.codecoachai.question.mapper.UserQuestionRecordMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final QuestionRelationMapper questionRelationMapper;
    private final QuestionCategoryMapper questionCategoryMapper;

    // ==================== 每日推荐题目 ====================

    @Operation(summary = "每日推荐题目")
    @GetMapping("/daily-recommend")
    public Result<List<Question>> dailyRecommend(
            @RequestParam(defaultValue = "10") Integer count) {
        Long userId = SecurityAssert.requireLoginUserId();
        int safeCount = normalizeCount(count);
        int candidateLimit = Math.max(safeCount * 4, safeCount + 10);

        // 策略：优先推荐用户未做过的高频题 + 薄弱知识点相关题
        // 1. 获取用户已做过的题目ID
        List<UserQuestionRecord> records = userQuestionRecordMapper.selectList(
                new LambdaQueryWrapper<UserQuestionRecord>()
                        .eq(UserQuestionRecord::getUserId, userId)
                        .select(UserQuestionRecord::getQuestionId));
        List<Long> doneIds = records.stream()
                .map(UserQuestionRecord::getQuestionId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Set<Long> blockedIds = loadCanonicalBlockIds(doneIds);

        // 2. 查询未做过的高频题
        LambdaQueryWrapper<Question> query = new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, CommonConstants.YES)
                .eq(Question::getIsHighFrequency, CommonConstants.YES)
                .notIn(!blockedIds.isEmpty(), Question::getId, blockedIds)
                .last("limit " + candidateLimit);
        List<Question> recommended = distinctByCanonicalGroup(questionMapper.selectList(query), safeCount);

        // 3. 不够则补充普通题
        if (recommended.size() < safeCount) {
            int remaining = safeCount - recommended.size();
            Set<Long> excludeIds = new HashSet<>(blockedIds);
            excludeIds.addAll(loadCanonicalBlockIds(recommended.stream()
                    .map(Question::getId)
                    .filter(id -> id != null)
                    .toList()));
            List<Question> extra = questionMapper.selectList(
                    new LambdaQueryWrapper<Question>()
                            .eq(Question::getStatus, CommonConstants.YES)
                            .notIn(!excludeIds.isEmpty(), Question::getId, excludeIds)
                            .last("limit " + Math.max(remaining * 4, remaining + 10)));
            recommended.addAll(distinctByCanonicalGroup(extra, remaining));
        }

        return Result.success(recommended.size() > safeCount ? recommended.subList(0, safeCount) : recommended);
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
        Set<Long> categoryIds = questionMap.values().stream()
                .map(Question::getCategoryId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNameMap = loadCategoryNameMap(categoryIds);

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
                    wc.setCategoryName(resolveCategoryName(e.getKey(), categoryNameMap));
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
        int safeCount = normalizeCount(count);

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
                .last("limit " + safeCount);
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
        int safeCount = normalizeCount(dto == null ? null : dto.getCount());

        // 从题库中按条件抽题
        LambdaQueryWrapper<Question> query = new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, 1)
                .like(Question::getTitle, dto.getTopic())
                .eq(dto.getDifficulty() != null, Question::getDifficulty, dto.getDifficulty())
                .eq(dto.getCategoryId() != null, Question::getCategoryId, dto.getCategoryId())
                .last("limit " + safeCount);
        List<Question> questions = questionMapper.selectList(query);

        // 如果按标题匹配不够，尝试按 content 匹配
        if (questions.size() < safeCount) {
            List<Long> existIds = questions.stream().map(Question::getId).toList();
            int remaining = safeCount - questions.size();
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

    private int normalizeCount(Integer count) {
        if (count == null || count <= 0) {
            return 10;
        }
        return Math.min(count, 50);
    }

    private Set<Long> loadCanonicalBlockIds(List<Long> seedQuestionIds) {
        if (seedQuestionIds == null || seedQuestionIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> blockedIds = new HashSet<>(seedQuestionIds);
        List<Question> seedQuestions = questionMapper.selectBatchIds(seedQuestionIds);
        List<Long> groupIds = seedQuestions.stream()
                .map(Question::getGroupId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (!groupIds.isEmpty()) {
            questionMapper.selectList(new LambdaQueryWrapper<Question>()
                            .eq(Question::getStatus, CommonConstants.YES)
                            .in(Question::getGroupId, groupIds))
                    .stream()
                    .map(Question::getId)
                    .filter(id -> id != null)
                    .forEach(blockedIds::add);
        }
        loadSameIntentNeighborIds(seedQuestionIds).forEach(blockedIds::add);
        return blockedIds;
    }

    private List<Question> distinctByCanonicalGroup(List<Question> candidates, int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<Question> result = new ArrayList<>();
        Set<String> seenCanonicalKeys = new HashSet<>();
        Set<Long> blockedIds = new HashSet<>();
        for (Question candidate : candidates) {
            if (candidate == null || candidate.getId() == null || blockedIds.contains(candidate.getId())) {
                continue;
            }
            String canonicalKey = candidate.getGroupId() != null
                    ? "G:" + candidate.getGroupId()
                    : "Q:" + candidate.getId();
            if (!seenCanonicalKeys.add(canonicalKey)) {
                continue;
            }
            result.add(candidate);
            blockedIds.addAll(loadSameIntentNeighborIds(List.of(candidate.getId())));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private Set<Long> loadSameIntentNeighborIds(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Set.of();
        }
        List<QuestionRelation> relations = questionRelationMapper.selectList(new LambdaQueryWrapper<QuestionRelation>()
                .eq(QuestionRelation::getRelationType, QuestionRelationType.SAME_INTENT.name())
                .eq(QuestionRelation::getRelationStatus, QuestionRelationStatus.ACTIVE.name())
                .and(wrapper -> wrapper.in(QuestionRelation::getSourceQuestionId, questionIds)
                        .or()
                        .in(QuestionRelation::getTargetQuestionId, questionIds)));
        Set<Long> ids = new HashSet<>();
        Set<Long> seedIds = new HashSet<>(questionIds);
        for (QuestionRelation relation : relations) {
            if (relation.getSourceQuestionId() != null && !seedIds.contains(relation.getSourceQuestionId())) {
                ids.add(relation.getSourceQuestionId());
            }
            if (relation.getTargetQuestionId() != null && !seedIds.contains(relation.getTargetQuestionId())) {
                ids.add(relation.getTargetQuestionId());
            }
        }
        return ids;
    }

    private Map<Long, String> loadCategoryNameMap(Set<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Map.of();
        }
        return questionCategoryMapper.selectList(new LambdaQueryWrapper<QuestionCategory>()
                        .in(QuestionCategory::getId, categoryIds))
                .stream()
                .filter(category -> category.getId() != null && category.getCategoryName() != null)
                .collect(Collectors.toMap(
                        QuestionCategory::getId,
                        QuestionCategory::getCategoryName,
                        (left, right) -> left));
    }

    private String resolveCategoryName(Long categoryId, Map<Long, String> categoryNameMap) {
        if (categoryId == null || categoryId <= 0) {
            return "未分类题目";
        }
        String name = categoryNameMap.get(categoryId);
        return name == null || name.isBlank() ? "题目分类 " + categoryId : name;
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
        private String categoryName;
        private int totalCount;
        private int wrongCount;
        private long wrongRate;
    }
}
