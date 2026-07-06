# BASE / PSEUDO / OPS 验收矩阵

> 适用范围：CodeCoachAI 前端、后端、文档和部署说明。
> 本文件只定义发布后人工验收门禁。本次静态修复期间不要执行任何会启动服务、构建项目、运行测试、启动 Docker 或监听端口的命令。

## 1. 基础门禁

| 范围 | 发布后人工执行示例 | 预期 | 阻塞条件 |
|---|---|---|---|
| 后端编译 | 勿在静态审查中执行：`mvn clean compile` | 所有模块编译通过 | 公共 DTO、Feign 契约或配置类不兼容 |
| 前端类型检查 | 勿在静态审查中执行：`npx vue-tsc -b` | TypeScript 类型通过 | API 类型、路由参数或组件 props 不兼容 |
| 前端构建 | 勿在静态审查中执行：`npm run build` | 生产构建产物生成 | 路由懒加载、依赖引用或环境变量导致失败 |
| 差异检查 | 静态可读命令：`git diff --check` | 无冲突标记和异常空白 | 出现 `<<<<<<<` 或 whitespace error |

## 2. 伪成功治理

| 类型 | 验收重点 | 阻塞条件 |
|---|---|---|
| AI mock | 发布配置中 `mock-enabled` 必须关闭，日志能区分 mock/fallback | 用户主链路把 mock 当成真实成功 |
| SSE fallback | SSE 未启动时可降级到同步接口，SSE 中途失败不得重复提交 | 页面展示本地伪造业务结果 |
| 题库 fallback | fallback 题必须可识别，不能计入真实题库命中 | fallback 被统计为真实题库数据 |
| 文件/OSS | 缺少 OSS 凭证时明确失败 | 返回假上传成功 |
| MQ 任务 | 触发异步任务后能查到任务或失败记录 | 页面成功但任务中心无记录 |

## 3. 发布后部署验收

| 步骤 | 发布后人工执行示例 | 预期 | 风险 |
|---|---|---|---|
| Compose 配置检查 | 勿在静态审查中执行：`docker compose config` | 配置可展开，不启动容器 | 变量缺失或 YAML 错误 |
| 基础设施启动 | 勿在静态审查中执行：`docker compose up -d mysql redis nacos rocketmq-namesrv rocketmq-broker elasticsearch` | 容器健康 | 端口占用、镜像拉取失败 |
| Nacos 导入 | 勿在静态审查中执行：`powershell -ExecutionPolicy Bypass -File scripts/nacos/import-nacos-config.ps1 -ConfirmWrite` | 配置进入目标 namespace | 误写环境、占位密钥未替换 |
| 后端服务启动 | 勿在静态审查中执行：`mvn -pl <module> spring-boot:run` | 服务注册到 Nacos | 数据源、AI、OSS、MQ 凭证缺失 |
| 前端启动 | 勿在静态审查中执行：`npm run dev` | 页面可通过网关访问接口 | API baseURL 与网关端口不一致 |

## 4. 人工业务链路

| 链路 | 验收重点 |
|---|---|
| 登录 / 当前用户 | token、当前用户、失败错误码真实可见 |
| 题库 / 刷题 | 分页、筛选、收藏、错题、练习记录持久化 |
| 简历上传解析 | 文件大小边界、解析失败提示、解析结果持久化 |
| AI 面试 | 创建、开始、作答、报告生成、fallback 标识 |
| 求职实验台 | 样本不足提示、证据来源、反馈状态和演示数据隔离 |
| 个人知识库 | 检索引用、禁用/删除后的证据状态、反馈不被承诺立即影响推荐 |
| 管理端 | 权限隔离、日志分页、任务中心和公告配置操作确认 |

## 5. 本轮静态限制

本轮只允许阅读和编辑文件、静态搜索和静态契约检查；不执行构建、类型检查、单元测试、E2E、Maven/Gradle/Java 命令、Docker 命令，也不启动任何前端、后端、预览或监听端口进程。
