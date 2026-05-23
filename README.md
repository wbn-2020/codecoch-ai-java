# CodeCoachAI Java 后端

CodeCoachAI 是一个面向 Java 开发者求职准备场景的 AI 面试训练与简历优化平台。本仓库是 CodeCoachAI 的 Java 后端项目，采用 Spring Boot / Spring Cloud Alibaba 微服务架构，围绕题库训练、简历解析与优化、AI 模拟面试、面试报告、学习计划、目标岗位匹配、能力画像、任务中心、通知、搜索和 AI 调用治理等能力进行建设。

当前后端主线已经从 V1/V2 的基础闭环和 AI 能力增强，推进到 V3 的岗位目标驱动求职训练闭环，并在 `dev-v4` 上进入 V4 个人求职智能体与数据分析增强开发。本 README 同时覆盖已实现的 V1/V2/V3 主链路和当前 V4 后端增量。

## 项目定位

CodeCoachAI 的目标不是做一个简单的题库或聊天机器人，而是构建一条完整的求职训练链路：

```text
目标岗位 / JD
  ↓
简历解析与简历-JD 匹配
  ↓
能力画像与技能差距分析
  ↓
学习计划与题目推荐
  ↓
AI 模拟面试与动态追问
  ↓
面试报告与薄弱点反馈
  ↓
持续训练和复盘
```

后端项目重点展示 Java 微服务、AI 应用工程化、异步任务、文件存储、搜索、日志审计、Prompt 治理和可观测性等能力。

## 当前开发进度概览

### V1：微服务核心闭环

V1 主要完成了项目的基础架构和 AI 面试核心闭环：

- Spring Cloud Alibaba 微服务骨架。
- Gateway 网关统一入口。
- 认证、用户、题库、简历、面试、AI、系统等服务拆分。
- 用户注册、登录、退出、当前用户、资料与密码管理。
- 题库、分类、标签、问题组管理。
- 用户刷题、提交答案、收藏、错题、掌握状态。
- 简历手动录入、编辑、项目经历和默认简历。
- 创建 AI 模拟面试、当前问题、提交回答、动态追问。
- 面试历史、面试详情和结构化面试报告。
- Prompt 模板和 AI 调用日志基础能力。
- 统一返回、统一异常、权限上下文、用户数据归属校验。

### V2：AI 能力增强

V2 在 V1 基础上补充了更完整的 AI 产品能力：

- 简历文件上传与解析状态管理。
- PDF / Word / Markdown / TXT 等简历文本解析能力。
- AI 简历结构化解析。
- AI 简历优化建议、风险提示和优化记录。
- 行业场景面试与行业模板管理。
- AI 题目生成与审核流程。
- 题库去重、疑似重复题审核和题目关系管理。
- 学习计划、每日任务、任务完成/跳过。
- 简答题 AI 点评。
- SSE 流式输出，用于面试提问、点评、报告、简历优化、学习计划、题目生成等场景。
- Prompt 模板版本管理、回滚、测试和 AI 日志增强。
- 文件记录管理和上传文件元数据治理。

### V3：岗位目标驱动闭环与工程化增强

V3 重点把 V2 的能力串成更完整的求职训练闭环，同时补齐工程化能力：

- 目标岗位管理和 JD 解析。
- 简历-JD 匹配报告。
- 能力画像和技能差距分析。
- 基于差距生成学习计划。
- 基于岗位、画像、短板的题目推荐。
- 目标岗位相关的模拟面试能力增强。
- V3 求职驾驶舱聚合能力。
- Redis 缓存、限流、幂等、锁等基础能力。
- RocketMQ 异步任务、任务中心、死信任务和通知联动基础。
- 文件服务增强，支持本地存储与阿里云 OSS 方向。
- Elasticsearch 搜索服务，覆盖题库、简历、面试记录等全文搜索方向。
- 操作日志、登录日志和后台审计能力。
- Docker Compose、Nacos 配置、基础设施部署脚本。
- AI workflow / traceId / ai_call_log 等 AI 调用治理基础。

## 当前已实现的主要后端模块

| 模块 | 说明 |
|---|---|
| `codecoachai-gateway` | 网关服务，负责统一入口、鉴权、Header 透传、内部接口保护、路由转发。 |
| `codecoachai-auth` | 认证服务，负责注册、登录、退出、Token、当前用户等能力。 |
| `codecoachai-user` | 用户服务，负责用户资料、角色、权限、用户管理、Dashboard 聚合等。 |
| `codecoachai-question` | 题库服务，负责题目、分类、标签、问题组、刷题、收藏、错题、AI 题目审核、去重等。 |
| `codecoachai-resume` | 简历服务，负责简历 CRUD、项目经历、简历上传解析、AI 优化、目标岗位、简历-JD 匹配、能力画像等。 |
| `codecoachai-interview` | 面试服务，负责模拟面试、面试状态机、答题、动态追问、报告、学习计划等。 |
| `codecoachai-ai` | AI 服务，负责模型调用、Prompt 渲染、AI 日志、简历解析、简历优化、题目生成、JD 解析、匹配分析等。 |
| `codecoachai-file` | 文件服务，负责文件上传、文件记录、存储 Provider、文件元数据管理。 |
| `codecoachai-task` | 任务服务，负责异步任务、通知、死信、MQ 消费等工程化能力。 |
| `codecoachai-search` | 搜索服务，负责 Elasticsearch 索引、搜索、索引同步和管理。 |
| `codecoachai-system` | 系统服务，负责系统配置、日志审计、后台治理能力。 |
| `codecoachai-common` | 公共模块，包含 core、web、security、mybatis、redis、feign、log、ai、oss、mq 等通用能力。 |

## 技术栈

| 类型 | 技术 |
|---|---|
| JDK | Java 17 |
| 基础框架 | Spring Boot 3.2.x |
| 微服务 | Spring Cloud 2023.x、Spring Cloud Alibaba 2023.x |
| 网关 | Spring Cloud Gateway |
| 注册与配置 | Nacos |
| 服务调用 | OpenFeign |
| ORM | MyBatis-Plus |
| 数据库 | MySQL 8 |
| 缓存 | Redis / Redisson |
| 权限 | Sa-Token |
| 消息队列 | RocketMQ |
| 搜索 | Elasticsearch 8 |
| 文件存储 | 本地存储、阿里云 OSS 方向 |
| API 文档 | Knife4j / OpenAPI |
| AI 调用 | OpenAI Compatible Client，当前主要面向 DeepSeek 兼容接口 |
| 部署 | Docker Compose、Nacos 配置导入脚本 |

## 目录结构

```text
CodeCoachAI-java
├── codecoachai-common                 # 公共模块
│   ├── common-core                     # Result、错误码、异常、基础实体
│   ├── common-web                      # Web、全局异常、日志切面
│   ├── common-security                 # 登录上下文、安全过滤器
│   ├── common-mybatis                  # MyBatis-Plus 公共配置
│   ├── common-redis                    # Redis 工具与缓存基础
│   ├── common-feign                    # Feign 公共能力
│   ├── common-ai                       # AI 通用客户端能力
│   ├── common-oss                      # OSS 存储能力
│   └── common-mq                       # MQ 消息封装
├── codecoachai-gateway                 # API 网关
├── codecoachai-auth                    # 认证服务
├── codecoachai-user                    # 用户服务
├── codecoachai-question                # 题库服务
├── codecoachai-resume                  # 简历、岗位、匹配、画像服务
├── codecoachai-interview               # 面试和学习计划服务
├── codecoachai-ai                      # AI 调用、Prompt、AI 日志服务
├── codecoachai-file                    # 文件服务
├── codecoachai-task                    # 异步任务和通知服务
├── codecoachai-search                  # 搜索服务
├── codecoachai-system                  # 系统配置和审计服务
├── docs                                # 后端相关文档，含官方 Nacos 配置源 docs/nacos
├── config/nacos                        # 历史/手工 Nacos 模板，保留作参考
├── scripts                             # 辅助脚本
├── sql                                 # 初始化 SQL / migration 脚本
├── docker-compose.yml                  # 基础设施编排
└── pom.xml                             # Maven 根工程
```

## 本地开发环境

### 基础依赖

本地开发建议先启动以下基础设施：

```text
MySQL 8
Redis
Nacos
RocketMQ
Elasticsearch（搜索相关功能需要）
```

如果只验证 V1/V2 基础接口，可以先启动 MySQL、Redis、Nacos；如果验证异步任务、搜索、V3 工程化能力，需要同时启动 RocketMQ 和 Elasticsearch。

### 导入 Nacos 配置

项目提供 Nacos 配置导入脚本，启动 Nacos 后执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\nacos\import-nacos-config.ps1
```

官方导入脚本默认读取：

```text
docs/nacos/
```

`config/nacos/` 仅保留为历史/手工模板参考，不再作为默认导入源；如手工上传，请优先对照 `docs/nacos/`。

dev 验收口径使用真实阿里云 OSS：`codecoachai.file.storage.provider=ALIYUN_OSS`，`codecoachai.oss.enabled=true`。启动文件服务前请在环境变量或私有 Nacos 配置中提供 `OSS_BUCKET`、`OSS_AK`、`OSS_SK`、`OSS_STS_ROLE_ARN` 等凭证；缺失时应用会在启动期明确失败，而不是等上传时才报错。

### 启动后端服务

按需启动服务，例如：

```powershell
mvn -pl codecoachai-gateway spring-boot:run
mvn -pl codecoachai-auth spring-boot:run
mvn -pl codecoachai-user spring-boot:run
mvn -pl codecoachai-question spring-boot:run
mvn -pl codecoachai-resume spring-boot:run
mvn -pl codecoachai-interview spring-boot:run
mvn -pl codecoachai-ai spring-boot:run
mvn -pl codecoachai-file spring-boot:run
mvn -pl codecoachai-task spring-boot:run
mvn -pl codecoachai-search spring-boot:run
mvn -pl codecoachai-system spring-boot:run
```

也可以先只启动当前联调所需服务，避免一次性启动全部模块。

## 数据库说明

本地开发统一使用 `codecoachai_v1` 作为默认数据库名，需与 `docs/nacos/*-dev.yml` 中的数据源配置保持一致。

Flyway Maven 插件默认也指向 `codecoachai_v1`。如需迁移到其他库，可在命令行覆盖：

```powershell
mvn flyway:migrate "-Dflyway.url=jdbc:mysql://127.0.0.1:3306/your_db?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
```

SQL 目录通常包含：

```text
sql/init.sql
sql/migration/
```

开发或联调前请确认：

1. 当前 Nacos 数据源配置中的数据库名称。
2. `sql/init.sql` 是否已导入。
3. `sql/migration` 下 V2/V3/V4 迁移脚本是否已按顺序执行。
4. Flyway 或 Docker 初始化方式是否与当前环境一致。

Windows 下执行 SQL 示例：

```powershell
$env:MYSQL_PWD="your-local-password"
mysql --host=127.0.0.1 --user=root --default-character-set=utf8mb4 --database=codecoachai_v1 -e "source sql/migration/V2_008__practice_answer_review.sql"
Remove-Item Env:\MYSQL_PWD
```

## 重要接口与能力

### 认证与用户

```text
/auth/register
/auth/login
/auth/logout
/auth/current-user
/users/profile
/users/password
/admin/users
```

### 题库与刷题

```text
/questions
/questions/{id}
/questions/{id}/answers
/questions/{id}/favorite
/questions/favorites
/questions/wrong-records
/questions/{id}/mastery
/admin/questions
/admin/question-categories
/admin/question-tags
/admin/question-groups
/admin/question-reviews
/admin/question-duplicate-reviews
```

### 简历、岗位、匹配与画像

```text
/resumes
/resumes/upload
/resumes/{id}/parse-status
/resumes/{id}/analysis-result
/resumes/{id}/confirm-analysis
/resumes/{id}/optimize
/resumes/{id}/optimize-records
/job-targets
/job-targets/{id}/parse
/resume-job-match/reports
/skill-profiles
```

### 面试与学习计划

```text
/interviews
/interviews/{id}/start
/interviews/{id}/current
/interviews/{id}/answer
/interviews/{id}/finish
/interviews/{id}/report
/study-plans/generate
/study-plans/generate-from-gap
/study-plans/{id}/tasks
/study-tasks/{id}/complete
/study-tasks/{id}/skip
```

### SSE 流式接口

```text
GET  /ai/sse/interview-question?sessionId={id}
GET  /ai/sse/interview-comment?sessionId={id}&answerContent={shortAnswer}
POST /ai/sse/interview-comment?sessionId={id}
GET  /ai/sse/report?sessionId={id}
GET  /ai/sse/resume-optimize?resumeId={id}
GET  /ai/sse/study-plan?reportId={id}
GET  /ai/sse/admin/questions/generate?count={count}
```

SSE 事件约定：

```text
start      流式任务开始
progress   阶段进度
chunk      主要内容片段
delta      chunk 的兼容别名
result     结构化业务结果
metadata   业务元数据
done       流式任务完成
error      脱敏后的失败事件
```

## AI 与 Prompt 治理

后端已经具备 AI 调用、Prompt 模板、Prompt 版本、AI 调用日志等基础能力。

主要能力包括：

- Prompt 模板管理。
- Prompt 版本管理、激活、回滚、测试。
- AI 调用日志记录。
- 模型名称、耗时、状态、失败原因等信息记录。
- JD 解析、简历解析、简历优化、题目生成、学习计划、面试点评、报告生成等 AI 场景。

需要注意：dev Nacos 的 `docs/nacos/codecoachai-ai-dev.yml` 默认走真实 DeepSeek 调用，`codecoachai.ai.mock-enabled=false`。启动 AI 服务前请提供 `DEEPSEEK_API_KEY`；缺失时应用会在启动期明确失败，避免把 mock/fallback 结果误认为真实 AI 输出。管理端保存模型密钥还需要配置 `CODECOACHAI_AI_CRYPTO_SECRET_KEY`。

## 当前已知问题与后续重点

根据当前 PRD 对照审查，后端仍有以下重点问题需要继续完善：

| 问题 | 说明 | 优先级 |
|---|---|---|
| AI mock/fallback 边界 | 部分 AI 失败后可能返回 mock/fallback，影响评分、追问、报告真实性 | P0 |
| 简历优化 apply | 当前更偏生成草稿，尚未形成稳定结构化 patch 落库 | P0 |
| V3 Dashboard 聚合 | 当前聚合实现仍需增强稳定空态、服务分层和字段一致性 | P0/P1 |
| 任务中心闭环 | 任务记录、重试、通知、死信处理需继续完善 | P1 |
| 操作日志覆盖 | AOP 能力存在，但核心写操作日志覆盖度需检查和补齐 | P1 |
| 前后端契约 | 部分 VO、SSE 事件、返回结构仍需固定契约 | P1 |
| Migration 一致性 | Flyway、Docker 初始化、SQL migration 执行路径需实跑验证 | P1 |
| 搜索前后端联动 | 后端搜索服务已具备，前端统一搜索入口仍需完善 | P2 |

## V4 开发状态

V4 已在 `dev-v4` 分支进入编码和本机联调阶段，当前增量已经覆盖 JobCoachAgent、成长档案、个人/后台 Analytics、Prompt Regression、简历版本与求职进度等主能力。真实 DeepSeek、OSS、Gateway 和 Nacos 仍以运行时配置与验收环境为准，仓库文档不保存生产密钥。

V4 定位为：

```text
个人求职智能体 + 长期成长档案 + 数据分析看板 + AI 工程化增强
```

V4 不做支付、会员、企业版、多租户、真实 B 端场景。

V4 已完成的主要文档包括：

```text
CodeCoachAI-doc/PRD/CodeCoachAI_PRD_V4_个人求职智能体与数据分析增强版.md
CodeCoachAI-doc/MD/V4/V4_开发路线图.md
CodeCoachAI-doc/MD/V4/V4-A/V4-A_技术设计.md
CodeCoachAI-doc/MD/V4/V4-A/V4-A_数据库设计.md
CodeCoachAI-doc/MD/V4/V4-A/V4-A_API契约.md
CodeCoachAI-doc/MD/V4/V4-A/V4-A_Prompt设计.md
CodeCoachAI-doc/MD/V4/V4-A/V4-A_开发任务拆解.md
CodeCoachAI-doc/MD/V4/V4-C/V4-C-1_基础BI看板_预研.md
```

V4-A 已落地 JobCoachAgent MVP 主路径，包括：

- `agent_run`。
- `agent_task`。
- AgentContextBuilder。
- CandidateTaskBuilder。
- JobCoachAgent Prompt。
- 今日计划生成。
- 今日任务查询。
- 任务完成/跳过。
- Agent 运行详情。

## 验证与测试

后端代码修改后建议执行：

```powershell
mvn clean compile
git diff --check
```

如果修改了 SQL，还需要执行：

```text
空库导入验证
migration 重复执行验证
关键表和索引存在性验证
```

建议基础 Smoke Test 覆盖：

- 注册 / 登录 / 当前用户。
- 题库列表 / 题目详情 / 提交答案。
- 简历上传 / 解析状态 / 确认解析结果。
- 简历优化 / 优化记录。
- 创建面试 / 当前问题 / 提交回答 / 报告生成。
- 学习计划生成 / 任务完成 / 跳过。
- AI 题目生成 / 审核 / 去重。
- Prompt 版本测试 / AI 调用日志。
- 文件上传 / 文件管理。
- 通知中心。
- 搜索接口。
- 用户数据隔离和管理员权限。

## 开发边界

1. 后端仓库不直接修改前端代码。
2. 新增接口应保持 Gateway 路由、权限和用户归属校验一致。
3. `/inner/**` 内部接口必须保持内部调用保护。
4. `/admin/**` 接口必须要求管理员权限。
5. AI 相关接口必须记录调用日志和失败原因。
6. 新增 AI 能力不应直接返回 mock 作为成功结果，除非明确处于本地开发 mock 模式。
7. 新增数据库表必须提供 migration，并保证可重复执行或符合项目当前 migration 规范。
8. 不新增支付、会员、企业版、多租户等商业化功能。

## 相关仓库

```text
前端仓库：CodeCoachAI-vue
后端仓库：CodeCoachAI-java
文档仓库：CodeCoachAI-doc
```

## 说明

本 README 依据当前项目开发进度、PRD 对照审查结果和 V4 规划文档整理，目的是为后端开发、联调和作品集展示提供中文说明。后续 V4 功能、验收范围或运行配置策略变化时，应同步更新本文档中的 V4 代码实现状态。

## 最近联调验证

以下链路已在本地联调环境完成验证，当前结论是“可继续按上线标准收敛”，但仍建议在正式发布前复跑一轮与生产一致的配置：

- Gateway 健康检查：`GET /health`
- AI 健康检查：`GET /ai/health`
- 搜索链路：`GET /search?keyword=Java`，已兼容旧参数入口
- Prompt 回归：`POST /admin/agent/prompt-regression/cases` 缺参返回 `40000`
- DeepSeek 日常计划生成：`POST /agent/job-coach/daily-plan/generate`
- OSS 链路：STS、上传、详情、下载均已跑通

本次验证覆盖了后端核心链路、第三方链路和管理后台关键入口。DeepSeek、OSS 等外部依赖仍应通过运行时配置注入，仓库内不保存生产密钥。

## 前端联动说明

前端仓库：`CodeCoachAI-vue`

当前与本次联调直接相关的页面主要包括：

- `src/views/admin/AdminPromptRegressionView.vue`
- `src/views/v4/KnowledgeBaseView.vue`
- `src/views/v4/JobApplicationView.vue`
- `src/views/resume/ResumeListView.vue`
- `src/views/admin/AdminFileManageView.vue`

前端路由中已包含 V4/V4-A 相关入口，以及管理后台的 Prompt 回归页面：

- `/admin/ai/prompt-regression`
- `/agent/tasks`
- `/agent/runs/:id`
- `/knowledge`
- `/applications`
- `/resumes`

前端联调建议优先检查这几条能力：

- 登录后能正常进入管理后台与用户侧主页面
- Prompt 回归页能正常加载、筛选、创建与执行
- 简历上传、文件下载、知识库检索、投递管理等页面能正常调用后端接口

## 发布前检查建议

若按上线标准继续收敛，建议在发布前再补一轮：

1. 生产配置下复跑 Gateway、AI、File、Search 的健康检查
2. 复跑 DeepSeek 真实调用与 OSS 上传下载
3. 复跑前端管理后台关键页和用户侧核心页
4. 确认无未提交的日志目录、临时文件和本地调试产物
