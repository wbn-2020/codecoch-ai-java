package com.codecoachai.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("slow_sql_log")
public class SlowSqlLog {
    private Long id;
    private String mapperId;
    private String sqlCommandType;
    private String sqlText;
    private String parameterSummary;
    private String databaseName;
    private Long costMs;
    private Long thresholdMs;
    private Integer resultSize;
    private LocalDateTime createdAt;
}
