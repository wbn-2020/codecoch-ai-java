# V3 工程化能力后端与验收矩阵

> Agent C 负责范围：`codecoachai-task`、`codecoachai-file`、`codecoachai-search`、`codecoachai-common/common-redis`、`codecoachai-common/common-mq`。本矩阵仅按当前代码与配置静态核对，未覆盖 P0 文件范围。

## 总览

| 任务 | 能力域 | 当前完成度 | 本轮结论 |
| --- | --- | --- | --- |
| BE-13 | 异步任务中心、MQ 消费跟踪、死信治理 | 85% | 三类业务消费、任务表、死信表、管理 API 已具备；本轮补齐普通失败后释放 Redis 幂等 key，避免 MQ 重试被跳过。 |
| BE/FE-14 | 文件服务、OSS/STS、管理检索 | 80% | `ALIYUN_OSS`/`LOCAL` 双 provider、STS、内部上传下载、后台文件列表已具备；仍需前端直传回调/确认链路统一验收。 |
| BE-06 | 全文搜索、索引同步、索引运维 | 75% | 用户侧/管理侧搜索、三类 ES 索引、MQ 同步消费者已具备；fallback 配置存在但当前消费者主要依赖 MQ 重试/死信，未看到 `search_index_record` 兜底写入闭环。 |
| BE/FE-15 | 通知中心、任务结果触达 | 80% | 用户通知列表、未读数、已读、管理发送/广播，以及任务成功/失败通知已具备；广播用 `userId=0`，前端需明确是否拉取/展示。 |
| BE-11 | common-redis/common-mq 工程底座 | 85% | MQ topic/tag、统一 producer、Redis key、幂等 token、分布式锁 helper 已具备；仍需联调验证 RocketMQ、Redis、Nacos 运行时配置。 |

## 可执行验收矩阵

| 任务 | 场景 | key/topic/index/API | TTL/状态机 | 降级策略 | 验收命令/接口 | 当前完成度 | 下一步文件落点 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BE-13 | 简历解析异步消费：创建/消费 `resume.parse` 消息，拉取 rawText，调用 AI，回写解析结果。 | Topic `codecoachai-resume`, tag `parse`; consumerGroup `codecoachai-task-resume-parse`; table `async_task`; API `GET /admin/tasks`, `GET /admin/tasks/by-message-id/{messageId}`。 | Redis key `codecoachai:mq:consumed:{messageId}` TTL 7 天；状态 `RUNNING -> SUCCESS/FAILED/DEAD`；普通失败 `retry_count + 1` 后释放 key 允许 MQ 重投；非重试失败进入 `DEAD`。 | payload/上下文缺失为 `NonRetryableMqException`，写 `message_dead_letter`，回写业务失败状态并通知用户；AI/下游瞬时异常抛出交给 RocketMQ 重试。 | `mvn -pl codecoachai-task -am compile -DskipTests`; `GET /admin/tasks?bizType=resume.parse`; `POST /admin/tasks/dead-letters/{id}/recover`。 | 85% | 若要支持更多死信恢复类型，扩展 `codecoachai-task/src/main/java/com/codecoachai/task/controller/AdminTaskController.java#replayDeadLetter`。 |
| BE-13 | 题目批量生成异步消费：AI 生成草稿并写入题库审核池。 | Topic `codecoachai-question`, tag `ai-generate`; consumerGroup `codecoachai-task-question-generate`; API `GET /admin/tasks?bizType=question.generate`。 | 同上；RocketMQ listener `maxReconsumeTimes=6`，业务 `MAX_RETRY=3` 记录在 `async_task.max_retry`。 | AI 空结果/保存数量不一致按可重试失败处理；payload 缺失进入死信并通知。 | 触发上游出题请求后查 `GET /admin/tasks?keyword={batchId}`，再查题库审核池。 | 80% | 死信恢复当前仅支持 `resume.parse`，题目恢复落点同 `AdminTaskController#replayDeadLetter`。 |
| BE-13 | 面试报告异步生成：拉取面试上下文，调用 AI，回写 report。 | Topic `codecoachai-interview`, tag `report`; consumerGroup `codecoachai-task-interview-report`; API `GET /admin/tasks?bizType=interview.report`。 | 同上；成功写 `SUCCESS`，不可重试失败写 `DEAD` 并回写 `reportStatus=FAILED`。 | 上下文缺失为不可重试；AI/网络异常走 MQ 重试；通知用户成功/失败。 | 结束面试触发报告后，查 `GET /admin/tasks?keyword={sessionId}` 与业务报告详情。 | 80% | 支持面试报告死信恢复：`AdminTaskController#replayDeadLetter` 增加 `interview.report` 路由。 |
| BE/FE-14 | 前端获取 OSS 直传凭证。 | API `GET /files/sts-token?bizType=resume|avatar|attachment`; config `codecoachai.file.storage.provider=ALIYUN_OSS`; OSS key dir `resume/{userId}/`、`avatar/{userId}/`、`attachment/{userId}/`。 | STS TTL 由 common-oss 配置控制；当前接口要求登录态 userId。 | `StsTokenService` 不存在或 OSS 未启用时返回系统错误；未知 bizType 降级到 `tmp/{userId}/`。 | `curl -H "Authorization: Bearer <token>" "http://localhost:8080/files/sts-token?bizType=resume"`。 | 80% | 若要做上传完成确认/落库回调，落点 `codecoachai-file/src/main/java/com/codecoachai/file/controller/FileUserController.java` 或新增确认 API。 |
| BE/FE-14 | 内部文件上传/下载与后台文件审计。 | API `POST /inner/files/upload`, `GET /inner/files/{id}/download`, `GET /admin/files`, `GET /admin/files/{id}`; table `file_info`; provider `LOCAL`/`ALIYUN_OSS`。 | 文件状态来自 `file_info.status`；大小限制 `max-size-mb=50`；扩展名白名单 `pdf/doc/docx/md/txt/png/jpg/jpeg`。 | OSS provider 失败会抛业务异常；本地 provider 可作为开发降级；后台详情会尝试补充最新简历解析状态，失败只 warn。 | `mvn -pl codecoachai-file -am compile -DskipTests`; 上传接口用 multipart；后台查 `GET /admin/files?pageNo=1&pageSize=20`。 | 80% | 文件确认、预签名下载统一体验落点 `FileStorageService` 及两个 provider 实现。 |
| BE-06 | 用户侧全文搜索：题库公开搜索、当前用户简历/面试搜索。 | API `GET /search/questions`, `GET /search/resumes`, `GET /search/interviews`; indices `cc_question`, `cc_resume`, `cc_interview`。 | 无 Redis TTL；ES 分页 `pageNo/pageSize`；简历/面试强制 filter `userId=当前登录用户`。 | ES 查询异常直接向上抛出；暂无 DB fallback 查询。 | `curl "http://localhost:8080/search/questions?keyword=Java&pageNo=1&pageSize=10"`；登录后验 `resumes/interviews` 不可传 userId 越权。 | 75% | 如需 DB fallback，落点 `SearchController#doSearch` 或新增 search service 层。 |
| BE-06 | 管理侧搜索与索引重建。 | API `GET /admin/search/questions|resumes|interviews`; `POST /admin/search/indices/{indexName}/rebuild`; `POST /admin/search/indices/rebuild-all`; indices 同上。 | 管理搜索 pageSize 上限 100；重建会删除/重建索引，需重新触发同步。 | 只允许管理员；未知 index 由 `IndexManageService` 控制。 | `mvn -pl codecoachai-search -am compile -DskipTests`; `POST /admin/search/indices/rebuild-all` 后检查 ES mapping。 | 75% | 自动重放业务数据到 ES 的落点 `IndexManageService` 或各业务服务 search-doc producer。 |
| BE-06 | 搜索同步 MQ 消费：业务变更同步到 ES。 | Topic `codecoachai-search`, tags `question/resume/interview`; Redis key `codecoachai:search:consumed:{messageId}`; indices `cc_question/cc_resume/cc_interview`。 | Redis 幂等 TTL 3 天；`UPSERT` 拉取完整文档写 ES；`DELETE` 删除 ES 文档；失败会删除幂等 key 并抛出，允许 RocketMQ 重试。 | 拉取文档为空不写占位文档，直接重试/死信；配置 `fallback-to-record=true` 存在，但当前消费者未见落 `search_index_record` 的闭环。 | 构造 `SearchSyncPayload` 消息后查 ES：`GET cc_question/_doc/{id}`；异常场景观察 key 是否释放。 | 75% | `search_index_record` 兜底落点 `SearchSyncConsumer` 或新增同步记录 service。 |
| BE/FE-15 | 用户通知中心：列表、未读数、已读。 | API `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all`; table `notification`。 | `read_status: 0 -> 1`; `read_at` 标记已读时间；无 Redis TTL。 | 以登录态 userId 过滤，单条已读带 userId 条件防越权。 | 登录后调用列表和未读数；标记已读后再次查 `unread-count`。 | 85% | 若要支持广播合并展示，落点 `NotificationController#myNotifications`。 |
| BE/FE-15 | 管理通知发送/广播与任务结果通知。 | API `GET /admin/notifications`, `POST /admin/notifications/send`, `POST /admin/notifications/broadcast`, `DELETE /admin/notifications/{id}`; service `NotificationService.notifyTaskDone/notifyTaskFailed/notifySystem`。 | 系统通知写 `type=SYSTEM`；任务通知写 `TASK_DONE/TASK_FAILED`；广播当前写 `userId=0`。 | 通知写入失败仅 warn，不阻断主任务；管理接口要求 admin。 | `POST /admin/notifications/send` 指定 `userIds`; 异步任务成功/失败后查 `GET /notifications`。 | 80% | 广播是否展开到全体用户或按 `userId=0` 查询，需前后端约定，落点 `AdminNotificationController`/`NotificationController`。 |
| BE-11 | MQ 工程底座：统一 topic/tag 与 producer。 | `MqTopics`: `codecoachai-resume:parse`, `codecoachai-question:ai-generate`, `codecoachai-interview:report`, `codecoachai-search:*`, `codecoachai-notify:push`; `MqProducer.sendSync/sendAsync/sendDelay`。 | RocketMQ listener 各自配置 `maxReconsumeTimes`; producer 写 KEYS header 便于控制台查询。 | `MqProducer` 仅在 `RocketMQTemplate` 存在时装配；无 MQ 时相关 recover 会返回 MQ producer unavailable。 | `mvn -pl codecoachai-common/common-mq -am compile -DskipTests`; RocketMQ 控制台按 KEYS/messageId 查消息。 | 85% | 若要业务级延迟/重试策略统一配置，落点 `common-mq` 新增 properties。 |
| BE-11 | Redis 工程底座：通用 key、幂等 token、分布式锁。 | Keys `codecoachai:mq:consumed:{messageId}`, `codecoachai:idempotent:{token|bizKey}`, `codecoachai:lock:*`, `codecoachai:sts:token:*`。 | MQ 幂等 7 天；Search 幂等 3 天；幂等 token 默认 5 分钟；Redisson lock 由 watchdog 续期。 | Redis 不可用会导致依赖功能失败；当前没有本地内存降级，适合保持 fail-fast。 | `mvn -pl codecoachai-common/common-redis -am compile -DskipTests`; Redis CLI `TTL codecoachai:mq:consumed:{messageId}`。 | 85% | 若要统一 search 消费 key，落点 `RedisKeyConstants` 增加 search-specific builder。 |

## 本轮局部修复

- `AsyncTaskService.acquire`：当 MQ 重试同一 `messageId` 时，复用已有 `async_task` 记录并重新置为 `RUNNING`，避免唯一键冲突。
- `AsyncTaskService.markFailed`：普通失败后删除 `codecoachai:mq:consumed:{messageId}`，让 RocketMQ 下一次重投不会被 Redis 幂等层误判为重复消息。

## 残余风险

- `SearchSyncConsumer` 的 Nacos 配置存在 `fallback-to-record=true`，但当前代码未见写入/回放 `search_index_record` 的完整降级链路。
- `AdminTaskController#replayDeadLetter` 目前只支持 `resume.parse`，题目生成和面试报告的死信只能查看/忽略，不能一键恢复。
- 通知广播写 `userId=0`，用户侧列表当前只查当前 userId；是否展示广播需要前后端补充约定。
- 本矩阵未执行运行时联调，Redis、RocketMQ、Elasticsearch、OSS、Nacos 配置仍需在本地或联调环境验证。
