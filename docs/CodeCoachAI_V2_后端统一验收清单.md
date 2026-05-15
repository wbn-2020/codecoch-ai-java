# CodeCoachAI V2 后端统一验收清单

本文档用于 V2 后端完成后的统一运行时验收。当前阶段不要求启动服务执行，本清单作为后续 E2E 测试输入。

## 1. 基础账号与认证

- 登录注册：用户可注册、登录并获取 token。
- 当前用户：携带 token 可获取当前用户信息。
- 用户资料：可查看和修改用户基础资料。
- 密码管理：密码修改后旧密码不可继续登录。

## 2. 题库与练习

- 题库浏览：`/questions` 可分页查询启用题目。
- 题目详情：题目详情包含题干、参考答案、解析、标签、分类等信息。
- 收藏：用户可收藏和取消收藏题目。
- 错题：提交错误或未掌握后可进入错题记录。
- 掌握状态：可更新题目掌握状态。
- 标签 / 分类筛选：按分类、标签、难度查询结果符合预期。

## 3. 简历与项目经历

- 简历创建：用户可创建简历。
- 简历更新：用户只能修改自己的简历。
- 默认简历：默认简历查询正常。
- 项目经历创建：可添加项目名称、背景、技术栈、职责、难点、优化结果。
- 项目经历更新 / 删除：仅允许本人操作。
- 内部简历接口：interview-service 可通过 `/inner/resumes/**` 获取简历和项目经历。

## 4. 项目深挖面试

- 创建项目深挖面试：`PROJECT_DEEP_DIVE` 模式可创建成功。
- 创建综合面试：`COMPREHENSIVE` 模式包含项目阶段。
- 开始面试：可进入首个阶段并生成第一题。
- 项目深挖问题：项目阶段问题包含项目背景、技术架构、数据库设计、核心难点、优化、故障、取舍或职责相关内容。
- 提交回答：回答保存到 `interview_message`。
- AI 评分：评分返回 score、comment、nextAction、knowledgePoints。
- AI 追问：低分或信息不足时可生成追问。
- 阶段推进：达到阶段问题数后进入下一阶段或完成。

## 5. 报告与学习反馈

- 生成报告：完成面试后生成 `interview_report`。
- 报告状态：生成中、成功、失败状态符合预期。
- 报告内容：包含 summary、stageScores、weakPoints、mainProblems、projectProblems、reviewSuggestions、recommendedQuestions、reportContent。
- 学习反馈：报告能说明主要短板、原因、复习方向、推荐练习题、下一轮训练建议。
- 推荐题目：报告中的推荐题目与薄弱点相关。
- 报告重试：失败报告可重试生成。

## 6. AI 能力

- mock 模式：不配置真实模型时面试问题、评分、追问、报告可稳定生成。
- real 模式：配置真实 provider 后可正常调用模型。
- fallback：AI 配置错误、超时、HTTP 错误、空响应或解析异常时有兜底结果。
- AI 调用日志：`ai_call_log` 记录 scene、model、request、response、latency、status、errorMessage 和 JSON 元数据。
- Prompt 模板：启用模板按 scene 生效。

## 7. 管理端权限

- `/admin/**` 普通用户禁止访问：普通用户访问管理接口返回 `FORBIDDEN`。
- `/admin/**` 管理员可访问：管理员访问 question、ai、system、user 管理接口成功。
- 直接访问业务服务端口时，缺少 `X-Roles: ADMIN` 的 `/admin/**` 请求被服务内过滤器拒绝。
- `OPTIONS` 和 `/health` 不被 admin 过滤器误拦截。

## 8. 内部接口安全

- `/inner/**` 缺签名拒绝。
- `/inner/**` 错误 signature 拒绝。
- `/inner/**` 过期 timestamp 拒绝。
- `/inner/**` nonce 重放拒绝。
- 正常 Feign 内部调用通过。
- Gateway 调 auth-service `/inner/auth/token-info` 通过。
- Redis 异常时 `/inner/**` fail-closed。

## 9. 前后端契约

- 前端 Network 无旧接口。
- 前端不直接访问 `/inner/**`。
- 前端不访问不存在的 public `/ai/**` 面试能力接口。
- 用户端接口仍使用 `/auth`、`/users`、`/questions`、`/resumes`、`/interviews`。
- 管理端接口仍使用 `/admin/**`。

## 10. 最小 E2E 建议顺序

1. 启动 Nacos、MySQL、Redis。
2. 启动 auth、user、question、resume、ai、interview、system、gateway。
3. 登录普通用户。
4. 创建简历和项目经历。
5. 创建项目深挖面试。
6. 开始面试。
7. 提交回答并触发 AI 评分。
8. 触发追问或下一题。
9. 完成面试并生成报告。
10. 查看学习反馈、推荐题目和 AI 调用日志。
11. 验证 `/admin/**` 普通用户拒绝、管理员通过。
12. 验证 `/inner/**` 缺签、错签、重放均拒绝。
