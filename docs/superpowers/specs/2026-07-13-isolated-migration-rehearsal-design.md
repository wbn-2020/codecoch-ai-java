# CodeCoachAI 隔离空库迁移演练规格

## 目标

在测试服务器执行一次完全隔离、可丢弃、可审计的空库迁移演练，证明仓库基线和 V3/V4 migration 可以在 MySQL 8 上从零构建当前结构，不影响正在运行的测试环境。

## 隔离模型

- 独立 Docker network。
- 独立 MySQL 8 容器。
- 独立匿名命名卷。
- 不映射主机端口。
- 不加入现有 Compose 网络。
- 一次性 Maven/Flyway 容器只读挂载发布快照。
- 随机密码保存在权限 0600 的临时文件，不进入命令参数、Git 或日志。

## 基线与版本

- 使用 `sql/init.sql` 初始化 `codecoachai_v1`。
- Flyway baseline 为 `2.999`，执行 V3/V4。
- 重点验证 V4_058-V4_071 连续 14 个版本。
- 不手工执行 V2 migration。

## 证据

演练保留：

- `flyway.log`
- `mysql.log`
- `final-schema.sql`
- `flyway-history.tsv`
- `tables.tsv`
- `columns.tsv`
- `indexes.tsv`
- seed 计数

证据中不得包含数据库密码。

## 通过条件

1. `flyway:migrate` 和 `flyway:validate` 成功。
2. V4_058-V4_071 正好 14 条且 `success=1`。
3. 新增 28 张目标表存在。
4. V4_067 readiness/evidence 字段存在。
5. V4_069 最终 active fingerprint 唯一索引存在。
6. 内置 rubric 1 条、8 类剧本、3 个 ATS 模板存在。
7. 字符集为 utf8mb4，目标表 engine 为 InnoDB。
8. 无重复列、重复索引、checksum 或 collation 错误。

## 失败处理

- 失败时先保存日志和 schema。
- 删除临时容器、网络、数据卷和密码文件。
- 不对现有测试数据库执行 repair 或回滚。
- 根据失败 migration 在本地补测试和修复后重新演练。

## 已知高风险

- V4_054 历史脚本 checksum 和 `utf8mb4_0900_ai_ci` 兼容。
- V4_068 非幂等列和索引创建。
- 存储过程需要相应数据库权限。
- `AFTER existing_column` 依赖仓库基线字段。
- 目标必须为 MySQL 8，不使用 MariaDB/MySQL 5.7。
