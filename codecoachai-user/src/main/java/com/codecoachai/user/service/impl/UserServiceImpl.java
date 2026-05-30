package com.codecoachai.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.constant.SecurityConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.user.convert.UserConvert;
import com.codecoachai.user.domain.dto.AdminUserQueryDTO;
import com.codecoachai.user.domain.dto.InnerCreateUserDTO;
import com.codecoachai.user.domain.dto.InnerResetPasswordDTO;
import com.codecoachai.user.domain.dto.UpdatePasswordDTO;
import com.codecoachai.user.domain.dto.UpdateUserProfileDTO;
import com.codecoachai.user.domain.dto.UpdateUserStatusDTO;
import com.codecoachai.user.domain.entity.SysUser;
import com.codecoachai.user.domain.entity.SysUserRole;
import com.codecoachai.user.domain.vo.AdminUserPageVO;
import com.codecoachai.user.domain.vo.InnerCreateUserVO;
import com.codecoachai.user.domain.vo.InnerUserAuthVO;
import com.codecoachai.user.domain.vo.InnerUserBasicVO;
import com.codecoachai.user.domain.vo.InnerUserRoleVO;
import com.codecoachai.user.domain.vo.UserDashboardOverviewVO;
import com.codecoachai.user.domain.vo.UserOverviewVO;
import com.codecoachai.user.domain.vo.UserProfileVO;
import com.codecoachai.user.mapper.SysUserMapper;
import com.codecoachai.user.mapper.SysUserRoleMapper;
import com.codecoachai.user.service.RoleService;
import com.codecoachai.user.service.UserService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public UserProfileVO getCurrentUserProfile() {
        Long userId = requireCurrentUserId();
        SysUser user = getUserOrThrow(userId);
        return UserConvert.toProfileVO(user, roleService.listRoleCodesByUserId(userId));
    }

    @Override
    public UserProfileVO updateCurrentUserProfile(UpdateUserProfileDTO dto) {
        Long userId = requireCurrentUserId();
        SysUser user = getUserOrThrow(userId);
        user.setNickname(dto.getNickname());
        user.setAvatarUrl(dto.getAvatarUrl());
        user.setEmail(dto.getEmail());
        sysUserMapper.updateById(user);
        return getCurrentUserProfile();
    }

    @Override
    public void updateCurrentUserPassword(UpdatePasswordDTO dto) {
        Long userId = requireCurrentUserId();
        SysUser user = getUserOrThrow(userId);
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_ERROR);
        }
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        sysUserMapper.updateById(user);
    }

    @Override
    public UserProfileVO updateAvatar(String avatarUrl) {
        Long userId = requireCurrentUserId();
        SysUser user = getUserOrThrow(userId);
        user.setAvatarUrl(avatarUrl);
        sysUserMapper.updateById(user);
        return getCurrentUserProfile();
    }

    @Override
    public void updatePhone(String phone) {
        Long userId = requireCurrentUserId();
        SysUser user = getUserOrThrow(userId);
        user.setPhone(phone);
        sysUserMapper.updateById(user);
    }

    @Override
    public UserOverviewVO getOverview() {
        Long userId = requireCurrentUserId();
        return UserOverviewVO.builder()
                .resumeCount(toInt(count("resume", "deleted = 0 AND user_id = ?", userId)))
                .interviewCount(toInt(count("interview_session", "deleted = 0 AND user_id = ?", userId)))
                .completedInterviewCount(toInt(count("interview_session",
                        "deleted = 0 AND user_id = ? AND status IN ('FINISHED','COMPLETED')", userId)))
                .questionAnsweredCount(toInt(count("practice_record", "deleted = 0 AND user_id = ?", userId)))
                .wrongQuestionCount(toInt(count("wrong_record", "deleted = 0 AND user_id = ?", userId)))
                .favoriteQuestionCount(toInt(count("user_favorite", "deleted = 0 AND user_id = ?", userId)))
                .build();
    }

    @Override
    public UserDashboardOverviewVO getDashboardOverview() {
        Long userId = requireCurrentUserId();
        UserDashboardOverviewVO vo = new UserDashboardOverviewVO();
        vo.setResumeCount(count("resume", "deleted = 0 AND user_id = ?", userId));
        vo.setRecentResumeParse(recentResumeParse(userId));
        vo.setRecentResumeOptimize(recentResumeOptimize(userId));
        vo.setInterviewCount(count("interview_session", "deleted = 0 AND user_id = ?", userId));
        vo.setRecentInterview(recentInterview(userId));
        vo.setRecentReport(recentReport(userId));
        vo.setStudyPlanCount(count("study_plan", "deleted = 0 AND user_id = ?", userId));
        vo.setActiveStudyPlan(activeStudyPlan(userId));
        vo.setTodayTaskCount(count("study_task",
                "deleted = 0 AND user_id = ? AND planned_date = CURDATE()", userId));
        vo.setTodayCompletedTaskCount(count("study_task",
                "deleted = 0 AND user_id = ? AND planned_date = CURDATE() AND task_status IN ('DONE','COMPLETED')",
                userId));
        vo.setEntryStatuses(entryStatuses(vo));
        vo.setGeneratedAt(LocalDateTime.now());
        return vo;
    }

    @Override
    public PageResult<AdminUserPageVO> pageAdminUsers(AdminUserQueryDTO query) {
        requireAdmin();
        Page<SysUser> page = sysUserMapper.selectAdminUserPage(Page.of(query.getPageNo(), query.getPageSize()),
                normalizeKeyword(query.getKeyword()), query.getStatus(), normalizeKeyword(query.getRoleCode()));
        Map<Long, List<String>> roleMap = listRoleCodesByUserIds(page.getRecords().stream()
                .map(SysUser::getId)
                .toList());
        List<AdminUserPageVO> records = page.getRecords().stream()
                .map(user -> UserConvert.toAdminUserPageVO(user, roleMap.getOrDefault(user.getId(), List.of())))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void updateUserStatus(Long id, UpdateUserStatusDTO dto) {
        requireAdmin();
        if (!CommonConstants.YES.equals(dto.getStatus()) && !CommonConstants.NO.equals(dto.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status 只能是0或1");
        }
        Long currentUserId = requireCurrentUserId();
        if (currentUserId.equals(id) && CommonConstants.NO.equals(dto.getStatus())) {
            throw new BusinessException(ErrorCode.DISABLE_SELF_NOT_ALLOWED);
        }
        SysUser user = getUserOrThrow(id);
        user.setStatus(dto.getStatus());
        sysUserMapper.updateById(user);
    }

    @Override
    public String resetPassword(Long id) {
        requireAdmin();
        SysUser user = getUserOrThrow(id);
        String newPassword = "Cc@" + System.currentTimeMillis() % 100000;
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(user);
        return newPassword;
    }

    @Override
    public InnerUserAuthVO getInnerUserByUsername(String username) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return UserConvert.toInnerUserAuthVO(user, roleService.listRoleCodesByUserId(user.getId()));
    }

    @Override
    public InnerUserAuthVO getInnerUserByEmail(String email) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email)
                .last("limit 1"));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return UserConvert.toInnerUserAuthVO(user, roleService.listRoleCodesByUserId(user.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InnerCreateUserVO createInnerUser(InnerCreateUserDTO dto) {
        // 注册由 auth 服务发起，这里在同一事务内创建用户并绑定默认角色，避免出现无角色账号。
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(dto.getPasswordHash());
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setStatus(SecurityConstants.USER_STATUS_ENABLED);
        sysUserMapper.insert(user);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(roleService.getRoleIdByCode(SecurityConstants.ROLE_USER));
        sysUserRoleMapper.insert(userRole);
        return UserConvert.toInnerCreateUserVO(user);
    }

    @Override
    public InnerUserRoleVO getInnerUserRoles(Long id) {
        getUserOrThrow(id);
        InnerUserRoleVO vo = new InnerUserRoleVO();
        vo.setUserId(id);
        vo.setRoles(roleService.listRoleCodesByUserId(id));
        return vo;
    }

    @Override
    public InnerUserBasicVO getInnerUser(Long id) {
        SysUser user = getUserOrThrow(id);
        return UserConvert.toInnerUserBasicVO(user, roleService.listRoleCodesByUserId(id));
    }

    @Override
    public void resetInnerPassword(Long id, InnerResetPasswordDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "passwordHash is required");
        }
        SysUser user = getUserOrThrow(id);
        user.setPasswordHash(dto.getPasswordHash());
        sysUserMapper.updateById(user);
    }

    private SysUser getUserOrThrow(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private Long requireCurrentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private void requireAdmin() {
        requireCurrentUserId();
        if (!LoginUserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String normalizeKeyword(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Map<Long, List<String>> listRoleCodesByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(userIds.size(), "?"));
        String sql = """
                SELECT ur.user_id, r.role_code
                FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id
                WHERE ur.deleted = 0
                  AND r.deleted = 0
                  AND r.status = 1
                  AND ur.user_id IN (%s)
                ORDER BY ur.user_id ASC, r.id ASC
                """.formatted(placeholders);
        Map<Long, List<String>> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, userIds.toArray(), rs -> {
            Long userId = rs.getLong("user_id");
            String roleCode = rs.getString("role_code");
            result.computeIfAbsent(userId, ignored -> new java.util.ArrayList<>()).add(roleCode);
        });
        return result;
    }

    private UserDashboardOverviewVO.RecentResumeParseVO recentResumeParse(Long userId) {
        if (!tableExists("resume_analysis_record")) {
            return null;
        }
        String sql = """
                SELECT r.id, r.resume_id, f.original_filename, r.parse_status, r.updated_at
                FROM resume_analysis_record r
                LEFT JOIN file_info f ON f.id = r.file_id AND f.deleted = 0
                WHERE r.deleted = 0 AND r.user_id = ?
                ORDER BY r.updated_at DESC, r.id DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }
            UserDashboardOverviewVO.RecentResumeParseVO vo = new UserDashboardOverviewVO.RecentResumeParseVO();
            vo.setAnalysisRecordId(rs.getLong("id"));
            vo.setResumeId(nullableLong(rs, "resume_id"));
            vo.setFileName(rs.getString("original_filename"));
            vo.setParseStatus(rs.getString("parse_status"));
            vo.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
            return vo;
        }, userId);
    }

    private UserDashboardOverviewVO.RecentResumeOptimizeVO recentResumeOptimize(Long userId) {
        if (!tableExists("resume_optimize_record")) {
            return null;
        }
        String sql = """
                SELECT id, resume_id, optimize_status, ai_call_log_id, updated_at
                FROM resume_optimize_record
                WHERE deleted = 0 AND user_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }
            UserDashboardOverviewVO.RecentResumeOptimizeVO vo = new UserDashboardOverviewVO.RecentResumeOptimizeVO();
            vo.setOptimizeRecordId(rs.getLong("id"));
            vo.setResumeId(nullableLong(rs, "resume_id"));
            vo.setOptimizeStatus(rs.getString("optimize_status"));
            vo.setAiCallLogId(nullableLong(rs, "ai_call_log_id"));
            vo.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
            return vo;
        }, userId);
    }

    private UserDashboardOverviewVO.RecentInterviewVO recentInterview(Long userId) {
        String sql = """
                SELECT id, title, status, report_status, updated_at
                FROM interview_session
                WHERE deleted = 0 AND user_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }
            UserDashboardOverviewVO.RecentInterviewVO vo = new UserDashboardOverviewVO.RecentInterviewVO();
            vo.setInterviewId(rs.getLong("id"));
            vo.setTitle(rs.getString("title"));
            vo.setStatus(rs.getString("status"));
            vo.setReportStatus(rs.getString("report_status"));
            vo.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
            return vo;
        }, userId);
    }

    private UserDashboardOverviewVO.RecentReportVO recentReport(Long userId) {
        String sql = """
                SELECT r.id, r.session_id, r.status, r.total_score, r.generated_at
                FROM interview_report r
                JOIN interview_session s ON s.id = r.session_id AND s.deleted = 0
                WHERE r.deleted = 0 AND s.user_id = ?
                ORDER BY COALESCE(r.generated_at, r.updated_at) DESC, r.id DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }
            UserDashboardOverviewVO.RecentReportVO vo = new UserDashboardOverviewVO.RecentReportVO();
            vo.setReportId(rs.getLong("id"));
            vo.setInterviewId(rs.getLong("session_id"));
            vo.setStatus(rs.getString("status"));
            vo.setTotalScore(nullableInt(rs, "total_score"));
            vo.setGeneratedAt(toLocalDateTime(rs.getTimestamp("generated_at")));
            return vo;
        }, userId);
    }

    private UserDashboardOverviewVO.ActiveStudyPlanVO activeStudyPlan(Long userId) {
        String sql = """
                SELECT id, plan_title, plan_status, updated_at
                FROM study_plan
                WHERE deleted = 0 AND user_id = ? AND plan_status = 'ACTIVE'
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }
            Long planId = rs.getLong("id");
            long total = count("study_task", "deleted = 0 AND plan_id = ?", planId);
            long done = count("study_task", "deleted = 0 AND plan_id = ? AND task_status IN ('DONE','COMPLETED')", planId);
            UserDashboardOverviewVO.ActiveStudyPlanVO vo = new UserDashboardOverviewVO.ActiveStudyPlanVO();
            vo.setPlanId(planId);
            vo.setPlanTitle(rs.getString("plan_title"));
            vo.setPlanStatus(rs.getString("plan_status"));
            vo.setTotalTaskCount(toInt(total));
            vo.setDoneTaskCount(toInt(done));
            vo.setProgressPercent(total == 0 ? 0 : Math.toIntExact(done * 100 / total));
            vo.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
            return vo;
        }, userId);
    }

    private List<UserDashboardOverviewVO.EntryStatusVO> entryStatuses(UserDashboardOverviewVO vo) {
        return List.of(
                entryStatus("resume", vo.getRecentResumeParse() == null ? "TODO" : "AVAILABLE",
                        vo.getRecentResumeParse() == null ? "No resume parse record found." : "Resume parse record available.",
                        vo.getRecentResumeParse() == null ? null : vo.getRecentResumeParse().getAnalysisRecordId()),
                entryStatus("interview", vo.getRecentInterview() == null ? "TODO" : "AVAILABLE",
                        vo.getRecentInterview() == null ? "No interview found." : "Recent interview available.",
                        vo.getRecentInterview() == null ? null : vo.getRecentInterview().getInterviewId()),
                entryStatus("studyPlan", vo.getActiveStudyPlan() == null ? "TODO" : "CONTINUE",
                        vo.getActiveStudyPlan() == null ? "No active study plan found." : "Active study plan available.",
                        vo.getActiveStudyPlan() == null ? null : vo.getActiveStudyPlan().getPlanId())
        );
    }

    private UserDashboardOverviewVO.EntryStatusVO entryStatus(String key, String status, String reason, Long relatedId) {
        UserDashboardOverviewVO.EntryStatusVO vo = new UserDashboardOverviewVO.EntryStatusVO();
        vo.setKey(key);
        vo.setStatus(status);
        vo.setReason(reason);
        vo.setRelatedId(relatedId);
        return vo;
    }

    private long count(String tableName, String condition, Object... args) {
        if (!tableExists(tableName)) {
            return 0L;
        }
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM `" + tableName + "` WHERE " + condition,
                Long.class, args);
        return count == null ? 0L : count;
    }

    private boolean tableExists(String tableName) {
        String sql = "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, tableName);
        return count != null && count > 0;
    }

    private int toInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime toLocalDateTime(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
