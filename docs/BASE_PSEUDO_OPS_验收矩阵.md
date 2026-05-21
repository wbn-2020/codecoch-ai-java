# BASE-01 / PSEUDO-01 / OPS-01 验收矩阵

> 负责人范围：Agent E - 回归、伪成功治理、部署验收
> 文档日期：2026-05-20
> 适用仓库：`CodeCoachAI-java`、`CodeCoachAI-vue`、`CodeCoachAI-doc`
> 约束：本矩阵只定义验收门禁与风险落点，不要求本轮启动 `docker compose up`。

## 1. BASE-01：V1 / V2 回归门禁清单

### 1.1 基础构建门禁

| 范围 | 命令 | 预期 | 阻塞条件 |
|---|---|---|---|
| 后端全量编译 | `cd CodeCoachAI-java && mvn clean compile` | 所有模块编译通过，无测试编译依赖缺失 | 任一服务编译失败；公共 DTO / Feign 契约不兼容 |
| 前端类型检查 | `cd CodeCoachAI-vue && npx vue-tsc -b` | TypeScript 类型检查通过 | API 类型、路由参数或组件 props 不兼容 |
| 前端构建 | `cd CodeCoachAI-vue && npm run build` | 生产构建通过，产物生成 | 路由懒加载、Element Plus / lucide 引用、环境变量导致构建失败 |
| 后端差异检查 | `cd CodeCoachAI-java && git diff --check` | 无尾随空格和冲突标记 | 出现 whitespace error 或 `<<<<<<<` |
| 前端差异检查 | `cd CodeCoachAI-vue && git diff --check` | 无尾随空格和冲突标记 | 出现 whitespace error 或 `<<<<<<<` |
| 文档差异检查 | `cd CodeCoachAI-doc && git diff --check` | 文档无冲突标记 | 出现编码异常新增、冲突标记或非法换行 |

### 1.2 V1 核心闭环回归

| 链路 | 命令 / 接口 | 页面 | 预期 | 验收证据 |
|---|---|---|---|---|
| 注册 / 登录 | `POST /auth/register`、`POST /auth/login`、`GET /auth/current-user` | `/login`、`/register`、用户工作台 | 登录返回 token；当前用户返回真实用户信息；失败时返回明确错误码 | Network 响应截图或接口响应日志 |
| 用户资料 | `GET /users/profile`、`PUT /users/profile`、`PUT /users/password` | `/profile` | 资料可查询和更新；旧密码修改后不可继续登录 | 接口响应 + 再登录结果 |
| 题库浏览 | `GET /questions`、`GET /questions/{id}` | `/questions`、题目详情 | 分页、分类、标签、难度筛选返回数据库真实题目 | 列表页截图 + Network |
| 刷题记录 | `POST /questions/{id}/answers`、`POST /questions/{id}/favorite`、`GET /questions/wrong-records` | 题目详情、错题页、收藏页 | 答题记录、收藏、错题状态持久化，刷新后仍存在 | 数据库记录或接口二次查询 |
| 简历 CRUD | `POST /resumes`、`GET /resumes`、`PUT /resumes/{id}`、`POST /resumes/{id}/projects` | `/resumes`、简历编辑页 | 简历和项目经历按当前登录用户隔离 | 页面刷新后数据仍一致 |
| 面试创建 | `POST /interviews` | `/interviews/create` | 可创建 V1 模拟面试，返回 session id | 接口响应含 `id` / `status` |
| 面试开始 | `POST /interviews/{id}/start`、`GET /interviews/{id}/current` | `/interviews/{id}/room` | 第一题来自真实接口；若题库无匹配只能返回可识别 fallback 标记，不可冒充题库题 | 当前题 Network 响应 |
| 面试作答 | `POST /interviews/{id}/answer`、`POST /interviews/{id}/answer/stream` | 面试房间 | 回答持久化，AI 点评或同步 fallback 返回评分、点评、下一步动作 | `interview_message` / 页面评分 |
| 面试结束与报告 | `POST /interviews/{id}/finish`、`GET /interviews/{id}/report` | `/interviews/{id}/report` | 报告状态从生成中进入成功或失败；失败可重试 | 报告页轮询记录 |
| 管理权限 | `/admin/**` | `/admin` | 普通用户访问被拒绝；管理员访问成功 | 403/成功响应对照 |
| 内部接口保护 | `/inner/**` | 无公开页面 | 缺签名、错签名、过期 timestamp、重放 nonce 均拒绝 | 接口测试记录 |

### 1.3 V2 增强能力回归

| 链路 | 命令 / 接口 | 页面 | 预期 | 验收证据 |
|---|---|---|---|---|
| 简历上传解析 | `POST /resumes/upload`、`GET /resumes/{id}/parse-status`、`GET /resumes/{id}/analysis-result` | `/resumes` | 文件上传成功；解析状态真实流转；失败展示失败原因，不返回假解析结果 | 上传记录 + 状态轮询 |
| 简历优化 | `POST /resumes/{id}/optimize`、`GET /ai/sse/resume-optimize` | 简历编辑页 / 简历列表 | SSE 成功时展示流式结果；SSE 未启动才允许同步接口 fallback；结果生成优化记录 | 优化记录详情 |
| 行业模板面试 | `GET /industry-templates`、`POST /interviews` | 面试创建页 | 行业模板来自接口；创建面试后大纲匹配所选模板 | 创建请求与面试详情 |
| AI 题目生成与审核 | `POST /admin/ai/questions/generate`、`GET /admin/question-reviews` | `/admin/questions` | 生成进入审核流；不得直接把 AI 草稿冒充已发布题目 | 审核列表和题库列表对照 |
| 题库去重 | `/admin/question-duplicate-reviews/**` | 题库管理 | 重复候选可查询、审核、处理 | 管理页记录 |
| 学习计划 | `POST /study-plans/generate`、`GET /study-plans`、`POST /study-tasks/{taskId}/complete` | `/study/plans`、每日任务页 | 计划和任务真实持久化；任务状态 TODO/DOING/DONE/COMPLETED/SKIPPED 兼容 | 页面刷新和接口查询 |
| 简答题 AI 点评 | `POST /practice/{questionId}/submit` 或相关练习接口 | 练习页 | AI 点评失败时标记失败或展示错误，不生成伪评分 | 练习记录详情 |
| Prompt 管理 | `/admin/ai/prompt-templates/**`、`/admin/ai/logs` | `/admin/ai/prompts` | 版本创建、激活、测试、日志查询可用；mockMode 字段真实暴露 | Prompt 测试响应和日志 |
| AI 调用日志 | `GET /admin/ai/call-logs`、`GET /admin/ai/logs/page` | AI 日志页 | 记录 scene、model、success/status、traceId、businessId、错误信息 | 日志详情 |
| 文件与知识库 | `/files/sts-token`、`/knowledge/documents`、`/knowledge/ask` | 文件 / 知识库页面 | OSS 未配置时明确失败；不可返回假上传成功 | 上传失败或成功证据 |

## 2. PSEUDO-01：mock / fallback / 伪成功治理矩阵

治理原则：

1. `mock` 只能用于明确配置或管理端 Prompt 测试，不得作为生产成功结果。
2. `fallback` 必须可观测：响应字段、状态、日志或页面提示至少一处可识别。
3. AI、文件、MQ、搜索等外部依赖失败时，不允许返回业务成功但不落库、不入队、不产生日志的伪成功。
4. 前端只允许 UI 空态或同步接口 fallback，不允许本地构造业务结果替代后端。

| 类型 | 代码落点 | 当前行为 / 风险 | 验收要求 | 优先级 |
|---|---|---|---|---|
| AI mock 配置 | `CodeCoachAI-java/config/nacos/codecoachai-common-dev.yml`：`codecoachai.ai.mock-enabled=false`；`CodeCoachAI-java/docs/nacos/codecoachai-ai-dev.yml` | dev 配置已显式关闭 mock；若环境覆盖为 true，Prompt 测试和 AI 调用可能进入 mock | 发布验收前记录最终 Nacos 配置；`mock-enabled` 必须为 false；页面/日志可看到 mockMode=false | P0 |
| Prompt 测试 mock | `codecoachai-ai/.../PromptTemplateServiceImpl.java`：`testAiResponse`、`savePromptTestLog` | mock 开启时返回 `PROMPT_VERSION_TEST_MOCK_RESPONSE`，日志仍 success=YES | 仅允许在管理端测试场景；日志 `modelName` 必须带 `(mock)`；不得用于面试、简历、岗位匹配等用户链路 | P0 |
| Prompt 日志伪成功 | `PromptTemplateServiceImpl.savePromptTestLog` | `elapsedMs/costMillis=0` 且插入异常被忽略；可能导致测试成功但日志缺失 | Prompt 测试验收必须同时检查 `aiCallLogId` 非空或页面提示日志缺失；后续建议把日志写入失败暴露为告警 | P1 |
| 题库选题 fallback | `codecoachai-question/.../QuestionServiceImpl.java`：`selectForInterview` | 题库无匹配时返回 `AI_GENERATED_FALLBACK`，未落题库 | 面试房间或日志必须能识别 fallback；不得把该题统计为真实题库命中；验收需准备空题库/无匹配用例 | P0 |
| SSE 到同步 fallback | `CodeCoachAI-vue/src/views/interview/InterviewRoomView.vue`：`submitAnswerFallback` | 仅在 SSE 未启动时回退同步答题；SSE 已启动后失败只提示刷新 | 合理；验收需覆盖 SSE 连接失败时同步接口成功，和 SSE 中途失败时不重复提交 | P1 |
| 报告 SSE fallback | `CodeCoachAI-vue/src/views/interview/InterviewReportView.vue`：`runSyncFallback` | 报告流式生成失败可能回退同步接口 | fallback 成功后报告必须来自后端持久化结果；失败应展示错误状态，不展示假报告 | P1 |
| 简历优化 SSE fallback | `CodeCoachAI-vue/src/views/resume/ResumeListView.vue`、`ResumeEditView.vue`：`runSyncOptimizeFallback` | SSE 启动失败时回退同步优化 | 同步 fallback 结果必须产生优化记录；页面需显示同步 fallback 状态 | P1 |
| AI 题目生成 SSE fallback | `CodeCoachAI-vue/src/views/admin/QuestionManageView.vue`：`runSyncGenerateFallback` | SSE 失败时同步生成 | 生成结果必须进入审核流；不能直接出现在用户题库 | P0 |
| 搜索 fallback | `CodeCoachAI-java/config/nacos/codecoachai-search-dev.yml`：`fallback-to-record=true` | ES 异常时可能回退 `search_index_record` 表 | 搜索页需展示降级来源或运维日志；验收时区分 ES 结果与兜底表结果 | P1 |
| Dashboard TODO 状态 | `codecoachai-user/.../UserServiceImpl.java`：`entryStatus(... "TODO" ...)` | TODO 是业务状态，不是 mock；但可能被误判为完成 | 工作台入口状态必须区分 TODO/AVAILABLE/CONTINUE；TODO 不计入完成率 | P2 |
| 前端空态防伪成功 | `DashboardView.vue`、`AdminDashboardView.vue`、`ResumeListView.vue`、`V3FoundationShell.vue` 等 | 多处文案声明接口异常不回退假数据 | 验收时断网/服务异常页面应为空态或错误提示，不能显示示例业务结果 | P0 |
| 文件 STS / OSS | `codecoachai-file/.../FileUserController.java`、`common-dev.yml` 的 OSS 配置 | OSS 凭证缺失时应失败；配置文件默认 enabled=true 存在误用风险 | 验收前确认 OSS enabled 与凭证来源；缺凭证不得返回上传成功 | P0 |
| MQ 任务伪成功 | `codecoachai-task` 管理接口、RocketMQ 配置 | 入队失败若被吞掉会造成任务中心无记录但页面成功 | 触发异步任务后必须在 `/admin/tasks` 查到任务或失败记录；无记录即阻塞 | P0 |

## 3. OPS-01：Docker Compose / README 部署验收

### 3.1 允许执行的非启动检查

本轮不执行 `docker compose up`。可执行以下只读或静态校验：

| 检查项 | 命令 | 预期 |
|---|---|---|
| Compose 文件语法 | `cd CodeCoachAI-java && docker compose config` | 能展开服务配置，无 YAML 语法错误；不启动容器 |
| Compose 服务清单 | `cd CodeCoachAI-java && docker compose config --services` | 至少包含 `mysql`、`redis`、`nacos`、`rocketmq-namesrv`、`rocketmq-broker`、`rocketmq-dashboard`、`elasticsearch` |
| 环境变量模板 | `cd CodeCoachAI-java && Get-Content .env.example` | 只含变量名或示例值，不含真实密钥 |
| README 部署说明 | `cd CodeCoachAI-java && Select-String -Path README.md -Pattern "Docker|Nacos|RocketMQ|Elasticsearch|OSS|AI"` | README 能指向基础依赖、Nacos 导入、服务启动和关键接口 |

### 3.2 实际部署验收步骤

| 步骤 | 命令 / 操作 | 预期 | 阻塞点 |
|---|---|---|---|
| 1. 准备环境变量 | 复制 `.env.example` 为本机私有 env，填入 `MYSQL_PASSWORD`、`DEEPSEEK_API_KEY`、`DASHSCOPE_API_KEY`、OSS 变量等 | 密钥只在本机私有文件或系统环境中存在 | `.env.local` 存在真实凭证形态值，发布前必须轮换并从提交范围排除 |
| 2. 静态校验 Compose | `docker compose config` | 配置能被 Docker Compose 解析 | Docker 未安装、Compose 版本过低、变量展开失败 |
| 3. 启动基础设施 | `docker compose up -d mysql redis nacos rocketmq-namesrv rocketmq-broker elasticsearch` | 容器启动，健康检查最终通过 | 本轮不执行；实际验收需记录容器状态 |
| 4. 检查基础端口 | MySQL `3306`、Redis `6379`、Nacos `8848/9848`、RocketMQ `9876/10911/10909`、ES `9200/9300` | 端口可访问且无本机冲突 | 本机已有服务占用端口 |
| 5. 导入 Nacos 配置 | `powershell -ExecutionPolicy Bypass -File scripts\nacos\import-nacos-config.ps1` | `codecoachai-*-dev.yml` 已进入 Nacos | Nacos 未就绪；配置中的密码/API Key 为空 |
| 6. 导入数据库 | `sql/init.sql` + `sql/migration` 按顺序执行，或使用现有 migration 流程 | 表结构完整，重复执行策略明确 | Compose 当前把 `./sql/migration` 挂到 MySQL 初始化目录，若脚本非全量幂等可能只适合空库 |
| 7. 启动后端服务 | 按需执行 `mvn -pl <module> spring-boot:run`，至少 gateway/auth/user/question/resume/interview/ai/system | 服务注册到 Nacos；`/health` 可访问 | Nacos 数据源和本地库名不一致；AI/OSS/MQ 凭证缺失 |
| 8. 启动前端 | `cd CodeCoachAI-vue && npm run dev` | 页面可通过网关访问接口 | `.env.development` 的 API baseURL 与 gateway 端口不一致 |
| 9. 冒烟接口 | `GET /health`、`POST /auth/login`、`GET /questions`、`GET /admin/ai/logs/page` | 核心接口返回真实状态 | 认证、路由、跨域、网关转发失败 |
| 10. 业务闭环 | 执行 V1/V2 回归门禁中的最小 E2E | 数据可持久化；AI 失败可观测 | mock/fallback 未标识或伪成功 |

### 3.3 环境变量清单

| 变量 | 用途 | 来源建议 | 验收要求 |
|---|---|---|---|
| `MYSQL_PASSWORD` | MySQL root 密码和应用数据源密码 | 本机私有 env / CI secret | 不得提交真实值 |
| `NACOS_SERVER_ADDR` | 后端服务发现与配置中心 | `.env.example` 默认 `127.0.0.1:8848` | 与实际 Nacos 地址一致 |
| `ROCKETMQ_NAME_SERVER` | RocketMQ NameServer 地址 | `.env.example` 默认 `127.0.0.1:9876` | 与 Compose 暴露端口一致 |
| `AI_API_KEY` | 兼容旧配置的 AI Key | CI secret / 本机私有 env | 若使用 `DEEPSEEK_API_KEY` 可为空，但不能写真实值 |
| `DEEPSEEK_API_KEY` | DeepSeek provider Key | CI secret / 本机私有 env | V1/V2 AI 链路真实验收必填 |
| `DASHSCOPE_API_KEY` | 通义 fallback provider Key | CI secret / 本机私有 env | 若开启 provider fallback 必填 |
| `AI_BASE_URL` | OpenAI compatible base URL | 示例值可提交 | 必须和 provider 匹配 |
| `AI_MODEL` | 默认模型 | 示例值可提交 | Prompt 测试与日志中应一致 |
| `OSS_ENDPOINT` | OSS endpoint | 示例值可提交 | 与 bucket 区域一致 |
| `OSS_BUCKET` | OSS bucket | 本机私有 env / CI secret | 真实 bucket 名不建议提交 |
| `OSS_AK` / `OSS_SK` | OSS 访问密钥 | CI secret / 本机私有 env | 不得提交真实值；发现后需轮换 |
| `OSS_STS_ROLE_ARN` | OSS STS 角色 | CI secret / 本机私有 env | 文件上传链路验收必填 |

### 3.4 当前已识别阻塞点

| 编号 | 阻塞点 | 影响 | 建议处理 |
|---|---|---|---|
| OPS-B01 | `CodeCoachAI-java/.env.local` 存在真实凭证形态值 | 密钥泄露风险；部署验收不能将其作为可提交配置 | 立即从提交范围排除，轮换相关 AI / OSS 凭证；保留 `.env.example` 作为唯一模板 |
| OPS-B02 | `docker-compose.yml` 只编排基础设施，不编排业务服务 | Compose 验收不能证明后端业务服务可用 | README 需明确业务服务由 IDE / Maven 启动，或后续补充业务服务 compose |
| OPS-B03 | MySQL 初始化挂载 `./sql/migration:/docker-entrypoint-initdb.d:ro` | 只在空数据目录初始化时执行；非空卷不会重复执行 | 实际验收需区分空库初始化与已有库 migration |
| OPS-B04 | Nacos 配置中 `codecoachai.oss.enabled=true` 且凭证默认走环境变量 | 缺凭证时文件链路失败；若误填真实值可能泄露 | 本地开发可关闭 OSS 或填临时凭证；验收必须记录选择 |
| OPS-B05 | AI provider fallback 依赖 `DEEPSEEK_API_KEY` 与 `DASHSCOPE_API_KEY` | Key 缺失会导致 AI 链路失败或进入错误 fallback | AI 链路验收前检查 Nacos 最终配置和调用日志 |
| OPS-B06 | 未实际运行 `docker compose up` | 无法确认镜像拉取、端口占用、健康检查真实通过 | 本轮只做静态验收；最终部署需补充运行记录 |

## 4. 最终出具验收记录时必须附带

| 类型 | 必填内容 |
|---|---|
| 修改文件 | 文档、配置、代码文件的相对路径和变更摘要 |
| 命令验证 | 构建、类型检查、`docker compose config`、`git diff --check` 的执行结果 |
| 接口验证 | 每条 V1/V2 核心链路至少一条请求、响应状态、关键字段 |
| 页面验证 | 登录、题库、简历、面试、报告、管理端、Prompt 日志截图或录屏路径 |
| 数据验证 | 关键表记录或管理端查询结果，证明不是前端假数据 |
| fallback 证据 | mockMode、fallback 标记、错误状态、AI 日志或任务中心记录 |
| 残余风险 | 未启动的中间件、未执行的 E2E、未轮换密钥、外部服务不可用等 |
