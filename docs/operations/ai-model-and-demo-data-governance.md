# AI 模型与历史验收数据治理手册

## 1. 安全边界

- 本手册不随 Flyway 自动删除、停用或切换任何现有数据。
- `default-chat` 默认仅标记为 `PLACEHOLDER`，仍保留原启用与默认状态。
- `ACCEPT_*` 角色默认仅盘点，不修改角色、用户绑定或菜单绑定。
- 任何清理必须先在目标环境完成 dry-run，记录候选主键、引用数量和操作者确认。
- 不允许按模糊关键字直接删除；实际操作必须使用 dry-run 得到的精确主键集合。
- 不允许删除唯一默认模型、仍被用户引用的角色、系统内置角色或用途不明的数据。

## 2. 当前默认模型契约

默认模型的作用域是 `PROVIDER`：

- 同一 `provider` 的有效记录最多有一个 `default_model = 1`。
- 不同 `provider` 可以分别拥有默认模型，因此管理端看到多个“默认”不代表数据冲突。
- 真实聊天路由先读取 `codecoachai.ai.router.default-provider`。
- 选定 provider 后，数据库中启用的默认模型优先；数据库配置不完整时才使用 Nacos/环境配置中的同名 provider。
- `codecoachai.ai.mock-enabled = true` 时，路由层禁止真实 Provider 调用。
- `codecoachai.ai.enabled = false` 时，路由层禁止真实 Provider 调用。

管理查询接口：

```text
GET /admin/ai/runtime-status
GET /admin/ai/routing-status
```

接口只返回配置是否存在、来源、模型标识、治理状态和风险码，不返回 API Key、密文或密钥摘要。

## 3. default-chat dry-run

先确认候选本身：

```sql
SELECT
    id,
    provider,
    model_code,
    model_name,
    default_model,
    enabled,
    governance_status,
    governance_note,
    created_at,
    updated_at
FROM ai_model_config
WHERE provider = 'OPENAI_COMPATIBLE'
  AND model_code = 'default-chat'
  AND deleted = 0;
```

再确认同 provider 是否已有可替代模型：

```sql
SELECT
    id,
    provider,
    model_code,
    default_model,
    enabled,
    governance_status
FROM ai_model_config
WHERE provider = 'OPENAI_COMPATIBLE'
  AND deleted = 0
ORDER BY default_model DESC, sort_order ASC, updated_at DESC;
```

允许进入后续人工处置的必要条件：

1. 候选主键与模型标识已人工核对。
2. 它不是当前唯一可用模型。
3. 如果它是默认模型，已先通过管理端把同 provider 的另一个有效模型设为默认。
4. 新默认模型已经完成手动测活。
5. 操作者提交精确主键、处置原因和独立幂等键。

处置顺序只能是“设置替代默认模型 -> 测活 -> 停用占位模型 -> 观察 -> 经再次确认后软删除”。本手册不提供自动提交脚本。

## 4. ACCEPT_* 历史验收角色 dry-run

角色候选：

```sql
SELECT
    id,
    role_code,
    role_name,
    status,
    deleted,
    created_at,
    updated_at
FROM sys_role
WHERE role_code LIKE 'ACCEPT\_%' ESCAPE '\'
  AND deleted = 0
ORDER BY id;
```

用户引用：

```sql
SELECT
    r.id AS role_id,
    r.role_code,
    COUNT(ur.id) AS active_user_bindings
FROM sys_role r
LEFT JOIN sys_user_role ur
  ON ur.role_id = r.id
 AND ur.deleted = 0
WHERE r.role_code LIKE 'ACCEPT\_%' ESCAPE '\'
  AND r.deleted = 0
GROUP BY r.id, r.role_code
ORDER BY r.id;
```

菜单引用：

```sql
SELECT
    r.id AS role_id,
    r.role_code,
    COUNT(rm.id) AS active_menu_bindings
FROM sys_role r
LEFT JOIN sys_role_menu rm
  ON rm.role_id = r.id
 AND rm.deleted = 0
WHERE r.role_code LIKE 'ACCEPT\_%' ESCAPE '\'
  AND r.deleted = 0
GROUP BY r.id, r.role_code
ORDER BY r.id;
```

角色只有在以下条件全部满足时才可提交清理审批：

1. `role_code` 精确匹配 dry-run 清单中的 `ACCEPT_*`。
2. `active_user_bindings = 0`。
3. 已确认不是部署、自动化验收或回归测试仍在使用的角色。
4. 已记录精确角色 ID、角色编码、创建时间和清理原因。
5. 用户再次明确确认目标 ID 集合后，才允许由系统管理模块执行软删除。

本工作包不执行角色删除，也不跨模块实现角色删除接口。

## 5. 运营诊断字段

AI 调用日志保留脱敏后的技术错误字段，同时新增：

- `sceneLabel`、`sceneCategory`、`sceneRegistered`
- `failureType`、`failureTypeLabel`、`failureHttpStatus`
- `operatorMessage`、`operatorSuggestion`

未登记场景不会被伪装成已识别值，会显示“未登记场景”并保留原始 `scene` 供后续补充字典。
