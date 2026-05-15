# CodeCoachAI V2 后端能力清单

本文档用于记录 CodeCoachAI Java 后端 V2 阶段已经完成的后端能力，为后续统一 E2E 测试和前后端联调做静态基线。

## 1. V2.1 安全基线

### 1.1 `/admin/**` 服务内 ADMIN 二次校验

- 在业务服务内增加 `AdminRoleFilter`，对 `/admin/**` 做 ADMIN 角色二次校验。
- 校验来源为 Gateway 透传的 `X-Roles` Header。
- 仅精确匹配 `ADMIN`，不做大小写模糊匹配。
- `OPTIONS`、`/health`、非 `/admin/**` 路径放行。
- 无权限返回项目统一结果：`Result.fail(ErrorCode.FORBIDDEN)`。

安全边界：

- 该能力用于降低绕过 Gateway 后普通请求误入管理接口的风险。
- `X-Roles` Header 在直接访问服务端口时仍可能被伪造，不能等价为完整零信任安全。
- 生产环境仍需要配合网关签名 Header、服务端口不暴露、内网隔离或 mTLS 等能力。

### 1.2 `/inner/**` HMAC 内部调用签名

- 在 `InternalCallFilter` 中对 `/inner/**` 增加 HMAC 签名校验。
- Feign 内部调用统一通过 `OpenFeignConfig` 加签。
- Gateway 调 auth-service `/inner/auth/token-info` 时通过 `AuthTokenClient` 加签。
- 签名要素包含 method、path、timestamp、nonce、serviceName。
- 使用 Redis 保存 nonce，降低重放风险。
- Redis 异常时 fail-closed，避免内部接口降级为弱安全。

剩余安全边界：

- V2.1 仍使用全局共享密钥，不是按服务矩阵拆分密钥。
- 未引入 mTLS。
- 未引入独立权限中心。
- `/inner/**` 不允许前端直接访问，Gateway 不应对外暴露内部接口。

## 2. V2.2 AI 调用稳定性

### 2.1 mock / real 模式

- mock 模式仍作为本地演示和开发默认稳定路径。
- real 模式通过 OpenAI-compatible client 接入真实模型。
- 不强制切换真实模型，不破坏 mock 模式。

### 2.2 fallback 能力

- AI 问题生成、回答评分、追问、报告生成均具备本地兜底。
- 真实模型异常时优先保证面试主流程不中断。
- 报告生成失败场景下尽量返回可展示的 fallback 报告内容。

### 2.3 AI 调用日志

- 复用 `ai_call_log` 表。
- 在 `request_body` / `response_body` 中写入 JSON 元数据。
- 元数据包含 provider、model、mockMode、failureType、retryCount、promptTemplateVersion、latency 等信息。
- 不新增日志表字段，避免 V2.2 引入数据库迁移。

### 2.4 超时和异常分类

- Provider 调用异常增加分类：
  - `CONFIG_ERROR`
  - `TIMEOUT`
  - `HTTP_ERROR`
  - `EMPTY_RESPONSE`
  - `PARSE_ERROR`
  - `UNKNOWN_ERROR`
- interview-service Feign read timeout 已调整为大于 ai-service provider timeout，降低调用方提前超时风险。

## 3. V2.3 项目深挖

### 3.1 项目经历上下文

- resume-service 已有 `resume_project` 项目经历模型。
- interview-service 通过内部 Feign 获取简历和项目经历。
- 项目经历被格式化为结构化文本，传给 ai-service。
- 使用字段包括项目名称、项目周期、项目背景、技术栈、个人角色、个人职责、核心功能、技术难点、优化结果、项目亮点。

### 3.2 项目问题生成

- 项目阶段优先使用 `PROJECT_DEEP_DIVE_QUESTION` 场景。
- Prompt 覆盖项目背景、技术架构、数据库设计、核心难点、性能优化、故障排查、技术取舍、个人职责。
- mock 模式也会生成项目深挖语义问题。

### 3.3 项目评分

- 项目深挖阶段评分关注：
  - 项目理解
  - 技术深度
  - 表达清晰度
  - 问题解决能力
  - 架构思维
- 评分 DTO 增加内部字段 `stageType` 和 `projectContent`，不影响前端接口。

### 3.4 项目报告分析

- 报告 Prompt 增加项目表达评价、技术深度评价、项目薄弱点、改进建议要求。
- 报告复用 `interview_report.project_problems` 和 `report_content` 承载项目深挖分析。
- 不新增数据库字段。

## 4. V2.4 学习反馈闭环

### 4.1 薄弱点提取

- 报告生成后从以下信息中提取薄弱点：
  - AI 报告字段
  - `interview_message`
  - 低分回答
  - `projectProblems`
  - Java、数据库、并发、缓存、架构、项目表达相关关键词
- 结果写入 `interview_report.weak_points`。

### 4.2 推荐题目

- 复用 question-service 内部接口 `POST /inner/questions/recommend-for-report`。
- 根据 weakTags 匹配题目标题、内容、解析、参考答案。
- 无匹配时回退到高频 / 最新启用题目。
- 推荐结果写入 `interview_report.recommended_questions`。

### 4.3 复习建议

- 生成可执行复习建议，写入 `review_suggestions` 和 `suggestions`。
- 建议内容覆盖：
  - 薄弱知识点
  - 推荐复习方向
  - 推荐练习题
  - 下一轮面试建议
  - 项目表达改进
  - 技术深度提升

### 4.4 下一轮训练建议

- 报告正文 `report_content` 追加“学习反馈闭环”段落。
- 内容面向前端直接展示，回答：
  - 哪里弱
  - 为什么弱
  - 应该复习什么
  - 应该练哪些题
  - 下一轮面试重点练什么

## 5. 已知限制

- 尚未做完整运行时测试。
- 推荐题目仍是关键词匹配，不是语义检索。
- 未引入 Embedding。
- 未做语音面试。
- 未引入 MQ 异步报告链路。
- 未新增复杂学习计划表。
- 未引入 ES。
- 未引入 MinIO。
- V2 后端能力仍需后续统一 E2E 验证。
