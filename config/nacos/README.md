# Nacos 配置文件模板

本目录存放各服务在 Nacos 中的配置模板。请按下表上传到本机 Nacos 控制台。

## 上传步骤

1. 启动 Nacos：
   ```cmd
   C:\my-claude\comware\nacos-server-2.5.2\bin\startup.cmd -m standalone
   ```
2. 浏览器打开：http://localhost:8848/nacos （默认 nacos / nacos）
3. 进入"配置管理 → 配置列表"，点 ➕ 新建配置
4. 按下表逐个上传（Group 全部填 `DEFAULT_GROUP`，类型选 `YAML`）

| Data ID | 文件 | 必填 |
|---------|------|------|
| `codecoachai-common-dev.yml` | `codecoachai-common-dev.yml` | ✅ 必填，所有服务共用 |
| `codecoachai-file-dev.yml` | `codecoachai-file-dev.yml` | ✅ 必填 |
| `codecoachai-task-dev.yml` | `codecoachai-task-dev.yml` | ✅ 必填 |
| `codecoachai-search-dev.yml` | `codecoachai-search-dev.yml` | ✅ 必填 |

其他服务（auth/user/question/resume/interview/ai/system/gateway）暂时不配置专属 yml 也能跑（落到 common 默认值）。

## 关键参数说明

### codecoachai-common-dev.yml

- **datasource**：MySQL 已写好（`localhost:3306/codecoachai`，用户名 `root`，密码通过 `${MYSQL_PASSWORD:}` 注入）。**首次启动前请先在 MySQL 里执行 `sql/init.sql` 建库建表**。
- **redis**：默认无密码，端口 6379
- **rocketmq**：默认 `127.0.0.1:9876`
- **codecoachai.oss.enabled**：默认 `false`（先用本地存储），等申请到 OSS 后改 `true` 并填三项凭证
- **codecoachai.ai.api-key**：通过环境变量 `${DEEPSEEK_API_KEY:}` 注入；或直接在配置里填明文（不推荐生产）
- **codecoachai.elasticsearch.uris**：默认 `http://127.0.0.1:9200`

### 环境变量推荐配置

在 Windows PowerShell 中：

```powershell
# 用户级环境变量（持久）
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-xxxxxxxx", "User")
[Environment]::SetEnvironmentVariable("MYSQL_PASSWORD", "your-local-password", "User")
[Environment]::SetEnvironmentVariable("OSS_AK", "LTAI...", "User")
[Environment]::SetEnvironmentVariable("OSS_SK", "xxx", "User")
[Environment]::SetEnvironmentVariable("OSS_STS_ROLE_ARN", "acs:ram::xxx:role/yyy", "User")
```

设置后需要**重启 IDE / 终端**才生效。

## 验证

启动 task 服务，应看到日志：
```
i.n.config.NacosConfigService: get changedGroupKeys: [codecoachai-task-dev.yml...]
```
拉到配置就成功。
