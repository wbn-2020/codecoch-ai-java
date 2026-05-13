# CodeCoachAI Java Stage 2 Report

## 1. Task Goal

Continue V1 backend development without breaking Stage 1. The goal is to push the V1 core backend loop forward:

Register/login -> maintain resume -> query questions -> create interview -> AI mock question -> answer -> AI mock evaluation/follow-up -> finish -> report.

## 2. Completed Modules

| Module | Status |
| --- | --- |
| question-service | Implemented V1 basic question bank APIs, admin metadata APIs, user answer/favorite/wrong-record APIs, and internal question selection. |
| resume-service | Implemented manual resume and project experience APIs plus internal resume query APIs. |
| ai-service | Implemented mock internal AI interview APIs, prompt management, and AI call log persistence. |
| interview-service | Implemented V1 basic interview state machine and OpenFeign orchestration with question/resume/ai. |
| system-service | Implemented lightweight system config APIs and simplified admin overview. |
| sql/init.sql | Extended with V1 minimal tables and seed data. |
| docs/STAGE2_API_TEST.md | Added recommended manual API test flow. |

## 3. Key Files

### question-service

- `Question.java`, `QuestionCategory.java`, `QuestionTag.java`, `QuestionTagRelation.java`, `QuestionGroup.java`, `UserQuestionRecord.java`
- `QuestionMapper.java`, `QuestionCategoryMapper.java`, `QuestionTagMapper.java`, `QuestionTagRelationMapper.java`, `QuestionGroupMapper.java`, `UserQuestionRecordMapper.java`
- `QuestionService.java`, `QuestionServiceImpl.java`
- `QuestionMetadataService.java`, `QuestionMetadataServiceImpl.java`
- `QuestionController.java`, `AdminQuestionController.java`, `AdminQuestionMetadataController.java`, `InnerQuestionController.java`

### resume-service

- `Resume.java`, `ResumeProject.java`
- `ResumeMapper.java`, `ResumeProjectMapper.java`
- `ResumeService.java`, `ResumeServiceImpl.java`
- `ResumeController.java`, `InnerResumeController.java`

### ai-service

- `PromptTemplate.java`, `AiCallLog.java`
- `PromptTemplateMapper.java`, `AiCallLogMapper.java`
- `AiService.java`, `AiServiceImpl.java`
- `PromptTemplateService.java`, `PromptTemplateServiceImpl.java`
- `InnerAiController.java`, `AdminAiController.java`

### interview-service

- `InterviewSession.java`, `InterviewStage.java`, `InterviewMessage.java`, `InterviewReport.java`
- `InterviewStatusEnum.java`, `ReportStatusEnum.java`, `NextActionEnum.java`, `InterviewModeEnum.java`
- `QuestionFeignClient.java`, `ResumeFeignClient.java`, `AiFeignClient.java`
- `InterviewService.java`, `InterviewServiceImpl.java`
- `InterviewController.java`

### system-service

- `SystemConfig.java`
- `SystemConfigMapper.java`
- `SystemConfigService.java`, `SystemConfigServiceImpl.java`
- `SystemConfigController.java`

## 4. Implemented Interfaces

### question-service

| Method | Path |
| --- | --- |
| GET | `/questions` |
| GET | `/questions/{id}` |
| POST | `/questions/{id}/answers` |
| POST | `/questions/{id}/favorite` |
| DELETE | `/questions/{id}/favorite` |
| GET | `/questions/favorites` |
| GET | `/questions/wrong-records` |
| PUT | `/questions/{id}/mastery` |
| GET | `/admin/questions` |
| POST | `/admin/questions` |
| PUT | `/admin/questions/{id}` |
| DELETE | `/admin/questions/{id}` |
| PUT | `/admin/questions/{id}/status` |
| GET/POST/PUT/DELETE | `/admin/question-categories/**` |
| GET/POST/PUT/DELETE | `/admin/question-tags/**` |
| GET/POST/PUT/DELETE | `/admin/question-groups/**` |
| POST | `/inner/questions/select` |
| POST | `/inner/questions/pick-for-interview` |
| GET | `/inner/questions/{id}` |
| GET | `/inner/questions/recommend` |
| POST | `/inner/questions/recommend-for-report` |

### resume-service

| Method | Path |
| --- | --- |
| GET | `/resumes` |
| POST | `/resumes` |
| GET | `/resumes/{id}` |
| PUT | `/resumes/{id}` |
| DELETE | `/resumes/{id}` |
| PUT | `/resumes/{id}/default` |
| POST | `/resumes/{resumeId}/projects` |
| PUT | `/resumes/{resumeId}/projects/{projectId}` |
| DELETE | `/resumes/{resumeId}/projects/{projectId}` |
| GET | `/inner/resumes/{id}` |
| GET | `/inner/resumes/{id}/projects` |
| GET | `/inner/resumes/default` |

### ai-service

| Method | Path |
| --- | --- |
| POST | `/inner/ai/interview/question` |
| POST | `/inner/ai/interview/evaluate` |
| POST | `/inner/ai/interview/follow-up` |
| POST | `/inner/ai/interview/report` |
| GET | `/admin/ai/prompts` |
| POST | `/admin/ai/prompts` |
| PUT | `/admin/ai/prompts/{id}` |
| DELETE | `/admin/ai/prompts/{id}` |
| PUT | `/admin/ai/prompts/{id}/status` |
| GET | `/admin/ai/call-logs` and `/admin/ai/logs` |
| GET | `/admin/ai/call-logs/{id}` and `/admin/ai/logs/{id}` |

### interview-service

| Method | Path |
| --- | --- |
| POST | `/interviews` |
| POST | `/interviews/{id}/start` |
| GET | `/interviews/{id}/current` |
| POST | `/interviews/{id}/answer` |
| POST | `/interviews/{id}/finish` |
| POST | `/interviews/{id}/report/retry` |
| GET | `/interviews` |
| GET | `/interviews/{id}` |
| GET | `/interviews/{id}/report` |

### system-service

| Method | Path |
| --- | --- |
| GET | `/admin/configs` |
| POST | `/admin/configs` |
| PUT | `/admin/configs/{id}` |
| DELETE | `/admin/configs/{id}` |
| GET | `/admin/system/overview` |

## 5. Simplified Or Mocked Interfaces

- ai-service internal AI APIs are mock implementations, but persist `ai_call_log`.
- interview-service uses simple rules for nextAction and stage flow.
- system-service overview returns zero/default statistics instead of aggregating internal service stats.
- question-service filtering is minimal; tag filtering is not fully optimized.
- report generation is synchronous and mock-based.
- internal-call service-side header validation is still TODO.

## 6. Unfinished Items

- Full admin filtering and detailed query combinations.
- Real AI SDK integration.
- Strong service-side `/inner/**` authorization.
- Rich interview stage scoring and report section aggregation.
- Full management dashboard statistics through internal Feign calls.
- Automated integration tests with running MySQL/Nacos/Redis.

## 7. Gateway Routes

No Gateway route change was required. Existing routes already include:

- `/questions/**`
- `/admin/questions/**`
- `/admin/question-categories/**`
- `/admin/question-tags/**`
- `/admin/question-groups/**`
- `/resumes/**`
- `/interviews/**`
- `/admin/ai/**`
- `/admin/system/**`
- `/admin/configs/**`

## 8. Boundary Confirmations

| Check | Result |
| --- | --- |
| Gateway exposes `/inner/**` | NO |
| User-facing `/ai/**` exists | NO |
| auth-service directly operates user DB | NO |
| system-service manages roles/user-role relations | NO |
| interview-service calls question/resume/ai through OpenFeign | YES |
| ai-service only exposes `/inner/ai/**` and `/admin/ai/**` | YES |
| Old forbidden interfaces generated | NO |
| MQ/ES/MinIO/SSE/WebSocket introduced | NO |

## 9. Initialization SQL

`sql/init.sql` now includes:

- `sys_user`, `sys_role`, `sys_user_role`
- question tables and seed categories/tags/groups/questions
- resume tables
- ai prompt/log tables and prompt seeds
- interview session/stage/message/report tables
- system config table and V1 default configs

Admin password is stored as BCrypt hash. No plaintext local DB password is committed.

## 10. Build Result

Command:

```bash
mvn clean package -DskipTests
```

Result:

```text
BUILD SUCCESS
```

## 11. Current TODO

- Harden internal APIs with trusted internal-call validation.
- Replace mock AI with real configurable client later.
- Add integration tests after infrastructure is running.
- Improve `GET /users/overview` with real question/resume/interview aggregation.
- Improve admin query filters and statistics.

## 12. Next Step

Start MySQL/Nacos/Redis, import `sql/init.sql`, then follow `docs/STAGE2_API_TEST.md` to manually verify the V1 backend loop through Gateway.
