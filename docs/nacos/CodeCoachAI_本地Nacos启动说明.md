# CodeCoachAI 本地 Nacos 启动说明

## 背景

本地 Nacos 目录通过 `NACOS_HOME` 或 `-NacosHome` 指定，例如：

```text
C:\tools\nacos-server-2.5.2
```

在当前 JDK 17 环境下，直接执行 `startup.cmd -m standalone` 可能失败，典型错误是：

```text
java.base does not "opens java.io" to unnamed module
```

原因是 Nacos 2.5.2 依赖的 Tomcat 反射访问了 JDK 模块内的 `java.io` 类型，需要为本地开发启动补充 `--add-opens`。

## 默认安全检查命令

在后端仓库根目录执行：

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：powershell -ExecutionPolicy Bypass -File scripts/nacos/start-nacos-dev.ps1
```

该命令只检查 Nacos 目录和端口状态。若 `8848` 未监听，脚本会输出 dry-run 提示并退出，不会默认启动服务。

确认只在本机开发环境启动 Nacos 后，再显式传入 `-Start`：

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：powershell -ExecutionPolicy Bypass -File scripts/nacos/start-nacos-dev.ps1 -Start
```

启动并确认写入仓库内 `docs/nacos/*.yml` 配置：

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：powershell -ExecutionPolicy Bypass -File scripts/nacos/start-nacos-dev.ps1 -Start -ImportConfig -ConfirmImport -Namespace "<dedicated-namespace-id>" -AllowCreateConfig
```

如果 Nacos 已经启动，只导入配置也必须显式确认：

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：powershell -ExecutionPolicy Bypass -File scripts/nacos/start-nacos-dev.ps1 -ImportConfig -ConfirmImport -Namespace "<dedicated-namespace-id>" -AllowCreateConfig
```

单独执行导入脚本时，默认也是 dry-run，只列出将导入的 dataId：

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：powershell -ExecutionPolicy Bypass -File scripts/nacos/import-nacos-config.ps1 -Target namespace -Namespace "<dedicated-namespace-id>"
```

确认目标地址、group、namespace 后才允许写入：

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：powershell -ExecutionPolicy Bypass -File scripts/nacos/import-nacos-config.ps1 -Target namespace -Namespace "<dedicated-namespace-id>" -ConfirmWrite -AllowCreateConfig
```

Bash 版本同样默认 dry-run，写入必须显式设置：

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：NACOS_TARGET=namespace NACOS_NAMESPACE="<dedicated-namespace-id>" ALLOW_CREATE_CONFIG=true CONFIRM_WRITE=true bash scripts/nacos/import-nacos-config.sh
```

## Namespace 要求

四个运行服务和配置门禁必须使用同一个已存在的专用 namespace ID：

```text
NACOS_NAMESPACE=<dedicated-namespace-id>
```

- `NACOS_NAMESPACE` 不能为空；Nacos Client 3.0.3 会把空值解释为字面量
  `public`，可能与内建默认 namespace 分叉。
- 不允许使用字面量 `public`。测试环境应先创建专用 namespace，再把六个当前
  Data ID 导入该 namespace。
- Config 和 Discovery 都直接读取同一个必填环境变量，不再维护两套 namespace
  环境变量。
- Compose 的 `nacos-config-init` 使用登录接口获取 access token，并按精确 tenant
  比较文件内容。`NACOS_CONFIG_BOOTSTRAP_ENABLED=true` 只创建缺失配置；已有配置
  发生漂移时会阻断启动，不会自动覆盖。

## 脚本行为

`scripts/nacos/start-nacos-dev.ps1` 会：

- 读取 `NACOS_HOME` 或 `-NacosHome` 指定的 Nacos 目录；未配置时直接退出。
- 如果 `8848` 已监听，直接输出当前进程，不重复启动。
- 如果未监听，默认只提示 dry-run；传入 `-Start` 后才使用 standalone 模式启动 Nacos。
- 启动时注入：

```text
JAVA_TOOL_OPTIONS=--add-opens=java.base/java.io=ALL-UNNAMED
```

- 最多等待 90 秒确认 `8848` 监听。
- 可选执行 `scripts/nacos/import-nacos-config.ps1` 同步 Nacos 配置；必须同时传入
  `-ImportConfig -ConfirmImport -Namespace <id>`。首次创建 Data ID 时再显式增加
  `-AllowCreateConfig`，导入脚本内部也会使用 `-ConfirmWrite`。

## 可配置参数

指定其他 Nacos 目录：

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：powershell -ExecutionPolicy Bypass -File scripts/nacos/start-nacos-dev.ps1 -NacosHome "<NACOS_HOME>"
```

指定端口：

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：powershell -ExecutionPolicy Bypass -File scripts/nacos/start-nacos-dev.ps1 -Port 8848
```

## V2.2 HMAC caller key ring 配置要求

`docs/nacos/codecoachai-common-dev.yml` 必须包含：

```yaml
codecoachai:
  internal:
    auth:
      enabled: true
      secret: ${CODECOACHAI_INTERNAL_OUTBOUND_SECRET:${CODECOACHAI_INTERNAL_SECRET}}
      legacy-shared-secret: ${CODECOACHAI_INTERNAL_LEGACY_SHARED_SECRET:}
      legacy-shared-secret-enabled: false
      legacy-shared-secret-callers: []
      caller-key-rings:
        # The exact caller rings and permissions are service-specific.
        # See codecoachai-core-dev.yml, codecoachai-ai-dev.yml,
        # codecoachai-gateway-dev.yml, and codecoachai-search-dev.yml.
      allowed-clock-skew-seconds: 300
      nonce-ttl-seconds: 300
```

说明：

- 当前四服务稳定拓扑使用强制 HMAC 模式，不是兼容弱校验模式。
- `secret` 是当前服务的出站签名 key；正式联调时每个服务必须使用不同值。
- `legacy-shared-secret` 仅保留为空值的兼容配置位；四服务稳定拓扑必须保持 `legacy-shared-secret-enabled: false`。
- caller key ring 必须配置精确的 HTTP method + `/inner/**` path 权限，以及独立的 `forward-user-context` 权限。
- 每个密钥至少包含 32 字节，caller 密钥必须两两不同且不能复用 legacy shared secret。
- caller key ring 的完整迁移顺序见 `docs/nacos/internal-auth-caller-keyring-migration.md`。
- 本地开发和验收环境也必须通过进程级环境变量或私有 Nacos 配置注入强随机 secret，不再提供公开默认值。
- 不要把 `change-me`、示例值或真实密钥持久化到仓库文档、用户级环境变量或公共 Nacos namespace。
- 生产环境应通过环境变量、私有配置中心或 secret manager 注入真实 secret。
- 如果修改了该配置，需要重新导入 Nacos 配置或等待配置刷新。
- Core 启用 OSS 时必须注入非空的 `OSS_BUCKET`、`OSS_AK`、`OSS_SK`。
- Core 和 AI 启用向量能力时必须注入 `QDRANT_BASE_URL`、`QDRANT_API_KEY`；
  Compose 内部地址使用 `http://qdrant:6333`，不能使用容器内 `127.0.0.1`。

四服务运行时只部署 Gateway、Core、AI、Search。Auth、User、Resume、
Interview、Question、File、System、Task 仍作为 Core 的构建期业务模块，
不再作为独立 Nacos 实例启动。旧 Data ID 可以为回滚保留，但不能作为当前
拓扑的启动清单。

## 启动后检查

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -in 8848,9848,9849 }
```

期望看到：

```text
8848
9848
9849
```

其中 `8848` 是 Nacos HTTP 端口，`9848/9849` 是 Nacos gRPC 相关端口。
