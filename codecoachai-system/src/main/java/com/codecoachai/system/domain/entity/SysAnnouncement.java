package com.codecoachai.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 系统公告。
 */
@Data
@TableName("sys_announcement")
public class SysAnnouncement {
    private Long id;
    private String title;
    private String content;
    private String type;
    private Integer status;
    private String targetUsers;
    private Long createdBy;
    private LocalDateTime publishedAt;
    private LocalDateTime expiredAt;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
