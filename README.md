# CodeCoachAI README 启动顺序

## 1. 项目目录

```text
C:\my-claude
├── CodeCoachAI-java   后端项目
├── CodeCoachAI-vue    前端项目
└── CodeCoachAI-doc    文档项目
```

---

## 2. 技术栈

### 2.1 后端

- Java 17
- Spring Boot 3
- Spring Cloud Alibaba
- Spring Cloud Gateway
- Nacos
- OpenFeign
- MyBatis-Plus
- MySQL 8
- Redis
- Sa-Token / Token 认证
- Knife4j / Swagger
- 大模型 API 接入

### 2.2 前端

- Vue 3
- Vite
- TypeScript
- Element Plus
- Vue Router
- Pinia
- Axios
- ECharts

---

## 3. 启动前准备

请确认本机已安装：

- JDK 17
- Maven 3.8+
- Node.js 18+
- MySQL 8
- Redis
- Nacos
- Git

---

## 4. 启动顺序总览

推荐顺序：

```text
1. 启动 MySQL
2. 启动 Redis
3. 启动 Nacos
4. 导入数据库 SQL
5. 配置 Nacos
6. 启动后端服务
7. 启动前端服务
8. 登录测试账号验证
```

---

## 5. 启动 MySQL

确保 MySQL 已启动，并已创建 CodeCoachAI 使用的数据库。

示例：

```sql
CREATE DATABASE IF NOT EXISTS codecoachai_v1 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

如果你的项目配置中数据库名不是 `codecoachai_v1`，以实际配置为准。

---

## 6. 启动 Redis

确认 Redis 正常启动。

常见默认地址：

```text
127.0.0.1:6379
```

可用以下命令测试：

```bash
redis-cli ping
```

正常返回：

```text
PONG
```

---

## 7. 启动 Nacos

进入本地 Nacos 目录后启动。

Windows 示例：

```bash
startup.cmd -m standalone
```

Linux / macOS 示例：

```bash
sh startup.sh -m standalone
```

访问：

```text
http://127.0.0.1:8848/nacos
```

默认账号密码通常为：

```text
nacos / nacos
```

以本地实际配置为准。

---

## 8. 导入数据库 SQL

进入后端项目目录：

```bash
cd C:\my-claude\CodeCoachAI-java
```

导入初始化 SQL 和测试数据。

示例：

```bash
mysql -u root -p codecoachai_v1 < sql/dev_test_data.sql
```

说明：

- `dev_test_data.sql` 包含 V1 本地联调演示数据。
- 包含普通用户和管理员测试账号。
- 包含中文题库、分类、标签、问题组、Prompt、AI 日志、面试报告样例。

如项目还有单独建表 SQL，请先导入建表 SQL，再导入 `dev_test_data.sql`。

---

## 9. 配置 Nacos

确认 Nacos 中存在各服务需要的配置，例如：

- 数据库连接信息
- Redis 连接信息
- Token / Sa-Token 配置
- AI 模型 API 配置
- Gateway 路由配置
- 各服务端口配置

V1 本地演示可将数据库、Redis、AI 配置放在 Nacos 中。

注意：

- API Key 不建议提交到 GitHub。
- 个人作品集本地演示可以使用本机 Nacos 配置。
- 如果没有真实大模型 Key，应确认后端有中文兜底逻辑，避免演示失败。

---

## 10. 启动后端服务

进入后端项目：

```bash
cd C:\my-claude\CodeCoachAI-java
```

先构建：

```bash
mvn -q -DskipTests package
```

构建通过后启动服务。

推荐启动顺序：

```text
1. codecoachai-gateway
2. codecoachai-auth
3. codecoachai-user
4. codecoachai-question
5. codecoachai-resume
6. codecoachai-interview
7. codecoachai-ai
8. codecoachai-system
```

如果使用 IDE，可以分别运行各模块的 Application 启动类。

如果使用命令行，请进入对应模块执行 jar 或 Spring Boot 启动命令，具体命令以项目打包结果为准。

启动后确认：

- Gateway 正常监听：`8080`
- Nacos 中能看到各服务实例
- 各服务无启动异常

---

## 11. 启动前端服务

进入前端项目：

```bash
cd C:\my-claude\CodeCoachAI-vue
```

安装依赖：

```bash
npm install
```

启动开发服务：

```bash
npm run dev
```

默认访问：

```text
http://127.0.0.1:5173
```

构建验证：

```bash
npm run build
```

---

## 12. 测试账号

| 角色     | 用户名      | 密码           |
| -------- | ----------- | -------------- |
| 普通用户 | `e2e_user`  | `E2eUser@123`  |
| 管理员   | `e2e_admin` | `E2eAdmin@123` |

---

## 13. 快速验证流程

### 13.1 管理端验证

1. 打开：`http://127.0.0.1:5173`
2. 使用管理员登录：
   - username：`e2e_admin`
   - password：`E2eAdmin@123`
3. 打开题目管理。
4. 验证题目列表能加载。
5. 验证标签筛选能生效。
6. 验证状态筛选能生效。
7. 打开 Prompt 模板。
8. 验证 keyword / scene / status 筛选能生效。

---

### 13.2 用户端验证

1. 使用普通用户登录：
   - username：`e2e_user`
   - password：`E2eUser@123`
2. 打开题库页面。
3. 选择标签 `E2E_TEST_缓存`。
4. 确认返回 `Total 3`。
5. 打开题目详情。
6. 查看参考答案和解析。
7. 进入面试历史。
8. 打开报告：`/interviews/100005/report`
9. 确认报告为中文。
10. 确认不出现 `Mock report`。

---

## 14. Network 检查项

浏览器 Network 中应确认：

- 无业务 404。
- 用户端不调用 `/admin/**`。
- 无 `/inner/**`。
- 无旧 `/ai/interview/**`。
- 无 `questions/answer`。
- 无 `questions/wrongs`。
- 分类、标签、问题组不调用 `/status` 子路径。

---

## 15. 常见问题排查

### 15.1 登录失败

检查：

1. 后端 auth 服务是否启动。
2. Gateway 是否启动。
3. Nacos 服务是否注册成功。
4. 数据库中是否存在 `e2e_user` / `e2e_admin`。
5. 前端请求地址是否指向 Gateway。

---

### 15.2 前端接口 404

检查：

1. Gateway 路由是否正确。
2. 对应业务服务是否启动。
3. 前端 API 路径是否仍调用旧接口。
4. Nacos 中服务名是否匹配。

---

### 15.3 题库标签筛选返回 0

检查：

1. 后端 `/questions` 是否返回真实 `tags[].id` 或 `tagIds`。
2. 前端是否使用真实 `tagId`。
3. 请求中是否误传 `tagIds`。
4. `question_tag_relation` 中是否存在对应关系。

---

### 15.4 面试报告出现英文 Mock report

检查：

1. 是否使用了旧数据库数据。
2. 后端报告查询时是否已做历史英文报告自动中文化。
3. AI fallback 是否仍返回英文内容。
4. 是否需要重新导入 `sql/dev_test_data.sql`。

---

### 15.5 Nacos 未注册服务

检查：

1. Nacos 是否启动。
2. 服务配置中的 Nacos 地址是否正确。
3. 服务启动日志是否有注册失败。
4. 端口是否被占用。

---

## 16. V1 演示边界

V1 当前用于展示最小核心闭环，不包含以下能力：

- 文件上传
- MinIO
- MQ
- Elasticsearch
- SSE / WebSocket
- AI 简历优化
- 学习计划
- AI 题目生成
- Prompt 版本管理
- Embedding 语义去重
- 完整题型体系
- 复杂数据看板

这些能力放到 V2 / V3。

---

## 17. 推荐演示顺序

```text
1. 管理员登录
2. 展示题目管理、分类、标签、问题组
3. 展示 Prompt 模板和 AI 日志
4. 普通用户登录
5. 展示题库筛选和题目详情
6. 展示简历管理
7. 创建 AI 面试
8. 进入面试房间并开始面试
9. 查看面试历史
10. 查看中文面试报告
```

---

## 18. 提交前检查清单

- [ ] 后端 `mvn -q -DskipTests package` 通过。
- [ ] 前端 `npm run build` 通过。
- [ ] 管理员登录正常。
- [ ] 普通用户登录正常。
- [ ] 用户端题库标签筛选正常。
- [ ] 面试报告中文化正常。
- [ ] Network 无业务 404。
- [ ] 用户端无误调 `/admin/**`。
- [ ] 文档文件名使用中文。

