-- Restore business context placeholders in active prompts by creating and activating new versions.

DROP TEMPORARY TABLE IF EXISTS tmp_v4_054_prompt_definition;
CREATE TEMPORARY TABLE tmp_v4_054_prompt_definition (
  scene VARCHAR(64) NOT NULL,
  version_name VARCHAR(128) NOT NULL,
  content LONGTEXT NOT NULL,
  variables_json LONGTEXT NOT NULL,
  PRIMARY KEY (scene)
);

INSERT INTO tmp_v4_054_prompt_definition (scene, version_name, content, variables_json)
VALUES
(
  'INTERVIEW_QUESTION_GENERATE',
  'V4.054 interview question business context',
  CONCAT_WS(CHAR(10),
    'You are a senior Java interviewer. Generate one focused interview question from the supplied business context.',
    'targetPosition: {{targetPosition}}',
    'experienceLevel: {{experienceLevel}}',
    'industryDirection: {{industryDirection}}',
    'industryContext: {{industryContext}}',
    'difficulty: {{difficulty}}',
    'interviewerStyle: {{interviewerStyle}}',
    'stageName: {{stageName}}',
    'stageType: {{stageType}}',
    'focusPoints: {{focusPoints}}',
    'currentQuestion: {{currentQuestion}}',
    'questionContent: {{questionContent}}',
    'resumeContent: {{resumeContent}}',
    'projectContent: {{projectContent}}',
    'historySummary: {{historySummary}}',
    'trainingScene: {{trainingScene}}',
    'targetSkillDomain: {{targetSkillDomain}}',
    'targetSkillCodes: {{targetSkillCodes}}',
    'targetLevel: {{targetLevel}}',
    'projectEvidenceIds: {{projectEvidenceIds}}',
    'projectEvidenceContext: {{projectEvidenceContext}}',
    'trainingContextSummary: {{trainingContextSummary}}',
    'followUpIntensity: {{followUpIntensity}}',
    'Use only supplied evidence. Do not invent candidate experience. Output JSON only: {"questionContent":"content"}.'
  ),
  'targetPosition,experienceLevel,industryDirection,industryContext,difficulty,interviewerStyle,stageName,stageType,focusPoints,currentQuestion,questionContent,resumeContent,projectContent,historySummary,trainingScene,targetSkillDomain,targetSkillCodes,targetLevel,projectEvidenceIds,projectEvidenceContext,trainingContextSummary,followUpIntensity'
),
(
  'PROJECT_DEEP_DIVE_QUESTION',
  'V4.054 project interview question business context',
  CONCAT_WS(CHAR(10),
    'You are a senior Java project interviewer. Generate one project deep-dive question from the supplied evidence.',
    'targetPosition: {{targetPosition}}',
    'experienceLevel: {{experienceLevel}}',
    'industryDirection: {{industryDirection}}',
    'industryContext: {{industryContext}}',
    'difficulty: {{difficulty}}',
    'interviewerStyle: {{interviewerStyle}}',
    'stageName: {{stageName}}',
    'stageType: {{stageType}}',
    'focusPoints: {{focusPoints}}',
    'currentQuestion: {{currentQuestion}}',
    'questionContent: {{questionContent}}',
    'resumeContent: {{resumeContent}}',
    'projectContent: {{projectContent}}',
    'historySummary: {{historySummary}}',
    'trainingScene: {{trainingScene}}',
    'targetSkillDomain: {{targetSkillDomain}}',
    'targetSkillCodes: {{targetSkillCodes}}',
    'targetLevel: {{targetLevel}}',
    'projectEvidenceIds: {{projectEvidenceIds}}',
    'projectEvidenceContext: {{projectEvidenceContext}}',
    'trainingContextSummary: {{trainingContextSummary}}',
    'followUpIntensity: {{followUpIntensity}}',
    'Ask for concrete architecture, tradeoff, troubleshooting, performance, responsibility, or measurable-result details. Output JSON only: {"questionContent":"content"}.'
  ),
  'targetPosition,experienceLevel,industryDirection,industryContext,difficulty,interviewerStyle,stageName,stageType,focusPoints,currentQuestion,questionContent,resumeContent,projectContent,historySummary,trainingScene,targetSkillDomain,targetSkillCodes,targetLevel,projectEvidenceIds,projectEvidenceContext,trainingContextSummary,followUpIntensity'
),
(
  'INTERVIEW_ANSWER_EVALUATE',
  'V4.054 interview answer evaluation business context',
  CONCAT_WS(CHAR(10),
    'You are a senior Java interviewer. Evaluate the answer using the complete question and training context.',
    'rootQuestionContent: {{rootQuestionContent}}',
    'currentQuestionContent: {{currentQuestionContent}}',
    'referenceAnswer: {{referenceAnswer}}',
    'userAnswer: {{userAnswer}}',
    'stageName: {{stageName}}',
    'stageType: {{stageType}}',
    'historySummary: {{historySummary}}',
    'industryContext: {{industryContext}}',
    'followUpCount: {{followUpCount}}',
    'maxFollowUpCount: {{maxFollowUpCount}}',
    'knowledgePoints: {{knowledgePoints}}',
    'projectContent: {{projectContent}}',
    'trainingScene: {{trainingScene}}',
    'targetSkillDomain: {{targetSkillDomain}}',
    'targetSkillCodes: {{targetSkillCodes}}',
    'targetLevel: {{targetLevel}}',
    'projectEvidenceIds: {{projectEvidenceIds}}',
    'projectEvidenceContext: {{projectEvidenceContext}}',
    'trainingContextSummary: {{trainingContextSummary}}',
    'followUpIntensity: {{followUpIntensity}}',
    'score must be an integer from 0 to 100. nextAction must be FOLLOW_UP, NEXT_QUESTION, NEXT_STAGE, or FINISH. Do not invent evidence.',
    'Output JSON only with score, comment, nextAction, followUpQuestion, followUpReason, and knowledgePoints.'
  ),
  'rootQuestionContent,currentQuestionContent,referenceAnswer,userAnswer,stageName,stageType,historySummary,industryContext,followUpCount,maxFollowUpCount,knowledgePoints,projectContent,trainingScene,targetSkillDomain,targetSkillCodes,targetLevel,projectEvidenceIds,projectEvidenceContext,trainingContextSummary,followUpIntensity'
),
(
  'INTERVIEW_FOLLOW_UP_GENERATE',
  'V4.054 interview follow-up business context',
  CONCAT_WS(CHAR(10),
    'You are a senior Java interviewer. Generate exactly one evidence-grounded follow-up question.',
    'rootQuestionContent: {{rootQuestionContent}}',
    'currentQuestionContent: {{currentQuestionContent}}',
    'referenceAnswer: {{referenceAnswer}}',
    'userAnswer: {{userAnswer}}',
    'aiComment: {{aiComment}}',
    'stageName: {{stageName}}',
    'historySummary: {{historySummary}}',
    'industryContext: {{industryContext}}',
    'followUpCount: {{followUpCount}}',
    'maxFollowUpCount: {{maxFollowUpCount}}',
    'knowledgePoints: {{knowledgePoints}}',
    'trainingScene: {{trainingScene}}',
    'targetSkillDomain: {{targetSkillDomain}}',
    'targetSkillCodes: {{targetSkillCodes}}',
    'targetLevel: {{targetLevel}}',
    'projectEvidenceIds: {{projectEvidenceIds}}',
    'projectEvidenceContext: {{projectEvidenceContext}}',
    'trainingContextSummary: {{trainingContextSummary}}',
    'followUpIntensity: {{followUpIntensity}}',
    'The follow-up must stay on the original Java topic and target a concrete omission or error. Output JSON only with followUpQuestion, reason, and relatedToOriginalQuestion.'
  ),
  'rootQuestionContent,currentQuestionContent,referenceAnswer,userAnswer,aiComment,stageName,historySummary,industryContext,followUpCount,maxFollowUpCount,knowledgePoints,trainingScene,targetSkillDomain,targetSkillCodes,targetLevel,projectEvidenceIds,projectEvidenceContext,trainingContextSummary,followUpIntensity'
),
(
  'INTERVIEW_REPORT_GENERATE',
  'V4.054 interview report business context',
  CONCAT_WS(CHAR(10),
    'You are a senior Java interview coach. Generate a structured report from real interview evidence.',
    'targetJobId: {{targetJobId}}',
    'skillProfileId: {{skillProfileId}}',
    'matchReportId: {{matchReportId}}',
    'skillGapContext: {{skillGapContext}}',
    'targetPosition: {{targetPosition}}',
    'experienceLevel: {{experienceLevel}}',
    'industryDirection: {{industryDirection}}',
    'industryContext: {{industryContext}}',
    'difficulty: {{difficulty}}',
    'resumeContent: {{resumeContent}}',
    'projectContent: {{projectContent}}',
    'historySummary: {{historySummary}}',
    'trainingScene: {{trainingScene}}',
    'targetSkillDomain: {{targetSkillDomain}}',
    'targetSkillCodes: {{targetSkillCodes}}',
    'targetLevel: {{targetLevel}}',
    'projectEvidenceIds: {{projectEvidenceIds}}',
    'trainingContextSummary: {{trainingContextSummary}}',
    'followUpIntensity: {{followUpIntensity}}',
    'Use only supplied summaries and evidence. If useful answers are absent, totalScore must be null and the reason must be explicit.',
    'Output JSON only with totalScore, summary, strengths, weakPoints, mainProblems, projectProblems, reviewSuggestions, recommendedQuestions, qaReview, stageScores, and reportContent.'
  ),
  'targetJobId,skillProfileId,matchReportId,skillGapContext,targetPosition,experienceLevel,industryDirection,industryContext,difficulty,resumeContent,projectContent,historySummary,trainingScene,targetSkillDomain,targetSkillCodes,targetLevel,projectEvidenceIds,trainingContextSummary,followUpIntensity'
),
(
  'PRACTICE_ANSWER_REVIEW',
  'V4.054 practice review business context',
  CONCAT_WS(CHAR(10),
    'You are a senior Java backend practice coach. Review the actual user answer against the supplied question evidence.',
    'recordId: {{recordId}}',
    'userId: {{userId}}',
    'questionId: {{questionId}}',
    'questionTitle: {{questionTitle}}',
    'questionContent: {{questionContent}}',
    'questionType: {{questionType}}',
    'difficulty: {{difficulty}}',
    'technologyStack: {{technologyStack}}',
    'knowledgePoint: {{knowledgePoint}}',
    'referenceAnswer: {{referenceAnswer}}',
    'analysis: {{analysis}}',
    'userAnswer: {{userAnswer}}',
    'answerDurationSeconds: {{answerDurationSeconds}}',
    'targetPosition: {{targetPosition}}',
    'experienceLevel: {{experienceLevel}}',
    'Do not invent user experience. Output JSON only with score, level, summary, strengths, weaknesses, improvementSuggestions, referenceComparison, knowledgeGaps, and suggestedFollowUps.'
  ),
  'recordId,userId,questionId,questionTitle,questionContent,questionType,difficulty,technologyStack,knowledgePoint,referenceAnswer,analysis,userAnswer,answerDurationSeconds,targetPosition,experienceLevel'
),
(
  'RESUME_JOB_MATCH',
  'V4.054 resume job match business context',
  CONCAT_WS(CHAR(10),
    'You are a senior Java backend career coach. Generate an evidence-grounded resume to target-job match report.',
    'reportId: {{reportId}}',
    'userId: {{userId}}',
    'resumeId: {{resumeId}}',
    'resumeVersionId: {{resumeVersionId}}',
    'targetJobId: {{targetJobId}}',
    'jdAnalysisId: {{jdAnalysisId}}',
    'userExperienceYears: {{userExperienceYears}}',
    'resumeAnalysisJson: {{resumeAnalysisJson}}',
    'resumeSnapshotJson: {{resumeSnapshotJson}}',
    'jobDescriptionAnalysisJson: {{jobDescriptionAnalysisJson}}',
    'targetJobJson: {{targetJobJson}}',
    'Use only supplied resume and JD evidence. Missing evidence must become a gap or risk, never an invented strength.',
    'Output JSON only with overallScore, dimensionScores, strengths, gaps, resumeRisks, optimizationSuggestions, recommendedLearningTopics, recommendedInterviewTopics, and summary.'
  ),
  'reportId,userId,resumeId,resumeVersionId,targetJobId,jdAnalysisId,userExperienceYears,resumeAnalysisJson,resumeSnapshotJson,jobDescriptionAnalysisJson,targetJobJson'
),
(
  'SKILL_GAP_ANALYZE',
  'V4.054 skill gap business context',
  CONCAT_WS(CHAR(10),
    'You are a senior Java backend career coach. Build a target-job skill profile from supplied match evidence.',
    'profileId: {{profileId}}',
    'matchReportId: {{matchReportId}}',
    'userId: {{userId}}',
    'resumeId: {{resumeId}}',
    'targetJobId: {{targetJobId}}',
    'jdAnalysisId: {{jdAnalysisId}}',
    'targetJobJson: {{targetJobJson}}',
    'jobDescriptionAnalysisJson: {{jobDescriptionAnalysisJson}}',
    'matchReportJson: {{matchReportJson}}',
    'matchDetailsJson: {{matchDetailsJson}}',
    'gapsJson: {{gapsJson}}',
    'recommendedLearningTopicsJson: {{recommendedLearningTopicsJson}}',
    'recommendedInterviewTopicsJson: {{recommendedInterviewTopicsJson}}',
    'resumeAnalysisJson: {{resumeAnalysisJson}}',
    'resumeSnapshotJson: {{resumeSnapshotJson}}',
    'Do not invent candidate evidence. Output JSON only with profileSummary, overallLevel, overallScore, skillGaps, nextPrioritySkills, and nextActions.'
  ),
  'profileId,matchReportId,userId,resumeId,targetJobId,jdAnalysisId,targetJobJson,jobDescriptionAnalysisJson,matchReportJson,matchDetailsJson,gapsJson,recommendedLearningTopicsJson,recommendedInterviewTopicsJson,resumeAnalysisJson,resumeSnapshotJson'
),
(
  'LEARNING_PLAN_GENERATE',
  'V4.054 learning plan business context',
  CONCAT_WS(CHAR(10),
    'You are a senior Java backend interview coach. Generate a practical study plan in Chinese.',
    'learningPlanId: {{learningPlanId}}',
    'userId: {{userId}}',
    'reportId: {{reportId}}',
    'sessionId: {{sessionId}}',
    'targetPosition: {{targetPosition}}',
    'industryDirection: {{industryDirection}}',
    'experienceLevel: {{experienceLevel}}',
    'expectedDurationDays: {{expectedDurationDays}}',
    'interviewSummary: {{interviewSummary}}',
    'weaknessSummary: {{weaknessSummary}}',
    'questionPerformanceSummary: {{questionPerformanceSummary}}',
    'resumeWeaknessSummary: {{resumeWeaknessSummary}}',
    'extraRequirements: {{extraRequirements}}',
    'Every task must trace to supplied weakness or performance evidence. Output JSON only with planTitle, planSummary, durationDays, and stages.'
  ),
  'learningPlanId,userId,reportId,sessionId,targetPosition,industryDirection,experienceLevel,expectedDurationDays,interviewSummary,weaknessSummary,questionPerformanceSummary,resumeWeaknessSummary,extraRequirements'
),
(
  'TARGETED_STUDY_PLAN_GENERATE',
  'V4.054 targeted study plan business context',
  CONCAT_WS(CHAR(10),
    'You are a senior Java backend career coach. Generate a gap-driven study plan for the target job.',
    'learningPlanId: {{learningPlanId}}',
    'userId: {{userId}}',
    'targetJobId: {{targetJobId}}',
    'skillProfileId: {{skillProfileId}}',
    'matchReportId: {{matchReportId}}',
    'targetJobJson: {{targetJobJson}}',
    'skillProfileJson: {{skillProfileJson}}',
    'skillGapsJson: {{skillGapsJson}}',
    'availableDays: {{availableDays}}',
    'dailyMinutes: {{dailyMinutes}}',
    'startDate: {{startDate}}',
    'existingStudyPlansJson: {{existingStudyPlansJson}}',
    'planTitle: {{planTitle}}',
    'Every task must map to a selected gap and must not invent candidate experience. Output JSON only with planTitle, planSummary, durationDays, and stages.'
  ),
  'learningPlanId,userId,targetJobId,skillProfileId,matchReportId,targetJobJson,skillProfileJson,skillGapsJson,availableDays,dailyMinutes,startDate,existingStudyPlansJson,planTitle'
),
(
  'TARGETED_QUESTION_RECOMMEND',
  'V4.054 targeted question recommendation business context',
  CONCAT_WS(CHAR(10),
    'You are a senior Java backend interview training coach. Generate evidence-grounded target-job question recommendations.',
    'batchId: {{batchId}}',
    'userId: {{userId}}',
    'sourceType: {{sourceType}}',
    'sourceId: {{sourceId}}',
    'targetJobId: {{targetJobId}}',
    'matchReportId: {{matchReportId}}',
    'skillProfileId: {{skillProfileId}}',
    'studyPlanId: {{studyPlanId}}',
    'strategy: {{strategy}}',
    'questionCount: {{questionCount}}',
    'difficultyPreference: {{difficultyPreference}}',
    'targetJobJson: {{targetJobJson}}',
    'matchReportJson: {{matchReportJson}}',
    'skillProfileJson: {{skillProfileJson}}',
    'skillGapsJson: {{skillGapsJson}}',
    'studyPlanJson: {{studyPlanJson}}',
    'studyTasksJson: {{studyTasksJson}}',
    'Each recommendation must map to supplied gaps or study tasks and must not invent candidate experience. Output JSON only with a questions array.'
  ),
  'batchId,userId,sourceType,sourceId,targetJobId,matchReportId,skillProfileId,studyPlanId,strategy,questionCount,difficultyPreference,targetJobJson,matchReportJson,skillProfileJson,skillGapsJson,studyPlanJson,studyTasksJson'
);

DROP TEMPORARY TABLE IF EXISTS tmp_v4_054_prompt_target;
CREATE TEMPORARY TABLE tmp_v4_054_prompt_target (
  template_id BIGINT NOT NULL,
  scene VARCHAR(64) NOT NULL,
  version_name VARCHAR(128) NOT NULL,
  content LONGTEXT NOT NULL,
  variables_json LONGTEXT NOT NULL,
  model_params_json LONGTEXT DEFAULT NULL,
  PRIMARY KEY (template_id),
  UNIQUE KEY uk_tmp_v4_054_prompt_target_scene (scene)
);

INSERT INTO tmp_v4_054_prompt_target (
  template_id, scene, version_name, content, variables_json, model_params_json
)
SELECT p.id, d.scene, d.version_name, d.content, d.variables_json, active_version.model_params_json
FROM prompt_template p
JOIN tmp_v4_054_prompt_definition d ON d.scene = p.scene
LEFT JOIN prompt_template_version active_version
  ON active_version.id = p.active_version_id
 AND active_version.deleted = 0
WHERE p.deleted = 0
  AND p.status = 1
  AND (p.enabled = 1 OR p.enabled IS NULL)
  AND p.id = (
    SELECT MAX(p2.id)
    FROM prompt_template p2
    WHERE p2.scene = p.scene
      AND p2.deleted = 0
      AND p2.status = 1
      AND (p2.enabled = 1 OR p2.enabled IS NULL)
  );

INSERT INTO prompt_template_version (
  template_id, scene, version_code, version_name, content, variables_json, model_params_json,
  status, is_active, activated_at, change_log, deleted
)
SELECT t.template_id,
       t.scene,
       'v4-054-business-context',
       t.version_name,
       t.content,
       t.variables_json,
       t.model_params_json,
       'INACTIVE',
       0,
       NULL,
       'V4_054 restores business variables and adds prompt trust validation coverage',
       0
FROM tmp_v4_054_prompt_target t
ON DUPLICATE KEY UPDATE
  version_name = VALUES(version_name),
  content = VALUES(content),
  variables_json = VALUES(variables_json),
  model_params_json = VALUES(model_params_json),
  status = 'INACTIVE',
  is_active = 0,
  activated_at = NULL,
  change_log = VALUES(change_log),
  deleted = 0;

UPDATE prompt_template_version v
JOIN tmp_v4_054_prompt_target t ON t.template_id = v.template_id
SET v.status = 'INACTIVE',
    v.is_active = 0
WHERE v.deleted = 0
  AND v.version_code <> 'v4-054-business-context'
  AND (v.is_active = 1 OR v.status = 'ACTIVE');

UPDATE prompt_template_version v
JOIN tmp_v4_054_prompt_target t
  ON t.template_id = v.template_id
 AND v.version_code = 'v4-054-business-context'
SET v.status = 'ACTIVE',
    v.is_active = 1,
    v.activated_at = NOW(),
    v.deleted = 0;

UPDATE prompt_template p
JOIN tmp_v4_054_prompt_definition d ON d.scene = p.scene
LEFT JOIN tmp_v4_054_prompt_target t ON t.template_id = p.id
SET p.enabled = 0,
    p.status = 0
WHERE p.deleted = 0
  AND t.template_id IS NULL
  AND (p.enabled = 1 OR p.status = 1);

UPDATE prompt_template p
JOIN tmp_v4_054_prompt_target t ON t.template_id = p.id
JOIN prompt_template_version v
  ON v.template_id = t.template_id
 AND v.version_code = 'v4-054-business-context'
 AND v.deleted = 0
SET p.active_version_id = v.id,
    p.content = v.content,
    p.template_content = v.content,
    p.variables = v.variables_json,
    p.version = v.version_code,
    p.enabled = 1,
    p.status = 1;

DROP TEMPORARY TABLE IF EXISTS tmp_v4_054_prompt_target;
DROP TEMPORARY TABLE IF EXISTS tmp_v4_054_prompt_definition;
