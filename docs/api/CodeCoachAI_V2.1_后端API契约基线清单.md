# CodeCoachAI V2.1 后端 API 契约基线清单

## 1. 基线说明

本文档记录 V2.1 启动时 `codecoch-ai-java` 后端 Controller 暴露的接口基线，只用于后端安全与契约加固，不修改现有接口路径。

安全边界说明：

- `/admin/**` 需要 `ADMIN` 角色，当前由 Gateway 校验 Token 后透传 `X-Roles`，并由业务服务内 `AdminRoleFilter` 做二次校验。
- 服务内 `AdminRoleFilter` 读取 `X-Roles` Header，不能等价为强安全；直接访问服务端口时 Header 仍可能被伪造。
- 本次 V2.1 最小任务的目标是防止绕过 Gateway 后的误访问和普通请求误入管理端接口。
- 真正防伪造需要后续配合网关签名 Header、内网隔离、服务端口不暴露、统一内部请求签名或 mTLS。
- `/inner/**` 仅限后端服务间调用，前端禁止使用；本次暂不实现 `/inner/**` HMAC 签名。

## 2. 用户端接口主路径

### auth-service

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/register` | 用户注册 |
| POST | `/auth/login` | 用户登录 |
| POST | `/auth/logout` | 用户登出 |
| GET | `/auth/current-user` | 当前用户信息 |
| POST | `/auth/refresh-token` | 刷新 Token |

### user-service

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/users/profile` | 当前用户资料 |
| PUT | `/users/profile` | 更新当前用户资料 |
| PUT | `/users/password` | 修改密码 |
| GET | `/users/overview` | 用户概览 |

### question-service

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/questions` | 题目分页 |
| GET | `/questions/{id}` | 题目详情 |
| POST | `/questions/{id}/answers` | 提交题目回答 |
| POST | `/questions/{id}/favorite` | 收藏题目 |
| DELETE | `/questions/{id}/favorite` | 取消收藏 |
| GET | `/questions/favorites` | 收藏列表 |
| GET | `/questions/wrong-records` | 错题记录 |
| PUT | `/questions/{id}/mastery` | 更新掌握状态 |

### resume-service

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/resumes` | 简历列表 |
| POST | `/resumes` | 创建简历 |
| GET | `/resumes/{id}` | 简历详情 |
| PUT | `/resumes/{id}` | 更新简历 |
| DELETE | `/resumes/{id}` | 删除简历 |
| PUT | `/resumes/{id}/default` | 设置默认简历 |
| POST | `/resumes/{resumeId}/projects` | 新增项目经历 |
| PUT | `/resumes/{resumeId}/projects/{projectId}` | 更新项目经历 |
| DELETE | `/resumes/{resumeId}/projects/{projectId}` | 删除项目经历 |

### interview-service

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/interviews` | 创建面试 |
| POST | `/interviews/{id}/start` | 开始面试 |
| GET | `/interviews/{id}/current` | 当前面试状态 |
| GET | `/interviews/{id}/current-question` | 当前题目 |
| POST | `/interviews/{id}/answer` | 提交面试回答 |
| POST | `/interviews/{id}/finish` | 结束面试并生成报告 |
| POST | `/interviews/{id}/report/retry` | 重试生成报告 |
| GET | `/interviews` | 面试历史 |
| GET | `/interviews/{id}` | 面试详情 |
| GET | `/interviews/{id}/messages` | 面试消息 |
| GET | `/interviews/{id}/report` | 面试报告 |

## 3. 管理端接口

### 主路径

| 服务 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| user-service | GET | `/admin/users` | 管理员用户列表 |
| user-service | PUT | `/admin/users/{id}/status` | 更新用户状态 |
| user-service | GET | `/admin/roles` | 管理员角色列表 |
| question-service | GET | `/admin/questions` | 管理员题目分页 |
| question-service | GET | `/admin/questions/{id}` | 管理员题目详情 |
| question-service | POST | `/admin/questions` | 创建题目 |
| question-service | PUT | `/admin/questions/{id}` | 更新题目 |
| question-service | DELETE | `/admin/questions/{id}` | 删除题目 |
| question-service | PUT | `/admin/questions/{id}/status` | 更新题目状态 |
| question-service | GET | `/admin/question-categories` | 题目分类列表 |
| question-service | POST | `/admin/question-categories` | 创建题目分类 |
| question-service | PUT | `/admin/question-categories/{id}` | 更新题目分类 |
| question-service | DELETE | `/admin/question-categories/{id}` | 删除题目分类 |
| question-service | PUT | `/admin/question-categories/{id}/status` | 更新题目分类状态 |
| question-service | GET | `/admin/question-tags` | 题目标签列表 |
| question-service | POST | `/admin/question-tags` | 创建题目标签 |
| question-service | PUT | `/admin/question-tags/{id}` | 更新题目标签 |
| question-service | DELETE | `/admin/question-tags/{id}` | 删除题目标签 |
| question-service | PUT | `/admin/question-tags/{id}/status` | 更新题目标签状态 |
| question-service | GET | `/admin/question-groups` | 题目组列表 |
| question-service | GET | `/admin/question-groups/{id}` | 题目组详情 |
| question-service | POST | `/admin/question-groups` | 创建题目组 |
| question-service | PUT | `/admin/question-groups/{id}` | 更新题目组 |
| question-service | DELETE | `/admin/question-groups/{id}` | 删除题目组 |
| question-service | PUT | `/admin/question-groups/{id}/status` | 更新题目组状态 |
| ai-service | GET | `/admin/ai/prompts` | Prompt 模板分页 |
| ai-service | POST | `/admin/ai/prompts` | 创建 Prompt 模板 |
| ai-service | GET | `/admin/ai/prompts/{id}` | Prompt 模板详情 |
| ai-service | PUT | `/admin/ai/prompts/{id}` | 更新 Prompt 模板 |
| ai-service | DELETE | `/admin/ai/prompts/{id}` | 删除 Prompt 模板 |
| ai-service | PUT | `/admin/ai/prompts/{id}/status` | 更新 Prompt 状态 |
| ai-service | GET | `/admin/ai/call-logs` | AI 调用日志分页 |
| ai-service | GET | `/admin/ai/call-logs/{id}` | AI 调用日志详情 |
| system-service | GET | `/admin/configs` | 系统配置列表 |
| system-service | POST | `/admin/configs` | 创建系统配置 |
| system-service | PUT | `/admin/configs/{id}` | 更新系统配置 |
| system-service | DELETE | `/admin/configs/{id}` | 删除系统配置 |
| system-service | GET | `/admin/system/overview` | 管理端系统概览 |

### 兼容路径

以下接口为兼容路径，V2 后续前后端契约应优先使用主路径，避免继续扩散旧路径。

| 服务 | 方法 | 路径 | 对应主路径 |
| --- | --- | --- | --- |
| question-service | GET | `/admin/questions/page` | `/admin/questions` |
| question-service | GET | `/admin/question-categories/list` | `/admin/question-categories` |
| question-service | GET | `/admin/question-categories/tree` | `/admin/question-categories` |
| question-service | GET | `/admin/question-tags/list` | `/admin/question-tags` |
| question-service | GET | `/admin/question-tags/page` | `/admin/question-tags` |
| question-service | GET | `/admin/question-groups/list` | `/admin/question-groups` |
| question-service | GET | `/admin/question-groups/page` | `/admin/question-groups` |
| ai-service | GET | `/admin/ai/prompts/page` | `/admin/ai/prompts` |
| ai-service | GET | `/admin/ai/logs` | `/admin/ai/call-logs` |
| ai-service | GET | `/admin/ai/logs/page` | `/admin/ai/call-logs` |
| ai-service | GET | `/admin/ai/logs/{id}` | `/admin/ai/call-logs/{id}` |

## 4. 内部接口

内部接口不对前端开放，不应配置为浏览器可直接访问路径。

| 服务 | 方法 | 路径 | 调用方 |
| --- | --- | --- | --- |
| auth-service | GET | `/inner/auth/token-info` | gateway |
| user-service | GET | `/inner/users/by-username` | auth-service |
| user-service | POST | `/inner/users` | auth-service |
| user-service | GET | `/inner/users/{id}/roles` | auth-service |
| user-service | GET | `/inner/users/{id}` | auth-service |
| question-service | POST | `/inner/questions/select` | interview-service |
| question-service | POST | `/inner/questions/pick-for-interview` | interview-service |
| question-service | GET | `/inner/questions/{id}` | interview-service |
| question-service | GET | `/inner/questions/recommend` | interview-service |
| question-service | POST | `/inner/questions/recommend-for-report` | interview-service |
| resume-service | GET | `/inner/resumes/{id}` | interview-service |
| resume-service | GET | `/inner/resumes/{id}/projects` | interview-service |
| resume-service | GET | `/inner/resumes/default` | interview-service |
| ai-service | POST | `/inner/ai/interview/question` | interview-service |
| ai-service | POST | `/inner/ai/interview/evaluate` | interview-service |
| ai-service | POST | `/inner/ai/interview/follow-up` | interview-service |
| ai-service | POST | `/inner/ai/interview/report` | interview-service |

## 5. 健康检查接口

以下接口为服务健康检查路径，不要求 `X-Roles`。

| 服务 | 方法 | 路径 |
| --- | --- | --- |
| ai-service | GET | `/health` |
| question-service | GET | `/health` |
| resume-service | GET | `/health` |
| interview-service | GET | `/health` |
| system-service | GET | `/health` |

## 6. V2.1 最小加固结论

- 本基线不改变任何 Controller 路径。
- 本基线不新增数据库表和字段。
- `/admin/**` 在 Gateway 校验之外增加服务内 `ADMIN` 二次校验。
- `/questions`、`/resumes`、`/interviews`、`/users`、`/auth` 等用户端路径不受 `AdminRoleFilter` 影响。
- `/inner/**` 仍沿用当前内部调用过滤器，后续单独规划 HMAC 签名、防重放和网关签名 Header。
