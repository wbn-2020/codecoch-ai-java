# CodeCoachAI 本地 Nacos 启动说明

## 背景

当前本机 Nacos 目录：

```text
C:\my-claude\comware\nacos-server-2.5.2
```

在当前 JDK 17 环境下，直接执行 `startup.cmd -m standalone` 可能失败，典型错误是：

```text
java.base does not "opens java.io" to unnamed module
```

原因是 Nacos 2.5.2 依赖的 Tomcat 反射访问了 JDK 模块内的 `java.io` 类型，需要为本地开发启动补充 `--add-opens`。

## 推荐启动命令

在后端仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/nacos/start-nacos-dev.ps1
```

启动并同步仓库内 `docs/nacos/*.yml` 配置：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/nacos/start-nacos-dev.ps1 -ImportConfig
```

## 脚本行为

`scripts/nacos/start-nacos-dev.ps1` 会：

- 默认使用 `C:\my-claude\comware\nacos-server-2.5.2`。
- 如果 `8848` 已监听，直接输出当前进程，不重复启动。
- 如果未监听，使用 standalone 模式启动 Nacos。
- 启动时注入：

```text
JAVA_TOOL_OPTIONS=--add-opens=java.base/java.io=ALL-UNNAMED
```

- 最多等待 90 秒确认 `8848` 监听。
- 可选执行 `scripts/nacos/import-nacos-config.ps1` 同步 Nacos 配置。

## 可配置参数

指定其他 Nacos 目录：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/nacos/start-nacos-dev.ps1 -NacosHome "D:\tools\nacos-server-2.5.2"
```

指定端口：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/nacos/start-nacos-dev.ps1 -Port 8848
```

## V2.1 HMAC 配置要求

`docs/nacos/codecoachai-common-dev.yml` 必须包含：

```yaml
codecoachai:
  internal:
    auth:
      enabled: true
      secret: ${CODECOACHAI_INTERNAL_SECRET:}
      allowed-clock-skew-seconds: 300
      nonce-ttl-seconds: 300
```

说明：

- 当前是强制 HMAC 模式，不是兼容弱校验模式。
- 本地开发和验收环境也必须通过 `CODECOACHAI_INTERNAL_SECRET` 或私有 Nacos 配置注入 secret，不再提供公开默认值。
- 生产环境应通过环境变量或配置中心注入真实 secret。
- 如果修改了该配置，需要重新导入 Nacos 配置或等待配置刷新。

## 启动后检查

```powershell
Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -in 8848,9848,9849 }
```

期望看到：

```text
8848
9848
9849
```

其中 `8848` 是 Nacos HTTP 端口，`9848/9849` 是 Nacos gRPC 相关端口。
