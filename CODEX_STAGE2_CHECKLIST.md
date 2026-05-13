# CodeCoachAI Java Stage 2 Checklist

| No. | Check Item | Status | Notes |
| --- | --- | --- | --- |
| 1 | question-service implements basic question APIs | PASS | User and admin question APIs added. |
| 2 | question-service implements category APIs | PASS | `/admin/question-categories`. |
| 3 | question-service implements tag APIs | PASS | `/admin/question-tags`. |
| 4 | question-service implements question group APIs | PASS | `/admin/question-groups`. |
| 5 | question-service implements `POST /questions/{id}/answers` | PASS | Records answer/mastery/wrong state. |
| 6 | question-service implements `GET /questions/wrong-records` | PASS | User wrong-record list. |
| 7 | question-service does not use old `POST /questions/answer` | PASS | Not implemented. |
| 8 | question-service does not use old `GET /questions/wrongs` | PASS | Not implemented. |
| 9 | resume-service implements resume CRUD | PASS | `/resumes/**`. |
| 10 | resume-service implements project CRUD | PASS | `/resumes/{resumeId}/projects/**`. |
| 11 | resume-service has no file upload parsing | PASS | Manual input only. |
| 12 | ai-service implements `/inner/ai/interview/question` | PASS | Mock with log. |
| 13 | ai-service implements `/inner/ai/interview/evaluate` | PASS | Mock with score/comment/nextAction. |
| 14 | ai-service implements `/inner/ai/interview/follow-up` | PASS | Mock with log. |
| 15 | ai-service implements `/inner/ai/interview/report` | PASS | Mock with report content. |
| 16 | ai-service has no user-facing `/ai/**` | PASS | Only `/inner/ai/**` and `/admin/ai/**`. |
| 17 | ai-service records AI call logs | PASS | `AiCallLog` persisted by mock calls. |
| 18 | interview-service implements `POST /interviews` | PASS | Creates session and stages. |
| 19 | interview-service implements `POST /interviews/{id}/start` | PASS | Starts and generates first question. |
| 20 | interview-service implements `GET /interviews/{id}/current` | PASS | Returns current state/question. |
| 21 | interview-service implements `POST /interviews/{id}/answer` | PASS | Saves answer, evaluates, returns nextAction. |
| 22 | interview-service implements `POST /interviews/{id}/finish` | PASS | Generates report synchronously. |
| 23 | interview-service implements `POST /interviews/{id}/report/retry` | PASS | Retries failed/missing report. |
| 24 | interview-service implements `GET /interviews` | PASS | User history. |
| 25 | interview-service implements `GET /interviews/{id}` | PASS | Detail with stages/messages. |
| 26 | interview-service implements `GET /interviews/{id}/report` | PASS | Report detail. |
| 27 | interview-service separates interview status and report status | PASS | `status` and `reportStatus`. |
| 28 | interview-service supports nextAction | PASS | State machine returns nextAction. |
| 29 | nextAction contains FOLLOW_UP / NEXT_QUESTION / NEXT_STAGE / FINISH | PASS | Enum exists. |
| 30 | reportStatus contains NOT_GENERATED / GENERATING / GENERATED / FAILED | PASS | Enum exists. |
| 31 | system-service only does config and simplified statistics | PASS | No role relation code. |
| 32 | system-service does not manage role relations | PASS | Roles remain in user-service. |
| 33 | Gateway does not expose `/inner/**` | PASS | No Gateway route; filter blocks `/inner/`. |
| 34 | Gateway does not add `/ai/**` | PASS | Only `/admin/ai/**`. |
| 35 | No V2/V3 features implemented | PASS | Scope stayed V1. |
| 36 | MQ not introduced | PASS | No MQ dependency or code. |
| 37 | ES not introduced | PASS | No Elasticsearch dependency. |
| 38 | MinIO not introduced | PASS | No MinIO dependency. |
| 39 | SSE/WebSocket not introduced | PASS | Not implemented. |
| 40 | `mvn clean package -DskipTests` passes | PASS | Build success. |

## TODO

| Item | Status | Notes |
| --- | --- | --- |
| Service-side `/inner/**` hardening | TODO | Gateway blocks frontend access; internal trusted header checks should be added later. |
| Real AI client | TODO | Stage 2 uses mock AI. |
| Runtime integration tests | TODO | Requires MySQL/Nacos/Redis running. |
| Rich statistics | TODO | system-service overview is simplified. |
