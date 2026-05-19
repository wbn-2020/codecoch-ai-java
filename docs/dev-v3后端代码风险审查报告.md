# dev-v3 后端代码风险审查报告

审查日期：2026-05-19
仓库：`C:\my-claude\CodeCoachAI-java`
分支：`dev-v3`
审查对象：当前工作区后端代码，包括已跟踪修改和未跟踪新增文件。
审查基线：V3 PRD 与 V3-A 规划文档、`AGENTS.md` 后端规则、现有 V1/V2 稳定能力边界。
说明：当前工作区已有大量未提交改动，本报告只记录风险，不修改业务代码。

## 总体结论

当前后端代码不建议直接合并、推送或进入前后端联调。主要原因是根工程编译失败，多个 V3 用户侧/API 路径在实际 Gateway 配置中不可达，新引入的 `codecoachai-task` 和 `codecoachai-search` 会被 `/inner/**` 内部调用保护拦截，且搜索接口存在普通用户越权检索其他用户数据的风险。

从 V3 阶段看，当前实现已经超出 V3-B 的 Target Job / JD 基础范围，进入了任务中心、搜索、通知、AI 配额、系统日志等 V3-G 类工程增强。范围扩大本身不是错误，但现在缺少对应的路由、安全、迁移、编译和端到端验证收敛，导致风险叠加。

## 风险等级说明

- P0：阻断编译、部署、核心链路或基础安全边界，必须优先修复。
- P1：会造成数据错误、权限泄漏、功能假成功、迁移失败或后续维护困难，应在联调前修复。
- P2：阶段范围、配置一致性或维护性风险，不一定立即阻断，但会放大后续返工成本。

## P0 风险

### R-01 根工程编译失败

证据：

- `codecoachai-user/src/main/java/com/codecoachai/user/service/impl/UserServiceImpl.java:86` 调用 `setAvatar(String)`。
- `codecoachai-user/src/main/java/com/codecoachai/user/service/impl/UserServiceImpl.java:94` 调用 `setPhone(String)`。
- `codecoachai-user/src/main/java/com/codecoachai/user/domain/entity/SysUser.java` 当前实体字段为 `username`、`passwordHash`、`nickname`、`avatarUrl`、`email`、`status` 等，没有 `avatar` setter，也没有 `phone` 字段。
- `codecoachai-question/src/main/java/com/codecoachai/question/controller/QuestionStudyController.java:120`、`:162` 使用 `UserQuestionRecord::getIsCorrect` / `r.getIsCorrect()`。
- `codecoachai-question/src/main/java/com/codecoachai/question/domain/entity/UserQuestionRecord.java` 当前实体使用 `wrong` 字段，没有 `isCorrect`。
- `codecoachai-interview/src/main/java/com/codecoachai/interview/controller/InterviewReportExportController.java:114-116` 在 `Map<?, ?>` 上调用 `getOrDefault("name", "未知")` 和 `getOrDefault("score", 0)`，触发泛型捕获类型编译错误。

验证结果：

- `mvn clean compile` 失败，首先阻断在 `codecoachai-user`。
- `mvn -pl codecoachai-question -am compile` 失败。
- `mvn -pl codecoachai-interview -am compile` 失败。

影响：

- 当前仓库无法产出完整后端构建包。
- CI、部署、后端联调和回归验证都无法可信进行。

建议：

1. `UserServiceImpl` 与 `SysUser` 字段对齐。头像应优先使用既有 `avatarUrl`；如果确实需要手机号，需要补实体、DTO、SQL 迁移和脱敏/权限策略。
2. `QuestionStudyController` 与 `UserQuestionRecord` 语义对齐。若 `wrong=1` 表示错题，则不要引入不存在的 `isCorrect` getter。
3. `InterviewReportExportController` 对 `Map<?, ?>` 先取 `Object` 再转换，或定义强类型 DTO，避免泛型 `getOrDefault` 捕获错误。

### R-02 实际 Gateway 配置缺失多条 V3 路由

证据：

- `config/nacos/codecoachai-gateway-dev.yml:38` 的 resume 路由只有 `/resumes/**,/admin/resumes/**,/skill-profiles/**`，缺少 `/job-targets/**` 和 `/resume-job-match/**`。
- `config/nacos/codecoachai-gateway-dev.yml:32` 的 question 路由缺少 `/question-recommendations/**`。
- `config/nacos/codecoachai-gateway-dev.yml:44` 的 interview 路由只有 `/interviews/**,/admin/interviews/**,/study-plans/**`，缺少 `/study-tasks/**`、`/study-checkins/**` 等新增路径。
- `config/nacos/codecoachai-gateway-dev.yml:75` 的 search 路由只有 `/search/**`，缺少管理员搜索治理路径 `/admin/search/**`。
- `config/nacos/codecoachai-gateway-dev.yml:26` 的 user 路由只有 `/users/**,/admin/users/**`，新增的 `/admin/roles/**` 不可达。
- `docs/nacos/codecoachai-gateway-dev.yml` 已包含部分规划路由，例如 `/job-targets`、`/resume-job-match`、`/question-recommendations`，说明文档配置和实际配置已经分叉。

影响：

- V3-B/V3-D/V3-E 相关前端请求经过 Gateway 会直接 404 或路由不到目标服务。
- 管理端角色、搜索治理等接口可能只能直连服务访问，破坏统一网关鉴权模型。
- 如果只改文档配置而不改实际 Nacos 配置，联调环境仍然不可用。

建议：

1. 以 `config/nacos/codecoachai-gateway-dev.yml` 为实际运行配置源，补齐 V3 已实现接口路径。
2. 用户侧、管理员侧和 `/inner/**` 路径分开声明，不要把管理员路径挂在普通用户路由下。
3. 同步更新 `docs/nacos`，避免后续开发者导入错误配置。
4. 补充 Gateway 层路由 smoke test，至少覆盖 `/job-targets/current`、`/resume-job-match/latest`、`/question-recommendations/**`、`/study-checkins/**`、`/admin/roles/**`、`/admin/search/**`。

### R-03 新服务会被 `/inner/**` 内部调用白名单拦截

证据：

- `codecoachai-common/common-security/src/main/java/com/codecoachai/common/security/filter/InternalCallFilter.java:27` 的 `ALLOWED_SERVICES` 包含 gateway/auth/user/question/resume/file/interview/ai/system，但不包含 `codecoachai-task` 和 `codecoachai-search`。
- `codecoachai-task` 的消费者需要通过 Feign 调用 resume、interview、ai 等服务的 `/inner/**` 接口。
- `codecoachai-search` 通过 Feign 调用 resume、question、interview 的 `/inner/**` 数据接口同步 ES 文档。

影响：

- 异步任务消费、搜索索引同步等运行时链路会被内部调用过滤器拒绝，表现为 `FORBIDDEN`。
- 这些错误不一定在模块级编译中暴露，会在运行时集中爆发。

建议：

1. 明确 `codecoachai-task` 和 `codecoachai-search` 是否为可信内部服务。
2. 如果是可信服务，将其加入 `ALLOWED_SERVICES`，并保留 HMAC、timestamp、nonce、replay protection。
3. 增加内部调用集成测试，覆盖 task 到 resume/interview/ai、search 到 resume/question/interview。

## P1 风险

### R-04 搜索接口存在普通用户数据越权风险

证据：

- `codecoachai-search/src/main/java/com/codecoachai/search/controller/SearchController.java:53` 的 `/search/resumes` 没有按当前登录用户过滤，注释却描述为管理员检索简历。
- `codecoachai-search/src/main/java/com/codecoachai/search/controller/SearchController.java:62-69` 的 `/search/interviews` 接收可选 `userId` 请求参数；如果不传则搜索全部面试，如果传入其他用户 ID 则可查询其他用户数据。
- 当前实际 Gateway 只暴露 `/search/**`，没有将管理员搜索能力拆到 `/admin/search/**`。

影响：

- 普通用户可能检索到其他用户的简历、面试记录或报告索引内容。
- 即使 ES 文档只包含摘要字段，也会泄漏用户求职、简历、面试表现等敏感业务数据。
- 违反项目安全规则中“普通用户不能读取或修改另一个用户的数据”的要求。

建议：

1. 用户侧 `/search/**` 必须从登录态获取 `userId`，不要信任请求参数中的 `userId`。
2. 管理端搜索拆到 `/admin/search/**`，通过 Gateway 和服务层双重 ADMIN 权限控制。
3. ES 查询 DSL 默认追加用户隔离过滤条件，避免调用方遗漏。
4. 对索引文档做字段分级，用户侧不要返回管理员治理字段或原始全文。

### R-05 搜索同步失败时写入占位文档并标记成功

证据：

- `codecoachai-search/src/main/java/com/codecoachai/search/consumer/SearchSyncConsumer.java` 在 inner Feign 返回空数据时构造只有 `docId`、`indexName`、`syncedAt` 的 fallback document。
- 同一消费者对 Feign 异常返回 `null`，后续仍可能按空数据路径写入占位索引。
- 结合 R-03，`codecoachai-search` 当前大概率会被 `/inner/**` 白名单拒绝，进而写入大量无业务字段的 ES 文档。

影响：

- ES 索引会出现“同步成功但内容为空”的脏数据。
- 后续真实数据修复困难，因为同步状态和索引内容已经不一致。
- 搜索结果质量下降，用户看到空标题、空摘要或无法解释的记录。

建议：

1. 内部数据拉取失败时不要写入占位文档，应标记同步失败、重试或进入死信。
2. `DELETE` 事件和 `UPSERT` 事件分开处理，避免把缺失数据误判为可索引空对象。
3. 同步成功条件应包括目标数据存在、必需字段完整、ES 写入成功。

### R-06 异步题目生成存在“假成功”和数据丢失风险

证据：

- `codecoachai-task/src/main/java/com/codecoachai/task/consumer/QuestionGenerateConsumer.java:80` 仍保留 TODO：通过 question-service inner 接口批量写入 `question_draft` 表。
- `QuestionGenerateConsumer.java:84-90` 在未落库题目草稿的情况下调用 `asyncTaskService.markSuccess(...)` 并通知用户“已生成题目”。

影响：

- 用户和管理员会看到任务成功，但题目审核池或草稿表没有真实数据。
- AI 生成结果只存在于内存响应中，消费结束后丢失。
- 任务中心、通知中心和题库管理之间的数据一致性被破坏。

建议：

1. 在 question-service 增加受内部调用保护的批量草稿写入接口，或复用既有 AI 题目审核池写入能力。
2. 只有在题目草稿持久化成功后才能标记异步任务成功。
3. 对写入失败场景记录失败原因并进入重试或死信，不要发送成功通知。

### R-07 AI 路由、配额和调用日志能力未接入核心业务路径

证据：

- 新增的 `AiModelRouter`、`ProviderAiCaller`、`TokenAccountant`、`AiCallLogService` 已存在。
- `codecoachai-ai/src/main/java/com/codecoachai/ai/service/impl/AiServiceImpl.java` 仍大量直接调用 `aiClient.chat(...)`。
- V3 关键方法 `parseJobDescription`、`analyzeResumeJobMatch`、`analyzeSkillGapAnalyze` 等仍走直接调用路径。
- `AiCallLogService` 的注释说明“推荐新业务代码使用此类代替直接调用 AiModelRouter”，但当前业务 service 没有实际使用它。

影响：

- V3 新增的 provider fallback、route trace、token cost、quota 等能力无法覆盖核心 AI 链路。
- `ai_call_log` 增强字段可能长期为空，管理员侧成本治理和调用追踪失真。
- 以后再切换到路由服务时，历史调用口径和失败处理口径会不一致。

建议：

1. 先选定一个 V3 AI 场景作为接入样板，例如 `JOB_DESCRIPTION_PARSE`。
2. 通过 `AiCallLogService` 统一承接模型路由、调用、日志、成本估算和失败记录。
3. 保留 mock mode 与 real model mode 的清晰边界，避免测试环境误消耗真实额度。

### R-08 SQL 迁移存在幂等性和历史兼容风险

证据：

- `sql/migration/V3_012__question_group_relation_normalize.sql:7-10` 直接使用 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`。
- `sql/migration/V3_012__question_group_relation_normalize.sql:13-15` 使用 `CREATE INDEX IF NOT EXISTS`，该语法在常见 MySQL 8 版本中并不可靠，应沿用项目已有 helper procedure 模式。
- `sql/migration/V3_012__question_group_relation_normalize.sql:37` 和 `:57` 重新定义了 V2 已存在的 `question_relation`、`question_duplicate_review` 表。由于是 `CREATE TABLE IF NOT EXISTS`，如果 V2 表已存在，V3 期望的新列和索引不会自动补齐。
- `sql/migration/V3_008__operation_log_login_log.sql` 已创建 `operation_log`、`login_log`。
- `sql/migration/V3_009__notification_and_ai_quota.sql` 已创建 `notification`。
- `sql/migration/V3_011__add_login_log_operation_log_notification.sql` 再次创建 `operation_log`、`login_log`、`notification`，且字段定义与 V3_008/V3_009 不完全一致。

影响：

- 新库和旧库经过不同迁移路径后，表结构可能不一致。
- 重复执行迁移可能因索引/列语法兼容性失败。
- Java 实体、Mapper 和 SQL 真实表结构可能偏离，导致运行时报 unknown column、索引缺失或字段为空。

建议：

1. V3_012 改为项目已有的 `information_schema` / helper procedure 风格，分别判断列、索引、表是否存在。
2. 对 V2 已有表只做兼容增量，不用 `CREATE TABLE IF NOT EXISTS` 伪装结构升级。
3. 合并或修正 V3_008/V3_009/V3_011 的重复表定义，形成单一最终 schema。
4. 必须用同一数据库执行“首次迁移成功”和“重复执行成功”两轮验证，并核对关键表字段和索引。

### R-09 `docs/nacos` 中存在真实感较强的敏感配置

证据：

- `docs/nacos/codecoachai-user-dev.yml:9`、`codecoachai-ai-dev.yml:9`、`codecoachai-interview-dev.yml:9`、`codecoachai-question-dev.yml:9`、`codecoachai-resume-dev.yml:9`、`codecoachai-system-dev.yml:9` 中包含 `password: wbn123..`。
- `config/nacos` 中大多已经使用环境变量占位符，例如 `${MYSQL_PASSWORD:}`，比 `docs/nacos` 更安全。

影响：

- 即使是开发密码，也可能被复制到共享环境或生产配置。
- 与项目“不要提交真实密码和密钥”的规则冲突。

建议：

1. `docs/nacos` 中的密码改为 `${MYSQL_PASSWORD:}` 或明显的示例占位符。
2. 在配置说明中明确本地开发通过私有 Nacos 或环境变量注入真实值。
3. 检查 Git 历史是否已经暴露真实密码；如是，需要轮换对应凭据。

## P2 风险

### R-10 V3 实现范围跨度过大，阶段验收边界不清

证据：

- `AGENTS.md` 当前说明后端下一阶段是 V3-B：Target Job / JD backend foundation。
- 当前工作区同时出现 `codecoachai-task`、`codecoachai-search`、通知、系统公告、系统日志、题库导入、题目关系、学习打卡等 V3-G 或更后阶段内容。

影响：

- 一个分支中混入多个阶段目标，导致代码 review、SQL 验证、前端契约确认和回归测试范围急剧扩大。
- V3-B 的主线能力还未完全通过 Gateway 和编译验证，后续能力已经引入新的阻断点。

建议：

1. 先冻结当前范围，优先把 V3-B 到 V3-D 主链路编译、路由、安全和 SQL 迁移跑通。
2. 将 task/search/system-log/notification 等能力拆成单独验收清单，不要混在 Target Job/JD 的基础验收里。
3. 每个阶段完成后再推进下一阶段，避免未闭环的基础能力被新工程能力覆盖。

### R-11 文档 Nacos 配置与实际 Nacos 配置持续分叉

证据：

- `docs/nacos/codecoachai-gateway-dev.yml` 与 `config/nacos/codecoachai-gateway-dev.yml` 对同一路由集合的定义不一致。
- 文档配置中包含更多 V3 路由，但实际配置缺失这些路由。

影响：

- 开发者可能以为接口已可通过 Gateway 访问，但实际环境仍不可达。
- 排查问题时会在“代码已实现”和“网关不通”之间反复消耗时间。

建议：

1. 明确哪个目录是运行配置源，哪个目录是示例/说明。
2. 建立配置同步检查，例如对 Gateway path 列表做简单 diff。
3. 修改 Gateway 时同时更新运行配置、文档配置和验收清单。

## 已观察到的正向点

- `codecoachai-resume` 的 Target Job、Resume-JD Match、Skill Profile 相关 service 普遍使用当前登录用户 ID 做 ownership 校验，整体方向符合用户数据隔离要求。
- `mvn -pl codecoachai-task -am compile`、`mvn -pl codecoachai-search -am compile`、`mvn -pl codecoachai-resume -am compile`、`mvn -pl codecoachai-ai -am compile`、`mvn -pl codecoachai-file,codecoachai-system -am compile` 已通过模块级编译。
- 早期 V3_001 到 V3_005 迁移整体沿用了 helper procedure 的幂等风格，后续迁移可以按这个模式收敛。
- `config/nacos` 相比 `docs/nacos` 更接近安全配置实践，已经大量使用环境变量占位符。

## 验证记录

已执行的静态和编译验证：

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short --branch` | 通过 | 当前分支为 `dev-v3`，工作区有大量既有未提交改动。 |
| `mvn clean compile` | 失败 | `codecoachai-user` 中 `SysUser` 缺少 `setAvatar` / `setPhone`。 |
| `mvn -pl codecoachai-task -am compile` | 通过 | task 模块及依赖可单独编译。 |
| `mvn -pl codecoachai-search -am compile` | 通过 | search 模块及依赖可单独编译。 |
| `mvn -pl codecoachai-question -am compile` | 失败 | `UserQuestionRecord::getIsCorrect` 不存在。 |
| `mvn -pl codecoachai-resume -am compile` | 通过 | resume 模块及依赖可单独编译。 |
| `mvn -pl codecoachai-interview -am compile` | 失败 | `InterviewReportExportController` 的 `Map<?, ?>.getOrDefault` 泛型类型不兼容。 |
| `mvn -pl codecoachai-ai -am compile` | 通过 | ai 模块及依赖可单独编译。 |
| `mvn -pl codecoachai-file,codecoachai-system -am compile` | 通过 | file/system 模块及依赖可单独编译。 |

未执行的验证：

- 未执行数据库迁移实跑，因为本次任务是代码审查与文档生成，未获得本轮数据库连接信息。
- 未执行运行时集成测试，因此 Nacos 注册、Gateway 路由、Sa-Token 登录态、Redis、RocketMQ、Elasticsearch、OSS 等运行时行为仍需单独验证。
- 未执行浏览器或前端联调验证。

## 建议修复顺序

1. 修复 P0 编译失败：user、question、interview 三个模块先回到可编译状态。
2. 对齐实际 Gateway 路由：补齐 V3 用户侧和管理侧路径，确认 `/admin/**` 仍只允许管理员访问。
3. 修复内部调用白名单：确认 `codecoachai-task`、`codecoachai-search` 的服务身份和 HMAC 签名行为。
4. 收敛搜索权限：用户侧搜索强制按当前用户过滤，管理员搜索迁移到 `/admin/search/**`。
5. 修复异步题目生成落库：没有写入题目草稿或审核池前，不允许标记任务成功。
6. 修复搜索同步失败策略：内部数据拉取失败时进入失败/重试/死信，不写占位索引。
7. 重写高风险 SQL 迁移：尤其是 V3_012，以及 V3_008/V3_009/V3_011 的重复 schema。
8. 选择一个 V3 AI 场景接入 `AiCallLogService`，验证 route trace、token cost、失败记录和 mock/real 模式边界。
9. 清理 `docs/nacos` 中的敏感配置，并同步运行配置和文档配置。
10. 完成数据库双跑、Gateway smoke test、关键 V1/V2 回归后，再进入前后端联调。

## 下一步建议

建议先开一个“dev-v3 P0 收敛”小任务，只修复编译、Gateway 路由和内部调用白名单。这个任务完成后再处理搜索数据隔离、SQL 迁移和异步任务一致性。这样可以尽快恢复可构建、可部署、可联调的主干状态。
