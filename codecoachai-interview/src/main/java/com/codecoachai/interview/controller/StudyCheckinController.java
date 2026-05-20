package com.codecoachai.interview.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.interview.domain.entity.StudyCheckin;
import com.codecoachai.interview.domain.entity.StudyTask;
import com.codecoachai.interview.mapper.StudyCheckinMapper;
import com.codecoachai.interview.mapper.StudyTaskMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习打卡 Controller。
 * 提供每日打卡、连续天数、打卡记录查询。
 */
@Tag(name = "学习打卡")
@RestController
@RequestMapping("/study-checkins")
@RequiredArgsConstructor
public class StudyCheckinController {

    private final StudyCheckinMapper checkinMapper;
    private final StudyTaskMapper studyTaskMapper;

    @Operation(summary = "今日打卡")
    @PostMapping
    public Result<StudyCheckin> checkin(@RequestBody(required = false) CheckinDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        LocalDate today = LocalDate.now();

        // 检查是否已打卡
        StudyCheckin existing = checkinMapper.selectOne(
                new LambdaQueryWrapper<StudyCheckin>()
                        .eq(StudyCheckin::getUserId, userId)
                        .eq(StudyCheckin::getCheckinDate, today));
        if (existing != null) {
            return Result.success(existing);
        }

        // 统计今日完成任务数
        Long completedToday = studyTaskMapper.selectCount(
                new LambdaQueryWrapper<StudyTask>()
                        .eq(StudyTask::getUserId, userId)
                        .eq(StudyTask::getPlannedDate, today)
                        .eq(StudyTask::getTaskStatus, "COMPLETED"));

        StudyCheckin checkin = new StudyCheckin();
        checkin.setUserId(userId);
        checkin.setPlanId(dto != null ? dto.getPlanId() : null);
        checkin.setCheckinDate(today);
        checkin.setCompletedTasks(completedToday.intValue());
        checkin.setStudyMinutes(dto != null ? dto.getStudyMinutes() : 0);
        checkin.setNote(dto != null ? dto.getNote() : null);
        checkinMapper.insert(checkin);
        return Result.success(checkin);
    }

    @Operation(summary = "查询打卡记录（最近N天）")
    @GetMapping
    public Result<List<StudyCheckin>> list(
            @RequestParam(defaultValue = "30") Integer days) {
        Long userId = SecurityAssert.requireLoginUserId();
        LocalDate startDate = LocalDate.now().minusDays(days);
        List<StudyCheckin> records = checkinMapper.selectList(
                new LambdaQueryWrapper<StudyCheckin>()
                        .eq(StudyCheckin::getUserId, userId)
                        .ge(StudyCheckin::getCheckinDate, startDate)
                        .orderByDesc(StudyCheckin::getCheckinDate));
        return Result.success(records);
    }

    @Operation(summary = "连续打卡天数")
    @GetMapping("/streak")
    public Result<StreakVO> streak() {
        Long userId = SecurityAssert.requireLoginUserId();
        List<StudyCheckin> records = checkinMapper.selectList(
                new LambdaQueryWrapper<StudyCheckin>()
                        .eq(StudyCheckin::getUserId, userId)
                        .orderByDesc(StudyCheckin::getCheckinDate)
                        .last("limit 365"));

        int streak = 0;
        LocalDate expected = LocalDate.now();
        for (StudyCheckin record : records) {
            if (record.getCheckinDate().equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (record.getCheckinDate().equals(expected.minusDays(1)) && streak == 0) {
                // 今天还没打卡，从昨天开始算
                expected = expected.minusDays(1);
                if (record.getCheckinDate().equals(expected)) {
                    streak++;
                    expected = expected.minusDays(1);
                }
            } else {
                break;
            }
        }

        // 总打卡天数
        Long totalDays = checkinMapper.selectCount(
                new LambdaQueryWrapper<StudyCheckin>()
                        .eq(StudyCheckin::getUserId, userId));

        StreakVO vo = new StreakVO();
        vo.setCurrentStreak(streak);
        vo.setTotalCheckinDays(totalDays.intValue());
        vo.setCheckedInToday(records.stream()
                .anyMatch(r -> r.getCheckinDate().equals(LocalDate.now())));
        return Result.success(vo);
    }

    @Operation(summary = "学习进度统计")
    @GetMapping("/progress")
    public Result<ProgressVO> progress(@RequestParam(required = false) Long planId) {
        Long userId = SecurityAssert.requireLoginUserId();

        LambdaQueryWrapper<StudyTask> baseQuery = new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getUserId, userId);
        if (planId != null) {
            baseQuery.eq(StudyTask::getPlanId, planId);
        }

        Long totalTasks = studyTaskMapper.selectCount(baseQuery);
        Long completedTasks = studyTaskMapper.selectCount(
                baseQuery.clone().eq(StudyTask::getTaskStatus, "COMPLETED"));
        Long skippedTasks = studyTaskMapper.selectCount(
                baseQuery.clone().eq(StudyTask::getTaskStatus, "SKIPPED"));

        ProgressVO vo = new ProgressVO();
        vo.setTotalTasks(totalTasks.intValue());
        vo.setCompletedTasks(completedTasks.intValue());
        vo.setSkippedTasks(skippedTasks.intValue());
        vo.setPendingTasks((int) (totalTasks - completedTasks - skippedTasks));
        vo.setCompletionRate(totalTasks > 0 ? Math.round(completedTasks * 100.0 / totalTasks) : 0);
        return Result.success(vo);
    }

    @Data
    public static class CheckinDTO {
        private Long planId;
        private Integer studyMinutes;
        private String note;
    }

    @Data
    public static class StreakVO {
        private int currentStreak;
        private int totalCheckinDays;
        private boolean checkedInToday;
    }

    @Data
    public static class ProgressVO {
        private int totalTasks;
        private int completedTasks;
        private int skippedTasks;
        private int pendingTasks;
        private long completionRate;
    }
}
