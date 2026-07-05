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
勿执行示例：powershell -ExecutionPolicy Bypass -File scripts/nacos/start-nacos-dev.ps1 -Start -ImportConfig -ConfirmImport
```

如果 Nacos 已经启动，只导入配置也必须显式确认：

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：powershell -ExecutionPolicy Bypass -File scripts/nacos/start-nacos-dev.ps1 -ImportConfig -ConfirmImport
```

单独执行导入脚本时，默认也是 dry-run，只列出将导入的 dataId：

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：powershell -ExecutionPolicy Bypass -File scripts/nacos/import-nacos-config.ps1
```

确认目标地址、group、namespace 后才允许写入：

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：powershell -ExecutionPolicy Bypass -File scripts/nacos/import-nacos-config.ps1 -ConfirmWrite
```

Bash 版本同样默认 dry-run，写入必须显式设置：

运行期/人工确认，静态审查勿执行：

```text
勿执行示例：CONFIRM_WRITE=true bash scripts/nacos/import-nacos-config.sh
```

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
- 可选执行 `scripts/nacos/import-nacos-config.ps1` 同步 Nacos 配置；必须同时传入 `-ImportConfig -ConfirmImport`，导入脚本内部也会使用 `-ConfirmWrite`。

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

## V2.1 HMAC 配置要求

`docs/nacos/codecoachai-common-dev.yml` 必须包含：

```yaml
codecoachai:
  internal:
    auth:
      enabled: true
      secret: ${CODECOACHAI_INTERNAL_SECRET}
      allowed-clock-skew-seconds: 300
      nonce-ttl-seconds: 300
```

说明：

- 当前是强制 HMAC 模式，不是兼容弱校验模式。
- 本地开发和验收环境也必须通过进程级 `CODECOACHAI_INTERNAL_SECRET` 或私有 Nacos 配置注入强随机 secret，不再提供公开默认值。
- 不要把 `change-me`、示例值或真实密钥持久化到仓库文档、用户级环境变量或公共 Nacos namespace。
- 生产环境应通过环境变量、私有配置中心或 secret manager 注入真实 secret。
- 如果修改了该配置，需要重新导入 Nacos 配置或等待配置刷新。

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
