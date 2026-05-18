# Flyway 数据库迁移

> 用 Flyway 管理 `sql/migration/` 下的版本化脚本。
> 已有脚本：V2_001 ~ V3_010（共 21 个）

## 一次性命令

把 V3_006 ~ V3_010 应用到本地数据库：

```cmd
:: 命令行临时设密码（不会写入 shell history）
set MYSQL_PASSWORD=your-local-password
cd C:\my-claude\CodeCoachAI-java

:: 真正执行迁移
mvn flyway:migrate -DskipTests
```

## 常用命令

| 操作 | 命令 |
|------|------|
| 查看当前版本 | `mvn flyway:info` |
| 应用所有待执行的脚本 | `mvn flyway:migrate` |
| 验证已应用脚本是否被改动 | `mvn flyway:validate` |
| 修复元数据表 | `mvn flyway:repair` |
| **危险** 删库（仅 dev） | `mvn flyway:clean -Dflyway.cleanDisabled=false` |

## 基线说明

由于项目已有 V2_001~V3_005 在仓库里且大概率已经在数据库里执行过，配置了：
```
baselineOnMigrate: true
baselineVersion: 2.999
```

含义：首次执行 `flyway:migrate` 时，会把当前 schema 标记为版本 2.999，然后只追加执行 V3_001 及之后的脚本（避免重复执行老脚本）。

如果您的本地数据库还没执行 V2 系列脚本，**请先手动跑完 V2 系列再启用 Flyway**。

## 添加新迁移脚本

命名规则：`V{大版本}_{编号3位}__{描述}.sql`，例如：
- `V3_011__add_user_avatar_url.sql`
- `V4_001__rebuild_index.sql`

注意：
- Flyway 一旦执行过的脚本不能再改！如需修复，新增一个 V 脚本去 ALTER
- 脚本要写成幂等的（用 `CREATE TABLE IF NOT EXISTS` / `IF COLUMN EXISTS` 这类判断）

## 与 Spring Boot 集成（可选）

如果您希望服务启动时自动执行迁移，可在某个服务（推荐 `codecoachai-system`）的 `application.yml` 里启用：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 2.999
```

并把 `sql/migration/*.sql` 同步到 `src/main/resources/db/migration/`。当前默认 `enabled: false`，靠 Maven 插件手动控制更安全。
