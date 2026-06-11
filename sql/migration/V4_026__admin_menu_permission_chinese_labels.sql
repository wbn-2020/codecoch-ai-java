-- V4_026: Chinese display labels for existing admin menu and permission resources.
-- Idempotent: updates only display names and remarks; permission codes and bindings stay unchanged.

UPDATE `sys_menu`
SET `menu_name` = '管理后台',
    `remark` = '管理后台根入口'
WHERE `permission_code` = 'admin:v3'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '运营首页',
    `remark` = '查看后台运营总览、待处理事项和系统状态'
WHERE `permission_code` = 'admin:system:overview'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '文件治理',
    `remark` = '查看和治理用户上传文件'
WHERE `permission_code` = 'admin:file:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '文件下载',
    `remark` = '从后台下载用户上传文件'
WHERE `permission_code` = 'admin:file:download'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '用户管理',
    `remark` = '查看用户列表和账号状态'
WHERE `permission_code` = 'admin:user:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '用户写入',
    `remark` = '启用、禁用或更新用户账号'
WHERE `permission_code` = 'admin:user:write'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '用户密码重置',
    `remark` = '由后台为用户发起密码重置'
WHERE `permission_code` = 'admin:user:password:reset'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '角色管理',
    `remark` = '查看角色列表和角色状态'
WHERE `permission_code` = 'admin:role:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '角色写入',
    `remark` = '创建、更新、禁用或删除角色'
WHERE `permission_code` = 'admin:role:write'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '角色授权',
    `remark` = '分配角色和角色菜单权限'
WHERE `permission_code` = 'admin:role:assign'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '题目管理',
    `remark` = '查看题库题目列表'
WHERE `permission_code` = 'admin:question:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = 'AI 题目生成',
    `remark` = '使用 AI 生成候选题目'
WHERE `permission_code` = 'admin:question:generate'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '题目审核',
    `remark` = '审核 AI 生成或待治理题目'
WHERE `permission_code` = 'admin:question:review'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '重复题治理',
    `remark` = '查看和处理重复题检测结果'
WHERE `permission_code` = 'admin:question:dedupe'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '题目关系',
    `remark` = '管理题目之间的关联关系'
WHERE `permission_code` = 'admin:question:relation'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '分类管理',
    `remark` = '管理题目分类'
WHERE `permission_code` = 'admin:question:category'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '标签管理',
    `remark` = '管理题目标签'
WHERE `permission_code` = 'admin:question:tag'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '题组管理',
    `remark` = '管理题目分组'
WHERE `permission_code` = 'admin:question:group'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '题目写入',
    `remark` = '创建、更新、删除或调整题目状态'
WHERE `permission_code` = 'admin:question:write'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '题目导入',
    `remark` = '导入题库数据'
WHERE `permission_code` = 'admin:question:import'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '题目导出',
    `remark` = '导出题库数据'
WHERE `permission_code` = 'admin:question:export'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '题目向量重建',
    `remark` = '重建或重试题目向量索引'
WHERE `permission_code` = 'admin:question:embedding:rebuild'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '行业模板',
    `remark` = '管理行业模板和岗位画像配置'
WHERE `permission_code` = 'admin:industry-template:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '行业模板写入',
    `remark` = '创建、更新、启用、禁用或删除行业模板'
WHERE `permission_code` = 'admin:industry-template:write'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '提示词模板',
    `remark` = '查看提示词模板和版本'
WHERE `permission_code` = 'admin:ai:prompt:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '提示词模板写入',
    `remark` = '创建、更新、删除或启用提示词模板'
WHERE `permission_code` = 'admin:ai:prompt:write'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '提示词版本发布',
    `remark` = '启用、回滚或停用提示词版本'
WHERE `permission_code` = 'admin:ai:prompt:publish'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '提示词测试',
    `remark` = '运行提示词版本测试'
WHERE `permission_code` = 'admin:ai:prompt:test'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '提示词回归',
    `remark` = '查看提示词回归用例和执行结果'
WHERE `permission_code` = 'admin:agent:prompt-regression:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '提示词回归写入',
    `remark` = '创建或更新提示词回归用例'
WHERE `permission_code` = 'admin:agent:prompt-regression:write'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '提示词回归执行',
    `remark` = '执行提示词回归用例'
WHERE `permission_code` = 'admin:agent:prompt-regression:run'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = 'AI 调用记录',
    `remark` = '查看 AI 调用记录和执行状态'
WHERE `permission_code` = 'admin:ai:log:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = 'AI 调用敏感内容查看',
    `remark` = '查看 AI 提示词、请求和响应中的敏感完整内容'
WHERE `permission_code` = 'admin:ai:log:raw:view'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = 'AI 模型配置',
    `remark` = '查看 AI 模型配置'
WHERE `permission_code` = 'admin:ai:model:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = 'AI 模型配置写入',
    `remark` = '创建、更新或删除 AI 模型配置'
WHERE `permission_code` = 'admin:ai:model:write'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = 'AI 模型发布',
    `remark` = '设置默认模型或调整模型运行状态'
WHERE `permission_code` = 'admin:ai:model:publish'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = 'AI 运营看板',
    `remark` = '查看 AI 调用、质量和成本运营指标'
WHERE `permission_code` = 'admin:analytics:ai'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '生成效果分析',
    `remark` = '查看智能教练生成效果和任务表现'
WHERE `permission_code` = 'admin:analytics:agent'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '指标字典写入',
    `remark` = '创建或更新运营指标定义'
WHERE `permission_code` = 'admin:analytics:metric:write'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '聚合任务执行',
    `remark` = '重新执行运营聚合任务和智能教练计划任务'
WHERE `permission_code` = 'admin:analytics:job:run'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '生成运行记录',
    `remark` = '查看智能教练运行记录'
WHERE `permission_code` = 'admin:agent:run:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '生成任务诊断',
    `remark` = '查看智能教练任务记录和诊断信息'
WHERE `permission_code` = 'admin:agent:task:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '菜单权限',
    `remark` = '查看菜单和权限定义'
WHERE `permission_code` = 'admin:menu:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '菜单权限写入',
    `remark` = '创建、更新或删除菜单权限定义'
WHERE `permission_code` = 'admin:menu:write'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '通知管理',
    `remark` = '查看站内通知和发送记录'
WHERE `permission_code` = 'admin:notice:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '通知写入',
    `remark` = '发送、广播或删除后台通知'
WHERE `permission_code` = 'admin:notice:write'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '公告列表',
    `remark` = '查看系统公告'
WHERE `permission_code` = 'admin:announcement:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '公告写入',
    `remark` = '创建、更新或删除系统公告'
WHERE `permission_code` = 'admin:announcement:write'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '公告发布',
    `remark` = '发布或下线系统公告'
WHERE `permission_code` = 'admin:announcement:publish'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '操作日志',
    `remark` = '查看后台操作审计日志'
WHERE `permission_code` = 'admin:audit:operation-log'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '登录日志',
    `remark` = '查看登录审计日志'
WHERE `permission_code` = 'admin:audit:login-log'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '慢 SQL 查询',
    `remark` = '查看慢 SQL 观测记录'
WHERE `permission_code` = 'admin:audit:slow-sql-log'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '面试记录',
    `remark` = '查看面试记录'
WHERE `permission_code` = 'admin:interview:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '面试报告',
    `remark` = '查看面试报告'
WHERE `permission_code` = 'admin:interview:report'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '任务中心',
    `remark` = '查看异步任务中心'
WHERE `permission_code` = 'admin:task:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '失败任务重试',
    `remark` = '重试失败的异步任务并恢复异常任务'
WHERE `permission_code` = 'admin:task:retry'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '系统配置',
    `remark` = '查看系统配置'
WHERE `permission_code` = 'admin:system:config:list'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '系统配置写入',
    `remark` = '创建、更新、删除或切换系统配置'
WHERE `permission_code` = 'admin:system:config:write'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '敏感配置查看',
    `remark` = '查看敏感系统配置的完整值'
WHERE `permission_code` = 'admin:system:config:raw:view'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '简历跨用户检索',
    `remark` = '后台跨用户检索简历'
WHERE `permission_code` = 'admin:search:resume'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '面试跨用户检索',
    `remark` = '后台跨用户检索面试记录'
WHERE `permission_code` = 'admin:search:interview'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '检索索引重建',
    `remark` = '重建检索索引'
WHERE `permission_code` = 'admin:search:index:rebuild'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `menu_name` = '运营统计查看',
    `remark` = '查看跨用户运营统计数据'
WHERE `permission_code` = 'admin:stats:list'
  AND `deleted` = 0;
