ALTER TABLE `personal_knowledge_eval_result`
  ADD COLUMN `citation_valid` TINYINT DEFAULT NULL COMMENT '1 if generated answer cites retrieved references correctly',
  ADD COLUMN `answer_grounded` TINYINT DEFAULT NULL COMMENT '1 if generated answer is grounded by valid retrieved citations',
  ADD COLUMN `answer_excerpt` VARCHAR(1000) DEFAULT NULL COMMENT 'Generated answer excerpt captured during evaluation',
  ADD COLUMN `citation_warning` VARCHAR(1000) DEFAULT NULL COMMENT 'Citation or grounding warning captured during evaluation';
