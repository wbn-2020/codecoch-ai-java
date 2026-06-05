USE codecoachai_v1;

ALTER TABLE question_category
  ADD COLUMN IF NOT EXISTS parent_id BIGINT DEFAULT NULL AFTER id,
  ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0 AFTER sort;

ALTER TABLE question_group
  ADD COLUMN IF NOT EXISTS canonical_title VARCHAR(255) DEFAULT NULL AFTER group_name,
  ADD COLUMN IF NOT EXISTS canonical_answer TEXT AFTER canonical_title,
  ADD COLUMN IF NOT EXISTS main_knowledge_point VARCHAR(255) DEFAULT NULL AFTER canonical_answer,
  ADD COLUMN IF NOT EXISTS difficulty VARCHAR(32) DEFAULT NULL AFTER main_knowledge_point;

ALTER TABLE question
  ADD COLUMN IF NOT EXISTS question_type VARCHAR(32) NOT NULL DEFAULT 'SHORT_ANSWER' AFTER difficulty,
  ADD COLUMN IF NOT EXISTS experience_level VARCHAR(64) DEFAULT NULL AFTER question_type,
  ADD COLUMN IF NOT EXISTS is_high_frequency TINYINT NOT NULL DEFAULT 0 AFTER experience_level;

ALTER TABLE resume
  ADD COLUMN IF NOT EXISTS resume_name VARCHAR(128) DEFAULT NULL AFTER title,
  ADD COLUMN IF NOT EXISTS target_position VARCHAR(128) DEFAULT NULL AFTER real_name,
  ADD COLUMN IF NOT EXISTS skill_stack TEXT AFTER target_position,
  ADD COLUMN IF NOT EXISTS work_experience TEXT AFTER skill_stack,
  ADD COLUMN IF NOT EXISTS education_experience TEXT AFTER work_experience;

ALTER TABLE resume_project
  ADD COLUMN IF NOT EXISTS project_period VARCHAR(128) DEFAULT NULL AFTER project_name,
  ADD COLUMN IF NOT EXISTS project_background TEXT AFTER project_period,
  ADD COLUMN IF NOT EXISTS responsibility TEXT AFTER tech_stack,
  ADD COLUMN IF NOT EXISTS core_features TEXT AFTER responsibility,
  ADD COLUMN IF NOT EXISTS technical_difficulties TEXT AFTER core_features,
  ADD COLUMN IF NOT EXISTS optimization_results TEXT AFTER technical_difficulties,
  ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0 AFTER sort;

ALTER TABLE prompt_template
  ADD COLUMN IF NOT EXISTS template_name VARCHAR(128) DEFAULT NULL AFTER name,
  ADD COLUMN IF NOT EXISTS template_content TEXT AFTER content,
  ADD COLUMN IF NOT EXISTS variables TEXT AFTER template_content,
  ADD COLUMN IF NOT EXISTS version VARCHAR(32) DEFAULT 'v1' AFTER variables;

ALTER TABLE ai_call_log
  ADD COLUMN IF NOT EXISTS user_id BIGINT DEFAULT NULL AFTER id,
  ADD COLUMN IF NOT EXISTS model_name VARCHAR(128) DEFAULT NULL AFTER scene,
  ADD COLUMN IF NOT EXISTS prompt_template_id BIGINT DEFAULT NULL AFTER model_name,
  ADD COLUMN IF NOT EXISTS request_prompt TEXT AFTER prompt_template_id,
  ADD COLUMN IF NOT EXISTS response_content MEDIUMTEXT AFTER request_prompt,
  ADD COLUMN IF NOT EXISTS business_id VARCHAR(128) DEFAULT NULL AFTER response_content,
  ADD COLUMN IF NOT EXISTS elapsed_ms BIGINT DEFAULT 0 AFTER response_body;

ALTER TABLE interview_session
  ADD COLUMN IF NOT EXISTS interview_mode VARCHAR(64) DEFAULT NULL AFTER resume_id,
  ADD COLUMN IF NOT EXISTS total_score INT DEFAULT NULL AFTER current_follow_up_count,
  ADD COLUMN IF NOT EXISTS start_time DATETIME DEFAULT NULL AFTER total_score,
  ADD COLUMN IF NOT EXISTS end_time DATETIME DEFAULT NULL AFTER start_time;

ALTER TABLE interview_stage
  ADD COLUMN IF NOT EXISTS stage_order INT NOT NULL DEFAULT 0 AFTER sort,
  ADD COLUMN IF NOT EXISTS expected_question_count INT NOT NULL DEFAULT 1 AFTER stage_order,
  ADD COLUMN IF NOT EXISTS asked_question_count INT NOT NULL DEFAULT 0 AFTER expected_question_count,
  ADD COLUMN IF NOT EXISTS focus_points TEXT AFTER asked_question_count,
  ADD COLUMN IF NOT EXISTS based_on_resume TINYINT NOT NULL DEFAULT 0 AFTER focus_points,
  ADD COLUMN IF NOT EXISTS allow_follow_up TINYINT NOT NULL DEFAULT 1 AFTER based_on_resume,
  ADD COLUMN IF NOT EXISTS max_follow_up_count INT NOT NULL DEFAULT 2 AFTER allow_follow_up,
  ADD COLUMN IF NOT EXISTS score INT DEFAULT NULL AFTER status;

ALTER TABLE interview_message
  ADD COLUMN IF NOT EXISTS parent_message_id BIGINT DEFAULT NULL AFTER question_group_id,
  ADD COLUMN IF NOT EXISTS question_content TEXT AFTER content,
  ADD COLUMN IF NOT EXISTS user_answer TEXT AFTER question_content,
  ADD COLUMN IF NOT EXISTS ai_comment TEXT AFTER user_answer,
  ADD COLUMN IF NOT EXISTS ai_score INT DEFAULT NULL AFTER ai_comment,
  ADD COLUMN IF NOT EXISTS is_follow_up TINYINT NOT NULL DEFAULT 0 AFTER ai_score,
  ADD COLUMN IF NOT EXISTS follow_up_count INT NOT NULL DEFAULT 0 AFTER is_follow_up,
  ADD COLUMN IF NOT EXISTS follow_up_reason VARCHAR(500) DEFAULT NULL AFTER follow_up_count,
  ADD COLUMN IF NOT EXISTS knowledge_points TEXT AFTER follow_up_reason;

ALTER TABLE interview_report
  ADD COLUMN IF NOT EXISTS user_id BIGINT DEFAULT NULL AFTER session_id,
  ADD COLUMN IF NOT EXISTS stage_scores TEXT AFTER total_score,
  ADD COLUMN IF NOT EXISTS weak_points TEXT AFTER stage_scores,
  ADD COLUMN IF NOT EXISTS main_problems TEXT AFTER weaknesses,
  ADD COLUMN IF NOT EXISTS project_problems TEXT AFTER main_problems,
  ADD COLUMN IF NOT EXISTS review_suggestions TEXT AFTER project_problems,
  ADD COLUMN IF NOT EXISTS recommended_questions TEXT AFTER review_suggestions,
  ADD COLUMN IF NOT EXISTS qa_review TEXT AFTER recommended_questions,
  ADD COLUMN IF NOT EXISTS report_content TEXT AFTER qa_review,
  ADD COLUMN IF NOT EXISTS generated_at DATETIME DEFAULT NULL AFTER report_content;

UPDATE interview_report
SET
  total_score = COALESCE(total_score, 82),
  summary = '本场 V1 模拟面试已完成，综合得分 82。总分由回答完整度、关键知识点覆盖、项目表达和工程权衡四个维度综合给出。',
  strengths = '回答亮点：能够围绕 Java 后端常见题目给出基本结论，并能结合 Spring、MySQL、Redis 等技术栈说明常见处理思路。',
  weaknesses = '主要问题：部分回答停留在结论层，对源码细节、执行计划字段、缓存一致性边界和线上排查步骤展开不足。',
  suggestions = '复习建议：复盘集合、并发、事务、索引和缓存的高频题，准备带指标的项目优化案例。',
  report_content = '本场 V1 模拟面试已完成，综合得分 82。'
WHERE summary LIKE '%Mock report%' OR summary LIKE '%the interview has been completed%';

INSERT INTO prompt_template (id, scene, name, template_name, content, template_content, variables, version, status)
VALUES
  (1, 'INTERVIEW_QUESTION_GENERATE', '技术面试问题生成', '技术面试问题生成', '你是 Java 面试官。请基于阶段 {{currentStage}}、目标岗位 {{targetPosition}}、难度 {{difficulty}} 和题库问题 {{questionContent}} 生成一道中文面试问题。只返回 JSON：{\"questionContent\":\"问题内容\"}', '你是 Java 面试官。请基于阶段 {{currentStage}}、目标岗位 {{targetPosition}}、难度 {{difficulty}} 和题库问题 {{questionContent}} 生成一道中文面试问题。只返回 JSON：{\"questionContent\":\"问题内容\"}', 'targetPosition,experienceLevel,industryDirection,difficulty,interviewerStyle,currentStage,questionContent,historySummary', 'v1', 1),
  (2, 'PROJECT_DEEP_DIVE_QUESTION', '项目深挖问题生成', '项目深挖问题生成', '你是 Java 项目面试官。请结合简历 {{resumeContent}}、项目 {{projectContent}} 和当前阶段 {{currentStage}} 生成一个中文项目深挖问题。只返回 JSON：{\"questionContent\":\"问题内容\"}', '你是 Java 项目面试官。请结合简历 {{resumeContent}}、项目 {{projectContent}} 和当前阶段 {{currentStage}} 生成一个中文项目深挖问题。只返回 JSON：{\"questionContent\":\"问题内容\"}', 'resumeContent,projectContent,currentStage,historySummary', 'v1', 1),
  (3, 'INTERVIEW_ANSWER_EVALUATE', '回答评分点评', '回答评分点评', '你是 Java 面试官。请根据问题 {{questionContent}}、参考答案 {{referenceAnswer}} 和候选人回答 {{userAnswer}} 给出中文评分。只返回 JSON：{\"score\":80,\"comment\":\"点评\",\"nextAction\":\"NEXT_QUESTION\",\"knowledgePoints\":\"知识点\"}，nextAction 只能是 FOLLOW_UP、NEXT_QUESTION、NEXT_STAGE 或 FINISH。', '你是 Java 面试官。请根据问题 {{questionContent}}、参考答案 {{referenceAnswer}} 和候选人回答 {{userAnswer}} 给出中文评分。只返回 JSON：{\"score\":80,\"comment\":\"点评\",\"nextAction\":\"NEXT_QUESTION\",\"knowledgePoints\":\"知识点\"}，nextAction 只能是 FOLLOW_UP、NEXT_QUESTION、NEXT_STAGE 或 FINISH。', 'questionContent,referenceAnswer,userAnswer,currentStage,historySummary', 'v1', 1),
  (4, 'INTERVIEW_FOLLOW_UP_GENERATE', '动态追问生成', '动态追问生成', '你是 Java 面试官。请基于问题 {{questionContent}}、回答 {{userAnswer}} 和点评 {{aiComment}} 生成一个中文追问。只返回 JSON：{\"followUpQuestion\":\"追问内容\"}', '你是 Java 面试官。请基于问题 {{questionContent}}、回答 {{userAnswer}} 和点评 {{aiComment}} 生成一个中文追问。只返回 JSON：{\"followUpQuestion\":\"追问内容\"}', 'questionContent,userAnswer,currentStage,historySummary', 'v1', 1),
  (5, 'INTERVIEW_REPORT_GENERATE', '面试报告生成', '面试报告生成', '你是 Java 面试教练。请基于面试记录 {{historySummary}} 生成中文结构化报告。仅当面试记录包含候选人的有效回答时才计算分数；无有效回答时不要生成评分报告。只返回 JSON：{\"totalScore\":0,\"summary\":\"总分来源说明\",\"strengths\":\"亮点\",\"weaknesses\":\"问题\",\"stageScores\":\"{}\",\"weakPoints\":\"[]\",\"mainProblems\":\"问题\",\"projectProblems\":\"[]\",\"reviewSuggestions\":\"建议\",\"recommendedQuestions\":\"[]\",\"qaReview\":\"[]\",\"reportContent\":\"报告正文\"}', '你是 Java 面试教练。请基于面试记录 {{historySummary}} 生成中文结构化报告。仅当面试记录包含候选人的有效回答时才计算分数；无有效回答时不要生成评分报告。只返回 JSON：{\"totalScore\":0,\"summary\":\"总分来源说明\",\"strengths\":\"亮点\",\"weaknesses\":\"问题\",\"stageScores\":\"{}\",\"weakPoints\":\"[]\",\"mainProblems\":\"问题\",\"projectProblems\":\"[]\",\"reviewSuggestions\":\"建议\",\"recommendedQuestions\":\"[]\",\"qaReview\":\"[]\",\"reportContent\":\"报告正文\"}', 'historySummary,targetPosition,experienceLevel,industryDirection,difficulty,resumeContent,projectContent', 'v1', 1)
ON DUPLICATE KEY UPDATE
  scene = VALUES(scene),
  name = VALUES(name),
  template_name = VALUES(template_name),
  content = VALUES(content),
  template_content = VALUES(template_content),
  variables = VALUES(variables),
  version = VALUES(version),
  status = VALUES(status);
