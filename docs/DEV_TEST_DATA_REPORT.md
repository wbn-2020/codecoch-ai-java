# CodeCoachAI V1 开发联调测试数据完整性复核报告

## 1. 复核结论

本次复核只检查、验证并补齐测试数据 SQL 与测试报告，未修改业务代码、未修改接口路径、未修改前端代码、未新增 V2/V3 能力。

| 项目 | 结论 | 说明 |
|---|---|---|
| `sql/dev_test_data.sql` | PASS | 文件存在，335 行，覆盖 V1 E2E 联调数据 |
| `docs/DEV_TEST_DATA_REPORT.md` | PASS | 文件存在，已补齐本次完整复核结果 |
| SQL 是否可重复执行 | PASS | 使用固定 `100000+` ID、唯一键和 `ON DUPLICATE KEY UPDATE` |
| 是否存在危险 SQL | PASS | 未发现可执行的 `DROP`、`TRUNCATE`、`DELETE FROM`、`ALTER TABLE`、`CREATE TABLE`、`CREATE DATABASE` |
| 是否需要补充导入 | PASS | 不需要，数据库实际数据已满足预期 |
| Gateway 最小链路 | PASS | 登录、用户端、管理端、权限校验均通过 |
| 是否可以进入前端点击联调 | PASS | 可以切换到 `CodeCoachAI-vue` 做浏览器点击测试 |

## 2. 本次检查的文件

| 文件 | 状态 | 行数 | 说明 |
|---|---|---:|---|
| `sql/dev_test_data.sql` | EXISTS | 335 | V1 E2E 本地联调测试数据 SQL |
| `docs/DEV_TEST_DATA_REPORT.md` | EXISTS | 已更新 | 测试数据完整性复核报告 |

## 3. 是否发生业务代码变更

本次复核未修改业务代码，没有 `.java` 文件变更。

当前工作区仍存在此前 Nacos 配置迁移留下的非本次测试数据文件变更，包括多个服务的 `pom.xml`、`application.yml`、`docs/nacos/*`、`scripts/nacos/*`。这些不是本次复核新增的业务代码变更，本次没有自动修改它们。

当前复核涉及的测试数据文件：

| 文件 | 状态 |
|---|---|
| `sql/dev_test_data.sql` | 已修改/已存在 |
| `docs/DEV_TEST_DATA_REPORT.md` | 已更新 |

## 4. SQL 安全性检查结果

检查方式：忽略注释行后扫描 `sql/dev_test_data.sql` 中的危险语句。

| 检查项 | 结果 | 说明 |
|---|---|---|
| `DROP` | PASS | 未发现可执行语句 |
| `TRUNCATE` | PASS | 未发现可执行语句 |
| `DELETE FROM` | PASS | 未发现可执行语句 |
| `ALTER TABLE` | PASS | 未发现可执行语句 |
| `CREATE TABLE` | PASS | 未发现可执行语句 |
| `CREATE DATABASE` | PASS | 未发现可执行语句 |
| 清空数据库/重建数据库 | PASS | 未发现 |

说明：SQL 注释中有“不使用 DROP/TRUNCATE/DELETE”的说明文本，不属于可执行危险语句。

## 5. SQL 完整性检查结果

| 检查项 | 是否存在 | 数量 | 备注 |
|---|---|---:|---|
| `e2e_user` 测试账号 | PASS | 1 | 普通用户 |
| `e2e_admin` 测试账号 | PASS | 1 | 管理员 |
| `e2e_user` 绑定 USER | PASS | 1 | 通过 `sys_user_role` |
| `e2e_admin` 绑定 ADMIN | PASS | 1 | 通过 `sys_user_role` |
| E2E_TEST 分类 | PASS | 5 | Java 基础、MySQL、Redis、SpringBoot、项目场景题 |
| E2E_TEST 标签 | PASS | 5 | 集合、索引、缓存、事务、项目深挖 |
| E2E_TEST 问题组 | PASS | 5 | 覆盖 HashMap、索引、缓存、事务、项目缓存 |
| E2E_TEST 题目 | PASS | 10 | 满足至少 8 道要求 |
| 题目与标签关系 | PASS | 12 | 覆盖多标签场景 |
| 题目与问题组关系 | PASS | 10 | 每道题关联 `group_id` |
| 高频题 | PASS | 3 | 标题包含 `E2E_TEST_高频_` |
| 难度 EASY | PASS | 2 | 覆盖简单 |
| 难度 MEDIUM | PASS | 5 | 覆盖中等 |
| 难度 HARD | PASS | 3 | 覆盖困难 |
| 答题记录 | PASS | 4 | 大于等于 2 |
| 错题/不会记录 | PASS | 2 | `wrong=1` / `NOT_MASTERED` |
| 收藏记录 | PASS | 2 | `favorite=1` |
| 已掌握记录 | PASS | 2 | `mastery_status=MASTERED` |
| e2e_user 简历 | PASS | 1 | 默认简历 |
| 默认简历 | PASS | 1 | `is_default=1` |
| 项目经历 | PASS | 2 | 支撑 AI 项目深挖 |
| 八股文提问模板 | PASS | 1 | `E2E_TEST_八股文提问模板` |
| 项目深挖提问模板 | PASS | 1 | `E2E_TEST_项目深挖提问模板` |
| 回答评分模板 | PASS | 1 | `E2E_TEST_回答评分模板` |
| 动态追问模板 | PASS | 1 | `E2E_TEST_动态追问模板` |
| 面试报告生成模板 | PASS | 1 | `E2E_TEST_面试报告生成模板` |
| AI 调用日志 | PASS | 2 | 提问生成、回答评分 |
| `INTERVIEW_QUESTION_GENERATE` | PASS | 1 | AI 日志 |
| `INTERVIEW_ANSWER_EVALUATE` | PASS | 1 | AI 日志 |
| interview_session | PASS | 1 | 已完成历史面试 |
| interview_stage | PASS | 4 | Java 基础、MySQL、Redis、项目深挖 |
| interview_message | PASS | 13 | 至少 4 轮问答 |
| AI 主问题 | PASS | 4 | `AI/QUESTION` |
| 用户回答 | PASS | 4 | `USER/ANSWER` |
| 追问关系 | PASS | 1 | `FOLLOW_UP` |
| interview_report | PASS | 1 | `GENERATED` |
| 报告总分 | PASS | 82 | 可展示 |
| system_config | PASS | 5 | V1 基础配置齐全 |

## 6. 数据库实际数据验证结果

数据库：`codecoachai_v1`

本次复核没有重复导入 SQL，先查询数据库确认数据已存在。

| 检查项 | 结果 | 实际数量 |
|---|---|---:|
| `e2e_user` 存在 | PASS | 1 |
| `e2e_admin` 存在 | PASS | 1 |
| `e2e_user` 角色 USER 正确 | PASS | 1 |
| `e2e_admin` 角色 ADMIN 正确 | PASS | 1 |
| E2E_TEST 分类数量 | PASS | 5 |
| E2E_TEST 标签数量 | PASS | 5 |
| E2E_TEST 问题组数量 | PASS | 5 |
| E2E_TEST 题目数量 | PASS | 10 |
| 题目与标签关系数量 | PASS | 12 |
| 高频题数量 | PASS | 3 |
| EASY 难度题数量 | PASS | 2 |
| MEDIUM 难度题数量 | PASS | 5 |
| HARD 难度题数量 | PASS | 3 |
| e2e_user 答题记录数量 | PASS | 4 |
| e2e_user 错题数量 | PASS | 2 |
| e2e_user 收藏数量 | PASS | 2 |
| e2e_user 已掌握数量 | PASS | 2 |
| e2e_user 简历数量 | PASS | 1 |
| e2e_user 默认简历数量 | PASS | 1 |
| e2e_user 项目经历数量 | PASS | 2 |
| Prompt 模板数量 | PASS | 5 |
| AI 提问生成日志数量 | PASS | 1 |
| AI 回答评分日志数量 | PASS | 1 |
| 面试历史数量 | PASS | 1 |
| 面试阶段数量 | PASS | 4 |
| 面试消息数量 | PASS | 13 |
| AI 主问题消息数量 | PASS | 4 |
| 用户回答消息数量 | PASS | 4 |
| 追问消息数量 | PASS | 1 |
| 面试报告数量 | PASS | 1 |
| system_config V1 配置数量 | PASS | 5 |

## 7. 登录与权限验证结果

Gateway 地址：`http://localhost:8080`

| 请求路径 | HTTP 状态码 | 业务 code | 关键响应字段 | 是否通过 |
|---|---:|---:|---|---|
| `POST /auth/login` (`e2e_user`) | 200 | 0 | `roles=USER` | PASS |
| `POST /auth/login` (`e2e_admin`) | 200 | 0 | `roles=ADMIN` | PASS |
| `GET /auth/current-user` (`e2e_user`) | 200 | 0 | `username=e2e_user` | PASS |
| `GET /auth/current-user` (`e2e_admin`) | 200 | 0 | `username=e2e_admin` | PASS |
| `GET /admin/users?pageNo=1&pageSize=10` (`e2e_user`) | 200 | 41003 | `forbidden` | PASS |
| `GET /admin/users?pageNo=1&pageSize=10&keyword=E2E_TEST` (`e2e_admin`) | 200 | 0 | `total=2` | PASS |

## 8. 用户端接口验证结果

验证要求：全部通过 Gateway `localhost:8080`，未使用旧接口 `POST /questions/answer` 或 `GET /questions/wrongs`。

| 请求路径 | HTTP 状态码 | 业务 code | 关键响应字段 | 是否通过 |
|---|---:|---:|---|---|
| `GET /questions?pageNo=1&pageSize=10&keyword=E2E_TEST` | 200 | 0 | `total=10` | PASS |
| `GET /questions/wrong-records?pageNo=1&pageSize=10` | 200 | 0 | `total=2` | PASS |
| `GET /questions/favorites?pageNo=1&pageSize=10` | 200 | 0 | `total=2` | PASS |
| `GET /resumes` | 200 | 0 | `count=1` | PASS |
| `GET /interviews?pageNo=1&pageSize=10` | 200 | 0 | `total=1` | PASS |
| `GET /interviews/100000` | 200 | 0 | `title=E2E_TEST_综合模拟面试` | PASS |
| `GET /interviews/100000/report` | 200 | 0 | `totalScore=82` | PASS |

## 9. 管理端接口验证结果

验证账号：`e2e_admin`

| 请求路径 | HTTP 状态码 | 业务 code | 关键响应字段 | 是否通过 |
|---|---:|---:|---|---|
| `GET /admin/users?pageNo=1&pageSize=10&keyword=E2E_TEST` | 200 | 0 | `total=2` | PASS |
| `GET /admin/questions?pageNo=1&pageSize=10&keyword=E2E_TEST` | 200 | 0 | `total=10` | PASS |
| `GET /admin/question-categories` | 200 | 0 | `count=19` | PASS |
| `GET /admin/question-tags` | 200 | 0 | `count=17` | PASS |
| `GET /admin/question-groups` | 200 | 0 | `count=15` | PASS |
| `GET /admin/ai/prompts?pageNo=1&pageSize=10` | 200 | 0 | `total=14` | PASS |
| `GET /admin/ai/call-logs?pageNo=1&pageSize=10` | 200 | 0 | `total=5` | PASS |
| `GET /admin/configs` | 200 | 0 | `count=12` | PASS |

## 10. 仍需人工确认的问题

| 问题 | 影响 | 建议 |
|---|---|---|
| 当前 `resume` 表没有独立 `resumeName/targetPosition/skillStack/workExperience/educationExperience` 字段 | 前端若直接期待这些字段，需要做 VO 映射 | V1 前端先按当前 `title/summary` 展示，后续再评估接口 VO 扩展 |
| 当前 `resume_project` 表没有独立 `projectTime/coreFeature/difficulty/optimization/supplement` 字段 | 项目经历详情只能从 `description/highlights` 读取综合文本 | V1 联调用现有字段，后续如有 UI 强字段需求再扩展 |
| `ai_call_log` 表没有独立 `user_id` 字段 | 管理端 AI 日志无法按用户字段直接筛选 | 当前已把 `userId` 写入 `request_body`，后续可评估表结构扩展 |
| 工作区存在此前 Nacos 配置迁移文件变更 | 与本次测试数据复核无关 | 提交前统一 review Nacos 迁移改动 |

## 11. 是否可以进入 CodeCoachAI-vue 前端浏览器点击联调

可以。后端本地 E2E 测试数据完整，数据库实际数据已验证，Gateway 登录、权限、用户端接口和管理端接口均已通过。
