# CodeCoachAI V1 后端 P0/P1 收敛报告

生成时间：2026-05-14  
仓库：`C:\my-claude\CodeCoachAI-java`  
分支：`dev`

## 1. 仓库状态

### 当前分支

```text
dev
```

### 工作区状态

```text
 M codecoachai-ai/src/main/java/com/codecoachai/ai/service/impl/AiServiceImpl.java
 M codecoachai-common/common-security/src/main/java/com/codecoachai/common/security/util/SecurityAssert.java
 M codecoachai-interview/src/main/java/com/codecoachai/interview/convert/InterviewConvert.java
 M codecoachai-interview/src/main/java/com/codecoachai/interview/domain/vo/InterviewReportVO.java
 M codecoachai-question/src/main/java/com/codecoachai/question/controller/AdminQuestionMetadataController.java
 M codecoachai-question/src/main/java/com/codecoachai/question/service/impl/QuestionServiceImpl.java
 M codecoachai-system/src/main/java/com/codecoachai/system/controller/SystemConfigController.java
 M codecoachai-user/src/main/java/com/codecoachai/user/controller/AdminUserController.java
```

### 最近提交

```text
b76d741 V1完善
7829266 docs: update V1 startup documentation
d6245ce Fix V1 question tags and report fallback
16289f0 Fix V1 backend filters and demo test data
c01f9ca chore: add nacos config and e2e test data
4aebc99 fix: finalize v1 integration readiness
044e713 fix: harden v1 integration auth flow
72b7cd5 v1
```

## 2. 修改文件列表

### common-security

- `codecoachai-common/common-security/src/main/java/com/codecoachai/common/security/util/SecurityAssert.java`

### user

- `codecoachai-user/src/main/java/com/codecoachai/user/controller/AdminUserController.java`

### system

- `codecoachai-system/src/main/java/com/codecoachai/system/controller/SystemConfigController.java`

### question

- `codecoachai-question/src/main/java/com/codecoachai/question/controller/AdminQuestionMetadataController.java`
- `codecoachai-question/src/main/java/com/codecoachai/question/service/impl/QuestionServiceImpl.java`

### ai

- `codecoachai-ai/src/main/java/com/codecoachai/ai/service/impl/AiServiceImpl.java`

### interview

- `codecoachai-interview/src/main/java/com/codecoachai/interview/domain/vo/InterviewReportVO.java`
- `codecoachai-interview/src/main/java/com/codecoachai/interview/convert/InterviewConvert.java`

## 3. P0 修复结果表

| 编号 | 问题 | 是否修复 | 修改文件 | 核验证据 | 备注 |
|---|---|---|---|---|---|
| P0-01 | `/admin/**` 权限遗漏 | 已修复 | `SystemConfigController`, `AdminUserController`, `AdminQuestionMetadataController`, `SecurityAssert` | 扫描所有 `/admin/**` Controller 后，方法入口均有 `SecurityAssert.requireAdmin()`；`mvn clean compile` 通过 | 未登录先返回 `UNAUTHORIZED`，非管理员返回 `FORBIDDEN` |
| P0-02 | 普通用户数据隔离 | 已核验 | `ResumeServiceImpl`, `InterviewServiceImpl`, `QuestionServiceImpl` | 简历使用 `getOwnedResume`，面试使用 `getOwnedSession`，题库答题/收藏/错题使用 `requireCurrentUserId()` | 本轮未改业务模型，仅核验已有隔离逻辑 |
| P0-03 | `/inner/**` 内部接口保护 | 已核验 | `AuthGatewayFilter`, `InternalCallFilter`, `OpenFeignConfig` | Gateway 直接拒绝 `/inner/**`；服务内校验 `X-Internal-Call=true` 和服务名；Feign 自动加内部调用头 | 采用 Gateway + 服务内 Header 双层保护 |
| P0-04 | AI 真实调用链 | 已修复 | `AiServiceImpl` | `mock-enabled=false` 时调用 `AiClient.chat`；mock 分支仅在配置开启时执行；编译通过 | mock 日志的 `modelName` 标记为 `模型名(mock)` |
| P0-05 | Prompt 模板读取和变量渲染 | 已修复 | `AiServiceImpl` | AI 调用优先读取启用 DB Prompt，并支持 `targetPosition/experienceLevel/industry/stageName/questionContent/referenceAnswer/userAnswer/resumeContent/projectExperience/historySummary` 等变量 | DB 无模板时使用代码兜底模板 |
| P0-06 | AI 调用日志 | 已修复 | `AiServiceImpl` | 成功/失败均写 `ai_call_log`，包含 `userId/scene/modelName/promptTemplateId/requestPrompt/responseContent/businessId/elapsedMs/status/errorMessage` | AI 原始响应解析失败时会保存 raw response |
| P0-07 | 面试主链路状态机 | 已核验 | `InterviewServiceImpl` | 创建、开始、当前问题、提交答案、追问、阶段流转、结束报告均已存在并编译通过 | 真实浏览器链路仍需前端联调验证 |
| P0-08 | 字段契约 | 已修复 | `InterviewReportVO`, `InterviewConvert`, `AiServiceImpl` | 当前问题主字段为 `questionContent`；题目答案主字段为 `referenceAnswer`；报告 VO 输出数组/对象字段 | `questionText` 仍保留兼容 |
| P0-09 | SQL | 已核验 | `sql/init.sql`, `sql/v1_backend_convergence_patch.sql`, `sql/dev_test_data.sql` | SQL 已包含核心表和 V1 Prompt；补丁 SQL 存在 | 现有数据库仍需实际执行 patch |
| P0-10 | Maven 编译测试 | 已通过 | 全仓库 | `mvn clean compile` 成功；`mvn -q test` 成功 | 当前测试覆盖以编译级验证为主 |

## 4. P1 修复结果表

| 编号 | 问题 | 是否修复 | 修改文件 | 核验证据 | 备注 |
|---|---|---|---|---|---|
| P1-01 | 问题组去重 fallback | 已修复 | `QuestionServiceImpl` | fallback 查询继续携带 `excludeGroupIds`，避免条件放宽后绕过同组排重 | 若仍无可用题，由 interview-service 走 AI 生成问题，`questionId` 可为空 |
| P1-02 | 报告结构稳定性 | 已修复 | `InterviewReportVO`, `InterviewConvert`, `AiServiceImpl` | `stageScores/stageReports` 返回对象，`weakPoints/strengths/mainProblems/projectProblems/reviewSuggestions/questionReviews` 返回非 null 数组 | JSON 解析失败返回空对象/空数组，不让前端白屏 |
| P1-03 | 系统配置最大追问次数 | P2 预留 | `SystemConfigController` | `/admin/configs` 已补管理员权限 | V1 主链路仍使用阶段 `maxFollowUpCount`，系统配置暂不接入流程 |

## 5. 命令执行结果

### `mvn clean compile`

```text
Reactor Summary for CodeCoachAI Java 1.0.0-SNAPSHOT:
CodeCoachAI Java ................................... SUCCESS
common-core ........................................ SUCCESS
common-web ......................................... SUCCESS
common-security .................................... SUCCESS
common-mybatis ..................................... SUCCESS
common-redis ....................................... SUCCESS
common-feign ....................................... SUCCESS
common-log ......................................... SUCCESS
common-ai .......................................... SUCCESS
codecoachai-common ................................. SUCCESS
codecoachai-gateway ................................ SUCCESS
codecoachai-auth ................................... SUCCESS
codecoachai-user ................................... SUCCESS
codecoachai-question ............................... SUCCESS
codecoachai-resume ................................. SUCCESS
codecoachai-interview .............................. SUCCESS
codecoachai-ai ..................................... SUCCESS
codecoachai-system ................................ SUCCESS
BUILD SUCCESS
```

### `mvn -q test`

```text
BUILD SUCCESS
```

说明：当前仓库测试覆盖较少，`mvn -q test` 主要证明测试编译和模块构建无失败。

## 6. 后端剩余风险

1. 现有 MySQL 库需要执行 `sql/v1_backend_convergence_patch.sql`，否则旧库可能缺少 V1 新字段。
2. 真实 AI 调用需要配置 `codecoachai.ai.base-url`、`codecoachai.ai.api-key`、`codecoachai.ai.model`，并确保 `mock-enabled=false`。
3. 本轮未启动服务做 HTTP smoke test，管理员 401/403、内部接口 403、AI 日志落库仍建议在启动后用 Gateway 验证。
4. `sql/dev_test_data.sql` 中仍有部分历史演示数据文本编码显示不佳，但不含真实 API Key；V1 主结构以 `init.sql` 和 patch SQL 为准。

## 7. 给前端的联调说明

- 当前问题字段：主用 `questionContent`；`questionText` 仅兼容旧字段。
- 题目参考答案字段：主用 `referenceAnswer`。
- 报告 VO 字段：`id`, `sessionId`, `totalScore`, `summary`, `stageReports`, `stageScores`, `weakPoints`, `strengths`, `mainProblems`, `projectProblems`, `reviewSuggestions`, `questionReviews`, `createdAt`, `generatedAt`, `reportContent`。
- 管理端接口：所有 `/admin/**` 必须携带管理员 token；普通用户会返回无权限。
- `/inner/**` 不允许前端调用，外部请求会被 Gateway 或服务内过滤器拒绝。
- AI 日志查询接口：`GET /admin/ai/logs/page`, `GET /admin/ai/logs/{id}`。
- Prompt 管理接口：`GET /admin/ai/prompts/page`, `GET /admin/ai/prompts/{id}`, `POST /admin/ai/prompts`, `PUT /admin/ai/prompts/{id}`, `DELETE /admin/ai/prompts/{id}`, `PUT /admin/ai/prompts/{id}/status`。

## 8. 是否建议进入前端联调

建议进入前端联调。

理由：后端 P0/P1 代码侧已完成收敛，`mvn clean compile` 和 `mvn -q test` 均通过。下一步应启动服务、执行 SQL patch，并用浏览器验证 V1 主链路。

## 9. 是否建议进入 V2

不建议进入 V2。

原因：V1 仍缺真实浏览器 E2E 证据、真实 AI 配置链路验证、数据库 patch 实际执行确认和前端字段联调确认。

## 10. Git diff 摘要

```text
8 files changed, 170 insertions(+), 66 deletions(-)
```

建议提交信息：

```text
fix: close v1 backend p0 p1 gaps
```
