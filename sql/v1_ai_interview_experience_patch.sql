USE codecoachai_v1;

-- V1 AI interview experience prompt refresh.
-- Safe to run repeatedly. It only updates the five V1 prompt templates and does not touch user data.

UPDATE prompt_template
SET
  content = '你是资深 Java 面试官。请基于当前阶段生成一个干净的中文技术面试问题。当前阶段：{{stageName}}。阶段类型：{{stageType}}。阶段重点：{{focusPoints}}。目标岗位：{{targetPosition}}。难度：{{difficulty}}。题库候选题：{{questionContent}}。历史摘要：{{historySummary}}。要求：只能围绕当前阶段重点提问，不要跳到无关主题。只输出 JSON，不要 Markdown，不要代码块，不要解释文字。JSON 字段固定：{"questionContent":"问题内容"}',
  template_content = '你是资深 Java 面试官。请基于当前阶段生成一个干净的中文技术面试问题。当前阶段：{{stageName}}。阶段类型：{{stageType}}。阶段重点：{{focusPoints}}。目标岗位：{{targetPosition}}。难度：{{difficulty}}。题库候选题：{{questionContent}}。历史摘要：{{historySummary}}。要求：只能围绕当前阶段重点提问，不要跳到无关主题。只输出 JSON，不要 Markdown，不要代码块，不要解释文字。JSON 字段固定：{"questionContent":"问题内容"}',
  variables = 'targetPosition,experienceLevel,industryDirection,difficulty,interviewerStyle,currentStage,stageName,stageType,focusPoints,questionContent,historySummary'
WHERE scene = 'INTERVIEW_QUESTION_GENERATE';

UPDATE prompt_template
SET
  content = '你是资深 Java 项目面试官。请结合简历 {{resumeContent}}、项目 {{projectContent}}、当前阶段 {{stageName}} 和阶段重点 {{focusPoints}} 生成一个中文项目深挖问题。只能围绕当前项目阶段提问，不要跳到无关主题。只输出 JSON，不要 Markdown，不要代码块，不要解释文字。JSON 字段固定：{"questionContent":"问题内容"}',
  template_content = '你是资深 Java 项目面试官。请结合简历 {{resumeContent}}、项目 {{projectContent}}、当前阶段 {{stageName}} 和阶段重点 {{focusPoints}} 生成一个中文项目深挖问题。只能围绕当前项目阶段提问，不要跳到无关主题。只输出 JSON，不要 Markdown，不要代码块，不要解释文字。JSON 字段固定：{"questionContent":"问题内容"}',
  variables = 'resumeContent,projectContent,currentStage,stageName,stageType,focusPoints,historySummary'
WHERE scene = 'PROJECT_DEEP_DIVE_QUESTION';

UPDATE prompt_template
SET
  content = '你是资深 Java 面试官。请一次性完成评分、点评、流程决策，并在需要时生成一个追问。原始主问题：{{rootQuestionContent}}。当前问题：{{currentQuestionContent}}。参考答案：{{referenceAnswer}}。候选人回答：{{userAnswer}}。当前阶段：{{stageName}}。历史摘要：{{historySummary}}。当前追问次数：{{followUpCount}}。最大追问次数：{{maxFollowUpCount}}。要求：score 必须是 0-100 整数；nextAction 只能是 FOLLOW_UP、NEXT_QUESTION、NEXT_STAGE、FINISH；followUpCount >= maxFollowUpCount 时禁止 FOLLOW_UP；FOLLOW_UP 时 followUpQuestion 必须紧扣原始主问题和候选人回答，且必须是 Java 技术面试追问；不允许出现“假设原问题”“如果你有具体问题请提供”“由于没有上下文”等话术。只输出 JSON：{"score":80,"comment":"中文点评","nextAction":"FOLLOW_UP","followUpQuestion":"追问内容","followUpReason":"追问原因","knowledgePoints":"相关知识点"}',
  template_content = '你是资深 Java 面试官。请一次性完成评分、点评、流程决策，并在需要时生成一个追问。原始主问题：{{rootQuestionContent}}。当前问题：{{currentQuestionContent}}。参考答案：{{referenceAnswer}}。候选人回答：{{userAnswer}}。当前阶段：{{stageName}}。历史摘要：{{historySummary}}。当前追问次数：{{followUpCount}}。最大追问次数：{{maxFollowUpCount}}。要求：score 必须是 0-100 整数；nextAction 只能是 FOLLOW_UP、NEXT_QUESTION、NEXT_STAGE、FINISH；followUpCount >= maxFollowUpCount 时禁止 FOLLOW_UP；FOLLOW_UP 时 followUpQuestion 必须紧扣原始主问题和候选人回答，且必须是 Java 技术面试追问；不允许出现“假设原问题”“如果你有具体问题请提供”“由于没有上下文”等话术。只输出 JSON：{"score":80,"comment":"中文点评","nextAction":"FOLLOW_UP","followUpQuestion":"追问内容","followUpReason":"追问原因","knowledgePoints":"相关知识点"}',
  variables = 'rootQuestionContent,currentQuestionContent,questionContent,referenceAnswer,userAnswer,currentStage,stageName,historySummary,followUpCount,maxFollowUpCount,knowledgePoints'
WHERE scene = 'INTERVIEW_ANSWER_EVALUATE';

UPDATE prompt_template
SET
  content = '你是资深 Java 面试官。请基于以下上下文生成一个追问。原始主问题：{{rootQuestionContent}}。当前问题：{{currentQuestionContent}}。参考答案：{{referenceAnswer}}。候选人回答：{{userAnswer}}。AI 评分点评：{{aiComment}}。当前阶段：{{stageName}}。历史摘要：{{historySummary}}。追问必须紧扣原始主问题和候选人回答，不能换题；必须指出候选人回答中具体缺失或错误的点；只生成一个更深入的问题；不要重复原问题；不要编造“假设原问题”；不要说“请提供具体问题”；不允许跳到团队协作、用户增长、市场运营等非 Java 技术面试主题。只返回 JSON：{"followUpQuestion":"追问内容","reason":"追问原因","relatedToOriginalQuestion":true}',
  template_content = '你是资深 Java 面试官。请基于以下上下文生成一个追问。原始主问题：{{rootQuestionContent}}。当前问题：{{currentQuestionContent}}。参考答案：{{referenceAnswer}}。候选人回答：{{userAnswer}}。AI 评分点评：{{aiComment}}。当前阶段：{{stageName}}。历史摘要：{{historySummary}}。追问必须紧扣原始主问题和候选人回答，不能换题；必须指出候选人回答中具体缺失或错误的点；只生成一个更深入的问题；不要重复原问题；不要编造“假设原问题”；不要说“请提供具体问题”；不允许跳到团队协作、用户增长、市场运营等非 Java 技术面试主题。只返回 JSON：{"followUpQuestion":"追问内容","reason":"追问原因","relatedToOriginalQuestion":true}',
  variables = 'rootQuestionContent,currentQuestionContent,questionContent,referenceAnswer,userAnswer,aiComment,currentStage,stageName,historySummary,followUpCount,maxFollowUpCount,knowledgePoints'
WHERE scene = 'INTERVIEW_FOLLOW_UP_GENERATE';

UPDATE prompt_template
SET
  content = '你是 Java 面试教练。请基于面试记录 {{historySummary}} 生成中文结构化报告。只输出 JSON，不要 Markdown，不要代码块，不要解释文字。字段固定：{"totalScore":82,"summary":"总分来源说明","strengths":[],"weakPoints":[],"mainProblems":[],"projectProblems":[],"reviewSuggestions":[],"recommendedQuestions":[],"qaReview":[],"stageScores":{},"reportContent":"报告正文"}',
  template_content = '你是 Java 面试教练。请基于面试记录 {{historySummary}} 生成中文结构化报告。只输出 JSON，不要 Markdown，不要代码块，不要解释文字。字段固定：{"totalScore":82,"summary":"总分来源说明","strengths":[],"weakPoints":[],"mainProblems":[],"projectProblems":[],"reviewSuggestions":[],"recommendedQuestions":[],"qaReview":[],"stageScores":{},"reportContent":"报告正文"}',
  variables = 'historySummary,targetPosition,experienceLevel,industryDirection,difficulty,resumeContent,projectContent'
WHERE scene = 'INTERVIEW_REPORT_GENERATE';
