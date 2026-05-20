# V3 阿里云 OSS 与 RocketMQ 后端实现说明

## 结论

V3 后端文件存储以阿里云 OSS 为正式实现，不再补 MinIO。消息队列统一使用 RocketMQ。

## 配置原则

- OSS 密钥只允许通过本地私有配置、环境变量或 Nacos 私有配置注入。
- 仓库内只保留 `codecoachai.oss.*` 示例占位，不提交真实 `accessKeyId`、`accessKeySecret`。
- RocketMQ 基础依赖由 `docker-compose.yml` 提供，业务服务通过 Nacos 或环境变量配置 nameserver。

## 覆盖能力

- 文件服务：本地存储与阿里云 OSS provider 共存，V3 正式 provider 为 `ALIYUN_OSS`。
- 异步任务：RocketMQ 承载简历解析、题目生成、面试报告、搜索同步等任务。
- P2 扩展：个人知识库切片检索与题目语义指纹复用现有 MySQL/ES 搜索基础，不引入新向量数据库。
