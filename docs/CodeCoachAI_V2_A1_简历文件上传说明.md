# CodeCoachAI V2 A1 简历文件上传说明

## 1. 范围

A1 对应正式 V2 PRD 中的 P0 能力：

- `3.1 用户端新增功能`：简历文件上传，支持 PDF、Word、Markdown、TXT。
- `7.1 简历文件上传与解析`：用户上传文件，file-service 保存文件，resume-service 创建解析记录。
- `9 V2 新增数据表`：`file_info`、`resume_analysis_record`。
- `10 V2 新增接口`：`POST /resumes/upload`。

## 2. 已完成能力

- 新增 `codecoachai-file` 模块。
- `resume-service` 暴露用户端接口 `POST /resumes/upload`。
- `resume-service` 接收 `MultipartFile` 后调用 `codecoachai-file` 内部接口保存文件。
- `codecoachai-file` 提供内部接口 `POST /inner/files/upload`。
- 文件保存成功后写入 `file_info`。
- `resume-service` 创建 `resume_analysis_record`，初始状态为 `PENDING`。
- 返回 `fileId`、`analysisRecordId`、`parseStatus`、原始文件名、文件大小和扩展名。

## 3. 本地文件存储配置

配置模板位于：

`docs/nacos/codecoachai-file-dev.yml`

关键配置：

```yaml
codecoachai:
  file:
    storage:
      provider: LOCAL
      root-path: ${CODECOACHAI_FILE_STORAGE_ROOT:./data/uploads}
      max-size-mb: 10
      allowed-extensions:
        - pdf
        - doc
        - docx
        - md
        - txt
```

说明：

- `root-path` 支持环境变量覆盖。
- 默认使用相对路径 `./data/uploads`。
- 不在仓库中写入真实个人绝对路径。
- 不写入真实密钥。

## 4. 文件安全校验

A1 已做以下校验：

- 拒绝空文件。
- 拒绝缺失文件名。
- 只允许扩展名：`pdf`、`doc`、`docx`、`md`、`txt`。
- 默认最大文件大小为 10MB，可通过配置调整。
- 不信任原始文件名作为磁盘路径。
- 存储文件名使用 UUID。
- 保存路径按日期分目录：`resume/yyyy/MM/dd/{uuid}.{ext}`。
- 保存前执行路径 normalize。
- 最终保存路径必须位于配置的 storage root 下。
- 文件保存成功但元数据落库失败时，会尽量删除已保存物理文件。

## 5. 安全边界

- `POST /resumes/upload` 是用户端接口，需要登录。
- `POST /inner/files/upload` 只允许后端服务间调用。
- 内部调用复用现有 `/inner/**` HMAC 签名机制。
- 前端不直接调用 `codecoachai-file`。
- Gateway 现有 `/resumes/**` 路由可覆盖 `/resumes/upload`，不新增公开 file-service 路由。

## 6. A1 不做什么

A1 不包含：

- 简历文本提取。
- AI 简历结构化解析。
- AI 简历优化。
- 解析结果确认。
- 创建完整简历内容。
- 行业场景面试。
- AI 题目生成。
- 学习计划。
- SSE 流式输出。
- MinIO、MQ、ES、Embedding。

## 7. 后续任务

- A2：实现简历解析记录状态查询、重试和状态流转。
- A3：实现文件文本提取和 AI 简历结构化解析。
- A4：实现 AI 简历优化记录和优化建议查询。
