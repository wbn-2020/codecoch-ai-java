# CodeCoachAI P2 接口与前端契约加固规格

## 目标

修复本轮可独立落地且风险可控的 P2 问题，同时保持现有业务码兼容：

1. 资源不存在和权限不足使用正确 HTTP 404/403。
2. 面试比较对历史报告缺字段时提供稳定、可解释的比较契约。
3. 前端保留成功响应顶层 `traceId` 的访问能力。
4. 补齐 readiness snapshot ID 查询 API。
5. 明确 multipart 内存风险的本轮处理边界。

## HTTP 语义

- `ErrorCode.RESOURCE_NOT_FOUND` 映射 HTTP 404。
- `ErrorCode.FORBIDDEN` 映射 HTTP 403。
- `ErrorCode.RESOURCE_RELATION_CONFLICT`、stale/idempotency 冲突映射 HTTP 409。
- 响应体继续保留原业务 `code/message/traceId/data`。
- 服务层对“明确不存在”使用 `RESOURCE_NOT_FOUND`。
- 所有权不匹配且不能安全暴露资源存在性时，可统一使用 `RESOURCE_NOT_FOUND`；管理员权限拒绝继续使用 `FORBIDDEN`。
- 不全仓机械替换所有 `PARAM_ERROR`，仅修复验收涉及的简历、readiness、面试比较资源入口。

## 面试比较契约

- 提取共享的“报告可比较性”校验，面试详情预检和比较创建使用同一规则。
- 可比较报告必须具备有效 `totalScore`、稳定 rubric identity、非空且可解析的 rubric dimensions。
- 历史报告缺少 rubric/dimensions 时不伪造分数，不把总分包装成维度趋势。
- 创建前明确返回 `RUBRIC_DATA_MISSING`、`TOTAL_SCORE_MISSING`、`RUBRIC_VERSION_MISMATCH` 等原因。
- 已存在的不可比较记录继续返回 `comparable=false`、`unavailableReasons` 和每轮基础摘要。
- 前端在选择阶段禁用不可比较报告，并展示“需要重新生成报告”等可执行提示。
- 只有 rubric identity 一致且维度数据完整时才返回总分和维度差异。

## traceId

- common-web 使用 `ResponseBodyAdvice` 为所有 `Result` 成功响应补充当前 traceId。
- 保持 Axios 默认返回业务 `data`，避免破坏所有调用方。
- 前端提供 opt-in 的 `requestWithMeta`/`preserveEnvelope` 能力，只在需要诊断信息的 API 使用。
- Gateway 暴露 `X-Trace-Id` 响应头，作为 envelope 外的诊断通道。
- 不把 `traceId` 混入任意业务对象，避免数组、原始值和 DTO 类型污染。

## Readiness Snapshot

- 新增 `getJobReadinessSnapshotApi(targetJobId, snapshotId)`。
- 参数必须为正整数。
- URL 与后端现有 `/job-targets/{targetJobId}/readiness-snapshots/{snapshotId}` 对齐。
- 增加 API 单测，覆盖 URL、方法和返回类型。

## Multipart

- 本轮保留 10 MiB 文件上限，并在创建 multipart 对象和调用 Feign 前执行文件尺寸检查。
- 为导出上传增加有界并发许可；队列满时返回可控业务错误，不继续分配大字节数组。
- 增加上传阶段耗时和失败日志，日志不包含简历原文。
- 补充并发许可释放和超限测试。
- 不在本轮仓促替换 Feign multipart 编码器；预签名或专用流式客户端作为独立性能任务。

## 测试

- `GlobalExceptionHandlerTest` 覆盖枚举和整数业务码映射。
- MockMvc 覆盖资源不存在 404、管理员拒绝 403 和冲突 409。
- Resume/Interview 服务测试覆盖资源不存在和所有权隔离。
- 面试比较测试覆盖完整、缺总分、缺维度、畸形维度和 rubric 不一致。
- 前端 request 元数据测试覆盖成功 traceId。
- readiness API 测试覆盖 snapshot ID URL。

## 非目标

- 不改变业务码编号。
- 不把所有历史 `PARAM_ERROR` 一次性重分类。
- 不伪造历史报告维度。
- 不在本轮实现全链路文件流式上传。
