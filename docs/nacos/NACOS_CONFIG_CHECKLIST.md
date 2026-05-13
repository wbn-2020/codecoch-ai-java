# Nacos Config Checklist

| 检查项 | 结果 | 说明 |
|---|---|---|
| 是否创建 common 配置 | PASS | `codecoachai-common-dev.yml` 已创建并导入。 |
| 是否创建 redis 配置 | PASS | `codecoachai-redis-dev.yml` 已创建并导入。 |
| 是否创建 gateway 配置 | PASS | `codecoachai-gateway-dev.yml` 已创建并导入。 |
| 是否创建 auth 配置 | PASS | `codecoachai-auth-dev.yml` 已创建并导入。 |
| 是否创建 user 配置 | PASS | `codecoachai-user-dev.yml` 已创建并导入。 |
| 是否创建 question 配置 | PASS | `codecoachai-question-dev.yml` 已创建并导入。 |
| 是否创建 resume 配置 | PASS | `codecoachai-resume-dev.yml` 已创建并导入。 |
| 是否创建 ai 配置 | PASS | `codecoachai-ai-dev.yml` 已创建并导入。 |
| 是否创建 interview 配置 | PASS | `codecoachai-interview-dev.yml` 已创建并导入。 |
| 是否创建 system 配置 | PASS | `codecoachai-system-dev.yml` 已创建并导入。 |
| 是否导入 Nacos | PASS | PowerShell 导入脚本返回 10 个 SUCCESS。 |
| 是否各服务能加载 Nacos 配置 | PASS | 8 个服务日志均出现 `[Nacos Config] Load config ... success`。 |
| 是否各服务能注册到 Nacos | PASS | Nacos 服务列表显示 8 个服务 healthy instance count 均为 1。 |
| 是否 Gateway 路由正常 | PASS | 通过 Gateway 完成登录、题库、简历、面试、管理端角色接口验证。 |
| 是否未暴露 `/inner/**` | PASS | Gateway 配置无 `/inner/**` 路由，直接访问 `/inner/auth/token-info` 返回 404。 |
| 是否未新增用户端 `/ai/**` | PASS | Gateway 仅保留 `/admin/ai/**`，无用户端 `/ai/**`。 |
| 是否未把业务数据放进 Nacos | PASS | 用户、题库、简历、面试、Prompt、AI 日志等业务数据未迁入 Nacos。 |
| 是否未引入 V2/V3 功能 | PASS | 未新增 MQ、ES、MinIO、SSE/WebSocket、文件上传、学习计划等能力。 |
| 是否构建通过 | PASS | `mvn clean package -DskipTests` 成功。 |
| 是否完成最小接口验证 | PASS | 登录、token、用户端列表、ADMIN/USER 管理端权限验证均完成。 |
