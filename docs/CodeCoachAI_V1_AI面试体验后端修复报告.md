# CodeCoachAI V1 AI 面试体验后端修复报告

## 1. 基本信息

- 分支：dev
- 当前提交：4dcefe0
- 修改时间：2026-05-14
- AI 模式：本地 smoke 运行态 AI 日志显示模型为 `deepseek-v4-flash`，本轮不把 Mock 当作真实 AI 验收
- 是否引入新中间件：否
- 本轮边界：只改后端，不改前端，不引入 MQ、ES、MinIO、SSE、任务中心或 Prompt 版本管理

## 2. 修改文件列表

### codecoachai-ai

- `codecoachai-ai/src/main/java/com/codecoachai/ai/domain/dto/EvaluateAnswerDTO.java`
- `codecoachai-ai/src/main/java/com/codecoachai/ai/domain/dto/GenerateFollowUpDTO.java`
- `codecoachai-ai/src/main/java/com/codecoachai/ai/domain/dto/GenerateInterviewQuestionDTO.java`
- `codecoachai-ai/src/main/java/com/codecoachai/ai/domain/dto/GenerateReportDTO.java`
- `codecoachai-ai/src/main/java/com/codecoachai/ai/domain/vo/EvaluateAnswerVO.java`
- `codecoachai-ai/src/main/java/com/codecoachai/ai/domain/vo/GenerateFollowUpVO.java`
- `codecoachai-ai/src/main/java/com/codecoachai/ai/service/impl/AiServiceImpl.java`

### codecoachai-interview

- `codecoachai-interview/src/main/java/com/codecoachai/interview/config/InterviewAsyncConfig.java`
- `codecoachai-interview/src/main/java/com/codecoachai/interview/domain/vo/SubmitInterviewAnswerVO.java`
- `codecoachai-interview/src/main/java/com/codecoachai/interview/feign/dto/EvaluateAnswerDTO.java`
- `codecoachai-interview/src/main/java/com/codecoachai/interview/feign/dto/GenerateFollowUpDTO.java`
- `codecoachai-interview/src/main/java/com/codecoachai/interview/feign/dto/GenerateInterviewQuestionDTO.java`
- `codecoachai-interview/src/main/java/com/codecoachai/interview/feign/dto/GenerateReportDTO.java`
- `codecoachai-interview/src/main/java/com/codecoachai/interview/feign/vo/EvaluateAnswerVO.java`
- `codecoachai-interview/src/main/java/com/codecoachai/interview/feign/vo/GenerateFollowUpVO.java`
- `codecoachai-interview/src/main/java/com/codecoachai/interview/service/impl/InterviewReportAsyncService.java`
- `codecoachai-interview/src/main/java/com/codecoachai/interview/service/impl/InterviewServiceImpl.java`

### SQL

- `sql/init.sql`
- `sql/dev_test_data.sql`
- `sql/v1_ai_interview_experience_patch.sql`

## 3. P0 修复表

| 问题 | 修复方式 | 修改文件 | 验证方式 | 是否通过 |
|---|---|---|---|---|
| 追问与原题相关性弱 | DTO 增加 `rootQuestionContent`、`currentQuestionContent`、`referenceAnswer`、`knowledgePoints`，interview 侧沿 `parentMessageId` 回溯原始主问题 | AI DTO/Feign DTO、`InterviewServiceImpl` | smoke 中追问围绕当前阶段原题；日志保留 raw/final | 通过 |
| 无效追问过滤 | AI 侧过滤“假设原问题”“请提供具体”“用户增长”“团队协作”等无效内容；无效时使用本地兜底追问并在 responseContent 标记 fallback | `AiServiceImpl` | 直接调用 `/inner/ai/interview/follow-up` 验证返回不包含无效话术 | 通过 |
| 评分与追问合并 | `evaluate` 一次返回 `score/comment/nextAction/followUpQuestion/followUpReason/knowledgePoints`；主链路优先使用评分结果里的追问 | `EvaluateAnswerVO`、`InterviewServiceImpl` | 提交一次回答只新增 1 条 `INTERVIEW_ANSWER_EVALUATE` 日志，无额外 `FOLLOW_UP` 日志 | 通过 |
| AI 日志记录 | 成功日志记录 raw response 和 final response；fallback 信息写入 `responseContent` 或 reason 字段 | `AiServiceImpl` | `/admin/ai/logs/page?scene=INTERVIEW_ANSWER_EVALUATE` 返回 `code=0`，total=12 | 通过 |

## 4. P1 修复表

| 问题 | 修复方式 | 修改文件 | 验证方式 | 是否通过 |
|---|---|---|---|---|
| 阶段题数和题目跨度 | 去掉固定 `QUESTIONS_PER_STAGE=1` 的主导影响，按 stageType 设置 `expectedQuestionCount`，阶段完成判断读取 DB 字段 | `InterviewServiceImpl` | 新建综合面试前四阶段为 `OPENING=1; JAVA_BASIC=2; DATABASE=2; CACHE_MQ=2` | 通过 |
| 结束面试轻量异步 | 新增 `InterviewAsyncConfig` 和 `InterviewReportAsyncService`，`finish` 只落 `GENERATING` 并 afterCommit 提交异步任务 | `InterviewAsyncConfig`、`InterviewReportAsyncService`、`InterviewServiceImpl` | `POST /interviews/{id}/finish` 128ms 返回 `REPORT_GENERATING/GENERATING` | 通过 |
| 报告生成状态 | `GET /report` 支持 `GENERATING` 临时报告对象；异步完成后更新 `GENERATED`；失败时更新 `FAILED/failureReason` | `InterviewServiceImpl`、`InterviewReportAsyncService` | 立即查询为 `GENERATING`，后台完成后 DB 为 `COMPLETED/GENERATED` | 通过 |
| AI JSON 解析增强 | `parseQuestion` 清理 `scene/questionText` 标签；`parseEvaluate` 支持 Markdown JSON 和段落兜底；score clamp；非法 nextAction 自动决策 | `AiServiceImpl` | 编译通过，真实调用日志保留 raw/final，页面字段不透出原始标签串 | 通过 |
| 跨用户数据隔离 smoke | 使用用户 A 创建简历和面试，用户 B 枚举 A 的简历、面试、报告、消息 | 后端现有隔离逻辑 | 用户 B 请求均返回 `40000 Resume/Interview not found`，未泄露数据 | 通过 |

## 5. 接口契约变化

- `POST /interviews/{id}/answer`：兼容原响应，新增 `knowledgePoints`、`followUpQuestion`、`followUpReason`、`followUpValid`。`nextQuestion` 如果是追问，内容来自同一次 `evaluate` 响应；只有追问缺失或无效时才 fallback 调用 `/inner/ai/interview/follow-up`。
- `POST /interviews/{id}/finish`：现在快速返回，`status=REPORT_GENERATING`，`reportStatus=GENERATING`，`report.status=GENERATING`。
- `GET /interviews/{id}/report`：报告生成中返回稳定 VO，`status=GENERATING`；成功后返回 `GENERATED`；失败后返回 `FAILED` 和 `failureReason`。
- 前端建议：报告页在 `GENERATING` 时轮询 `GET /interviews/{id}/report`，轮询间隔建议 1-2 秒。
- 兼容性：路径不变，原字段保留；新增字段为向后兼容扩展。

## 6. AI Prompt 变化

- 评分 Prompt：从单纯评分改为“评分 + 点评 + nextAction + 可选追问”一次输出，强约束 JSON、nextAction 枚举、最大追问次数、追问相关性。
- 追问 Prompt：新增原始主问题、当前问题、参考答案、候选人回答、AI 点评、历史摘要，禁止“假设原问题”“请提供具体问题”等无效话术。
- 问题生成 Prompt：新增 `stageType`、`focusPoints`、`historySummary`，减少跨阶段随机跳题。
- 报告 Prompt：明确只输出 JSON，不要 Markdown、代码块或解释文字。
- 旧库刷新 SQL：执行 `sql/v1_ai_interview_experience_patch.sql`。

## 7. 测试结果

| 测试项 | 结果 |
|---|---|
| `mvn clean compile` | 通过，18 个 Maven 模块 SUCCESS |
| `mvn -q test` | 通过 |
| Prompt patch | `sql/v1_ai_interview_experience_patch.sql` 已在本地执行成功 |
| 后端服务重启 | 8080、9201-9207 均恢复监听，错误日志为空 |
| 提交回答耗时 | smoke 为 4356ms，真实 AI 正常一次调用；相比串行评分+追问减少一次 AI 往返 |
| EVALUATE 日志 | 提交一次回答后 `INTERVIEW_ANSWER_EVALUATE` 日志增量为 1 |
| 结束面试返回耗时 | 128ms，立即返回 `GENERATING` |
| 报告异步生成 | 后台完成后 session 为 `COMPLETED`，report 为 `GENERATED` |
| AI 日志接口 | 管理员查询 `/admin/ai/logs/page` 返回 `code=0` |
| 跨用户隔离 | 用户 B 枚举用户 A 简历/面试/报告/消息均未返回真实数据 |

## 8. 剩余风险

- 真实模型仍可能偶发不遵守 JSON；后端已兜底解析和过滤，但 Prompt 质量仍需要基于更多真实样本继续优化。
- 轻量异步没有 MQ 可靠性保障；服务重启时正在生成的报告任务可能丢失，V1 通过 retry 接口补偿。
- 前端报告页需要识别 `GENERATING` 并轮询，否则用户可能看到空报告状态。
- 当前 smoke 使用真实运行态 AI，但样本数量有限，仍建议前端联调时保留 AI 日志观察。

## 9. 是否建议进入前端适配

建议进入前端适配。

## 10. 是否建议进入 V2

不建议进入 V2。当前应先完成前端对 `GENERATING` 报告轮询和新增追问字段展示的适配，再做 V1 封版回归。
