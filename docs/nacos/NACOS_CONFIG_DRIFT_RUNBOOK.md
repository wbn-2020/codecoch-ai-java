# Nacos 配置漂移诊断与安全修复手册

## 1. 适用范围

本手册用于治理以下问题：

- Nacos HTTP 查询内容与应用实际加载内容不一致。
- Nacos Server 持久化文件与客户端 snapshot 哈希不一致。
- `public` 默认命名空间与字面量 `public` 自定义命名空间重名。
- 配置发布接口返回 `true`，但目标应用没有加载预期版本。

本手册不包含自动删除命名空间、配置或客户端 snapshot 的操作。所有发布都必须先记录远端旧内容，再使用 CAS 发布，并从同一 tenant 回读验证。

## 2. 2026-07-15 测试环境根因证据

只读诊断对象：

- Nacos Server：`nacos/nacos-server:v2.3.1`
- Gateway Nacos Client：`3.0.3`
- Nacos 模式：standalone
- 持久化：内嵌 Derby，宿主机 `/opt/codecoachai/data/nacos` 挂载到容器 `/home/nacos/data`
- Gateway Nacos 地址：`nacos:8848`

### 2.1 存在两个不同的 public 命名空间

Nacos namespace API 同时返回：

| 显示名 | namespace ID | type |
|---|---:|---:|
| public | 空字符串 | 0，内建默认命名空间 |
| public | `public` | 2，历史自定义命名空间 |

这两个 namespace 各自包含 13 个同名 Data ID。当前有 11 个 Data ID 内容相同，以下两个已经发生漂移：

| Data ID | 内建 public | 字面量 `public` |
|---|---|---|
| `codecoachai-gateway-dev.yml` | 10,144 bytes，MD5 `875082b824af6a3f18f212936f1ce8ff` | 7,197 bytes，MD5 `dbdf02797bebe51e75cef4131ad58ae0` |
| `codecoachai-resume-dev.yml` | 772 bytes，MD5 `b425aaca18ca74085b4fe98f9d3e9e95` | 393 bytes，MD5 `510d82cd3acab8b4c1faaa1bda3c77b5` |

Gateway 配置的 SHA-256：

- 内建 public：`3a60e912b70a22c4539a94ceb96c0541d8a9b40b779abaf7047be66e1bd18a4d`
- 字面量 `public`：`4e0bebbecc31a4d385dfb372b2ed4d07aa2cdd61b2149e81212612da731427f6`

### 2.2 Server 磁盘文件不是同一配置的随机旧缓存

Nacos Server 磁盘上存在两个独立 tenant 分支：

```text
/home/nacos/data/config-data/DEFAULT_GROUP/codecoachai-gateway-dev.yml
/home/nacos/data/tenant-config-data/public/DEFAULT_GROUP/codecoachai-gateway-dev.yml
```

前者哈希等于内建 public 的新配置，后者哈希等于字面量 `public` 的旧配置。Derby 数据、Server 磁盘 cache、精确 namespace 查询三者一致，因此不是单一配置文件偶发未刷新，而是两个 tenant 中确实保存了两份不同配置。

测试环境没有启用外部 MySQL Nacos 数据源。`SPRING_DATASOURCE_PLATFORM` 未设置，`/home/nacos/data/derby-data` 存在且持续使用，因此本次问题与 MySQL 主从、外部数据库连接或读副本无关。

### 2.3 Gateway 正常联网，但订阅了字面量 public

Gateway 日志显示：

```text
[subscribe] codecoachai-gateway-dev.yml+DEFAULT_GROUP+public
[add-listener] ... tenant=public
```

Nacos Server `config-client-request.log` 记录 Gateway IP 对 `tenant=public` 拉取的 MD5 为：

```text
dbdf02797bebe51e75cef4131ad58ae0
```

Gateway 客户端 snapshot：

```text
/root/nacos/config/Config-fixed-public-nacos_8848_nacos/
  snapshot-tenant/public/DEFAULT_GROUP/codecoachai-gateway-dev.yml
```

该 snapshot 的 MD5/SHA-256 与 Server 的字面量 `public` 分支完全一致。Gateway 同时完成了 gRPC 9848 连接、配置 push 接收和 ACK，所以本次不是网络失败后回退到离线旧 snapshot；客户端从 Server 成功取得的就是字面量 `public` 中的旧版本。

### 2.4 客户端版本变化放大了重名风险

当前依赖解析结果为 `nacos-client 3.0.3`。本地字节码契约验证表明：

- Nacos Client `2.3.1` 在 namespace 未配置时使用空字符串。
- Nacos Client `3.0.3` 在 namespace 为空时将其解析为字面量 `public`。

测试环境的 gRPC 连接、查询和 push 均可工作，因此不能把问题笼统归因于“客户端与 Server 完全不兼容”。实际触发条件是：

1. Server 中历史上存在 ID 为 `public` 的自定义 namespace。
2. Client 3.0.3 默认订阅字面量 `public`。
3. 后续发布只更新了内建 public，即 `tenant=""`。
4. 两个 tenant 从此分叉。

当前证据无法确定历史自定义 `public` namespace 的创建人和精确创建时间。它很可能来自一次显式携带 `tenant=public` 的旧导入或控制台操作，但这部分只能作为推断，不能作为审计定论。

### 2.5 旧发布校验为什么误判成功

2026-07-15 03:33 的发布日志只更新了：

```text
codecoachai-gateway-dev.yml+DEFAULT_GROUP
```

没有更新：

```text
codecoachai-gateway-dev.yml+DEFAULT_GROUP+public
```

此外，Nacos 2.3.1 普通配置 GET 对 `tenant=public` 存在默认命名空间别名行为，可能仍返回 `tenant=""` 的内容；而 `search=accurate` 能区分字面量 `public`。因此仅用普通 GET 回读会看到新配置并误判发布完成。

## 3. 已落地的治理工具

### 3.1 核心守卫

```text
scripts/nacos/nacos_config_guard.py
```

能力：

- 默认 `audit`，只读，不发布。
- 从 namespace API 检测重复 `public`。
- 使用 `search=accurate` 按同一 `tenant + group + dataId` 精确回读。
- 同时比较原始内容、MD5 和 SHA-256。
- 发布前保存目标 tenant 的旧内容与 manifest。
- 已存在配置通过 Nacos 2.3.1 要求的 `casMd5` HTTP Header 执行 CAS，并发修改时停止，不盲目覆盖。
- 发布后轮询同一 tenant，只有内容、MD5、SHA-256 全部一致才成功。
- 远端缺失配置时默认阻断，必须显式使用 `--allow-create-config`。
- 没有 DELETE API，也不会删除 namespace、配置或客户端 snapshot。

目标选择：

| 参数 | 行为 |
|---|---|
| `--target auto` | 正常环境使用内建 public；发现字面量 `public` 时立即阻断 |
| `--target builtin-public` | 只检查或发布 `tenant=""` |
| `--target literal-public` | 只检查或发布已存在的 `tenant="public"` |
| `--target mirror-public` | 同步并分别验证两个 public tenant |
| `--target namespace --namespace-id <id>` | 使用已声明的专用 namespace |

### 3.2 兼容入口

原入口已经改为调用核心守卫：

```text
scripts/nacos/import-nacos-config.ps1
scripts/nacos/import-nacos-config.sh
```

不带写入确认时只执行精确审计。写入必须显式启用，并生成审计目录。

### 3.3 只读运行时诊断

```text
scripts/nacos/diagnose-nacos-runtime.sh
```

该脚本只读取：

- 容器镜像、状态、挂载和非敏感环境变量。
- 两个 public tenant 的精确 API 哈希。
- Server 持久化 cache 哈希。
- 客户端 snapshot 哈希。
- 最近的 Nacos 配置拉取日志。

它不会发布、重启或删除任何内容。

## 4. 安全使用示例

### 4.1 默认审计

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts\nacos\import-nacos-config.ps1
```

若目标存在重复 `public`，预期结果是阻断并提示选择明确 target。

### 4.2 显式审计两个 public tenant

```powershell
python scripts\nacos\nacos_config_guard.py audit `
  --nacos-addr http://127.0.0.1:8848 `
  --target mirror-public `
  --group DEFAULT_GROUP `
  --config-dir docs\nacos
```

`audit` 返回码：

- `0`：全部匹配。
- `2`：存在缺失或漂移。
- `1`：API、namespace、安全契约或参数错误。

### 4.3 受控同步两个 public tenant

先准备独立、不可提交到 Git 的审计目录：

```bash
export NACOS_AUDIT_DIR="/opt/codecoachai/releases/<release>/nacos-audit"
export NACOS_TARGET="mirror-public"
export NACOS_DATA_IDS="codecoachai-gateway-dev.yml,codecoachai-resume-dev.yml"
export CONFIRM_WRITE="true"
bash scripts/nacos/import-nacos-config.sh
```

等价的核心命令：

```bash
python3 scripts/nacos/nacos_config_guard.py publish \
  --nacos-addr http://127.0.0.1:8848 \
  --target mirror-public \
  --group DEFAULT_GROUP \
  --config-dir docs/nacos \
  --data-id codecoachai-gateway-dev.yml \
  --data-id codecoachai-resume-dev.yml \
  --confirm-write \
  --audit-dir "/opt/codecoachai/releases/<release>/nacos-audit"
```

禁止把 Nacos token、密码或审计目录中的旧配置内容写进验收报告或提交到仓库。

## 5. 测试服务器建议动作

以下动作尚未在测试服务器执行；当前只完成了只读诊断和本地脚本实现。

1. 把本次脚本作为独立发布工件上传到测试服务器 release 目录，不覆盖正在运行的 JAR。
2. 先对 Gateway 和 Resume 执行 `audit --target mirror-public`，保存 manifest。
3. 仅对 `codecoachai-gateway-dev.yml`、`codecoachai-resume-dev.yml` 执行一次 `publish --target mirror-public`。
4. 确认两个 tenant 的所有项目均为 `VERIFIED`，重点检查 Gateway 和 Resume。
5. 等待 Nacos push 后运行 `diagnose-nacos-runtime.sh`，确认 Server 两个分支和客户端 snapshot 哈希一致。
6. 新 Gateway JAR 部署前，在容器配置中显式设置 `NACOS_NAMESPACE=public`；未设置时不得发布该 JAR。
7. 若 Server 已一致但客户端 snapshot 仍旧，只重启受影响应用容器以重新订阅；不要手工删除 `/root/nacos/config`。
8. 重新验证 Gateway 动态路由、`resume-claim-audits`、Resume 上传限制和健康状态。
9. 保留 audit 目录、容器日志时间点、配置哈希和回归结果，禁止记录凭据。

测试服务器两个 tenant 之间只有 Gateway 与 Resume 存在内容漂移，因此当前修复应限定这两个 Data ID。仓库其余 YAML 与服务器可能存在 CRLF/LF 等原始字节差异；核心守卫会按原始内容、MD5、SHA-256 严格报告，未审核前不要借本次修复顺带全量重发。

## 6. 长期治理

短期 `mirror-public` 用于消除运行时影响，不代表应永久保留两个重名 namespace。

长期建议：

1. 建立专用 namespace，例如 `codecoachai-test`，不要复用保留显示名 `public`。
2. 所有服务统一通过进程级 `NACOS_NAMESPACE` 指向同一 namespace ID。
3. 在升级或切换窗口验证 Config、Discovery、gRPC push 和动态刷新。
4. 导出并审计字面量 `public` 的全部配置，确认没有客户端订阅后再处理历史 namespace。
5. 删除历史 namespace 必须单独变更、单独备份、单独审批；本仓库脚本不会自动执行该删除。

Gateway 启动配置已经把 Config 与 Discovery 的 namespace 都显式绑定到必填环境变量：

```text
${NACOS_NAMESPACE}
```

不使用 `${NACOS_NAMESPACE:}`，因为 Nacos Client 3.0.3 会继续把空值解析为字面量 `public`；也不把 `public` 写成代码默认值。新 Gateway JAR 部署前必须先完成目标 namespace 同步，并在容器中显式设置 `NACOS_NAMESPACE`。测试环境短期可以在同步两个 public 分支后显式设置为 `public`，长期应切换到专用 namespace ID。

## 7. 验证记录

本地验证不启动后端服务或真实 Nacos：

- Python 守卫单元测试：6 个通过。
- Python 语法编译：通过。
- Bash 两个脚本 `bash -n`：通过。
- PowerShell 导入脚本 AST 解析：通过。
- PowerShell、Bash 导入包装器连接本地假 Nacos 的只读 smoke：通过。
- Gateway 全量测试：17 个通过，其中 namespace 启动契约 3 个。
- 通过 SSH 内存端口转发对测试服执行守卫只读审计：内建 public 的 Gateway、Resume 均 `MATCH`；字面量 `public` 的两项均 `DRIFT`，返回码 `2`，与人工取证一致。
