# 隔离空库迁移演练

## 用途

`scripts/rehearse-migrations.sh` 在独立 Docker network、MySQL 8 容器和命名卷中，从 `sql/init.sql` 基线执行仓库根目录的 Flyway migration。脚本不映射主机端口、不加入现有 Compose 网络，也不访问现有 CodeCoachAI 数据库。

`sql/init.sql` 必须包含完整 V2 基线，包括 `file_info`、`resume_analysis_record` 和最终的 `study_task.planned_date`；Flyway 再以 `2.999` 为 baseline 执行全部 V3/V4 migration。readiness 同时验证这些对象，避免基线不完整时启动 Flyway。

## 执行

```bash
chmod +x scripts/rehearse-migrations.sh
scripts/rehearse-migrations.sh \
  --repo /opt/codecoachai/releases/<release>/source/codecoch-ai-java \
  --evidence-dir /opt/codecoachai/releases/<release>/migration-evidence
```

仅做 dry validation：

```bash
scripts/rehearse-migrations.sh \
  --repo /opt/codecoachai/releases/<release>/source/codecoch-ai-java \
  --evidence-dir /opt/codecoachai/releases/<release>/migration-evidence \
  --validate-only
```

`--validate-only` 只解析并验证参数、必需命令、最低空间参数和仓库输入文件；它不会连接 Docker daemon，不创建证据/secret 文件，也不创建 Docker 资源。该模式适合发布包上传后、正式演练前做快速完整性检查。

可通过 `MIGRATION_REHEARSAL_MIN_FREE_KB` 调整主机证据盘和 Docker 存储的最低可用空间，默认要求 2 GiB。测试服务器必须能够从镜像仓库拉取以下镜像：

- `mysql:8.0`
- `maven:3.9.9-eclipse-temurin-17`

如果测试服务器已按发布流程预加载并校验镜像，但访问镜像仓库被外部网络阻断，可显式启用离线门禁：

```bash
MIGRATION_REHEARSAL_PRELOADED_IMAGES=1 \
MYSQL_IMAGE=<preloaded-mysql-image> \
MAVEN_IMAGE=<preloaded-maven-image> \
scripts/rehearse-migrations.sh \
  --repo /opt/codecoachai/releases/<release>/source/codecoch-ai-java \
  --evidence-dir /opt/codecoachai/releases/<release>/migration-evidence
```

该模式不会静默降级：两个镜像必须已存在，脚本会逐一验证完整的内容寻址 `sha256:<64 hex>` image ID；若镜像同时带有 `RepoDigests`，仍会逐行执行原有严格校验。`image-digests.tsv` 会记录镜像来源、RepoDigests 和 image ID。默认值仍为 `0`，正常路径继续强制执行 registry pull 和 RepoDigests 硬门禁。

## 预检

脚本在创建数据库前执行以下预检：

- 必需命令、`pom.xml`、`sql/init.sql`、迁移目录和验证 SQL 存在。
- 创建证据目录后立即开启 `preflight.log`（stdout/命令轨迹）和 `error.log`（stderr/失败原因）追加写入；该捕获早于 `docker info`、镜像 pull、digest inspect 和依赖解析，因此早期失败也会留下证据。
- Docker daemon 可访问。
- 证据文件系统与 Docker 存储空间满足阈值。
- MySQL/Maven 镜像必须成功执行 `docker pull`，随后通过 `docker image inspect` 读取 `RepoDigests`。
- `RepoDigests` 为空、`null`、`[]`，或任一 digest 行为空/不是精确的 `<repository>@sha256:<64 hex>` 时触发硬门禁；多行结果逐行校验，合法行不能掩盖空行或非法行。摘要使用行尾锚定，65 位及 64 位后追加任何 hex 都会被拒绝。
- 只有两个镜像都通过 digest 门禁后，才写入镜像 digest 和 image ID。
- Maven 容器可运行，且根 POM 能解析 Flyway Maven plugin 依赖。

MySQL readiness 只通过容器内 `127.0.0.1:3306` 的 TCP 连接判断，并要求基线末尾 sentinel table `system_config` 及其最终 seed `ai.timeout.seconds` 已存在，避免在 `init.sql` 尚未完成时启动 Flyway。

## 隔离与凭据

每次运行使用 OpenSSL 随机 owner token 生成不可预测的资源名。容器、network 和 volume 都带有 `com.codecoachai.migration-rehearsal.owner` ownership label；清理前逐项读取 label，只有值与本次 token 完全一致时才删除。

MySQL root secret 位于权限为 `0700` 的独立临时目录，密码文件权限为 `0600`。它不在证据目录、命令参数、Git 或日志中；清理阶段会删除整个 secret 临时目录。

## Flyway 范围

Maven 使用 `-N`，只执行仓库根 POM，并显式固定：

```text
-Dflyway.locations=filesystem:/workspace/sql/migration
```

这样不会递归进入业务模块，也不会因模块工作目录变化而读取错误的 migration location。

## 精确通过条件

- `flyway:migrate` 和 `flyway:validate` 成功。
- V4_058 到 V4_071 的 14 个指定版本逐一存在且 `success=1`，缺失数为 0。
- 28 张目标表存在。
- V4_067 的 readiness/evidence 列按 ordinal、data/column type、nullable、default、字符长度、charset、表默认 collation、extra、generation expression 和 comment 精确匹配；evidence project 索引名、唯一性和列序也必须精确匹配。
- V4_069 的三个 active-only 唯一索引名、唯一性和列序精确匹配。
- V4_070 的 `raw_data_json`、`duplicate_candidates_json` 均为 nullable `MEDIUMTEXT`。
- V4_071 rubric/scenario JSON 合法，完整内容 SHA-256、场景代码、中文名称和中文描述精确匹配。
- `ATS_SINGLE_COLUMN`、`ATS_COMPACT`、`ATS_PROJECT_FOCUS` 三个指定模板均满足 `status=ACTIVE`、`template_version=1`、`deleted=0`。

## 证据

成功或失败都会保留随机命名的证据目录。主要文件包括：

- `preflight.log`
- `error.log`
- `docker-info.txt`
- `image-digests.tsv`
- `mysql-version.txt`
- `baseline-sentinel.tsv`
- `flyway.log`
- `flyway-container.log`
- `mysql.log`
- `flyway-history.tsv`
- `tables.tsv`
- `columns.tsv`
- `indexes.tsv`
- `verification.tsv`
- `final-schema.sql`
- `exit-code`

`image-digests.tsv` 记录镜像来源、RepoDigests 和经过严格格式校验的 image ID；正常 registry pull 路径仍要求非空 `RepoDigests`，显式预加载模式则允许本地构建镜像以 `[]` 表示无仓库 digest。`mysql-version.txt` 记录容器内实际 MySQL 版本。

## 失败处理

失败时 trap 会先尝试导出当前可读取的 schema 到 `final-schema.sql`，再清理 Flyway 容器、MySQL 容器、network 和 volume。若 ownership 检查失败，脚本拒绝删除该资源并写入 `cleanup-warnings.log`；证据目录始终保留，便于定位失败 migration。

禁止对现有测试数据库执行 `repair`、`clean`、回滚或本脚本。修复 migration 后，应使用新的空库资源重新演练。
