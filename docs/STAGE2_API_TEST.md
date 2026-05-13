# Stage 2 API Test Guide

## 1. Infrastructure

Start infrastructure first:

1. Nacos: `127.0.0.1:8848`
2. MySQL 8
3. Redis

Import:

```sql
source sql/init.sql;
```

Local database credentials should be passed through environment variables, for example:

```bash
MYSQL_USERNAME=root
MYSQL_PASSWORD=your-local-password
```

Do not commit local plaintext passwords.

## 2. Service Start Order

Recommended order:

1. `codecoachai-gateway`
2. `codecoachai-auth`
3. `codecoachai-user`
4. `codecoachai-question`
5. `codecoachai-resume`
6. `codecoachai-ai`
7. `codecoachai-interview`
8. `codecoachai-system`

Gateway default port:

```text
http://localhost:8080
```

## 3. Basic Flow

### Register

```http
POST /auth/register
Content-Type: application/json

{
  "username": "stage2user",
  "password": "123456",
  "confirmPassword": "123456",
  "nickname": "Stage2 User",
  "email": "stage2@example.com"
}
```

### Login

```http
POST /auth/login
Content-Type: application/json

{
  "username": "stage2user",
  "password": "123456"
}
```

Use the returned token:

```text
Authorization: Bearer {token}
```

### Current User

```http
GET /auth/current-user
Authorization: Bearer {token}
```

## 4. Question Flow

```http
GET /questions?pageNo=1&pageSize=10
Authorization: Bearer {token}
```

```http
GET /questions/1
Authorization: Bearer {token}
```

```http
POST /questions/1/answers
Authorization: Bearer {token}
Content-Type: application/json

{
  "answerContent": "HashMap uses hash buckets, handles collisions, treeifies long chains, and resizes by load factor.",
  "masteryStatus": "MASTERED"
}
```

```http
GET /questions/wrong-records
Authorization: Bearer {token}
```

## 5. Resume Flow

```http
POST /resumes
Authorization: Bearer {token}
Content-Type: application/json

{
  "title": "Java Backend Resume",
  "realName": "Stage2 User",
  "email": "stage2@example.com",
  "phone": "13800000000",
  "summary": "Java backend developer with Spring Boot and microservice experience."
}
```

```http
POST /resumes/{resumeId}/projects
Authorization: Bearer {token}
Content-Type: application/json

{
  "projectName": "CodeCoachAI",
  "role": "Backend Developer",
  "techStack": "Spring Boot, MyBatis-Plus, MySQL, Redis, Nacos",
  "description": "Built V1 interview training backend.",
  "highlights": "Microservice boundaries and mock AI interview loop.",
  "sort": 1
}
```

## 6. Interview Flow

Create:

```http
POST /interviews
Authorization: Bearer {token}
Content-Type: application/json

{
  "mode": "COMPREHENSIVE",
  "resumeId": 1,
  "title": "Stage2 Mock Interview",
  "maxQuestionCount": 5
}
```

Start:

```http
POST /interviews/{interviewId}/start
Authorization: Bearer {token}
```

Current:

```http
GET /interviews/{interviewId}/current
Authorization: Bearer {token}
```

Answer:

```http
POST /interviews/{interviewId}/answer
Authorization: Bearer {token}
Content-Type: application/json

{
  "answerContent": "My answer includes principle, scenario, tradeoff, and production example."
}
```

Continue answering until `nextAction` is `FINISH`, then finish:

```http
POST /interviews/{interviewId}/finish
Authorization: Bearer {token}
```

Report:

```http
GET /interviews/{interviewId}/report
Authorization: Bearer {token}
```

History:

```http
GET /interviews?pageNo=1&pageSize=10
Authorization: Bearer {token}
```

## 7. Admin Checks

```http
GET /admin/question-categories
Authorization: Bearer {admin-token}
```

```http
GET /admin/ai/prompts
Authorization: Bearer {admin-token}
```

```http
GET /admin/ai/call-logs
Authorization: Bearer {admin-token}
```

```http
GET /admin/system/overview
Authorization: Bearer {admin-token}
```

## 8. Boundary Checks

These must not be exposed through Gateway routes:

```text
/inner/**
/ai/**
```

AI user-facing calls are intentionally absent. The frontend uses `/interviews/**`; `interview-service` calls `/inner/ai/**` through Spring Cloud OpenFeign.
