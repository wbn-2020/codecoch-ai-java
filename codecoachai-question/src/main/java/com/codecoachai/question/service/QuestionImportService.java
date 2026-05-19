package com.codecoachai.question.service;

import com.codecoachai.question.domain.entity.Question;
import java.io.InputStream;
import java.util.List;
import lombok.Data;

/**
 * 题目批量导入服务接口。
 * 支持格式：Excel(.xlsx) / Markdown(.md) / Word(.docx) / PDF(.pdf)
 */
public interface QuestionImportService {

    /**
     * 解析文件并导入题目。
     *
     * @param fileName   文件名（用于判断格式）
     * @param inputStream 文件流
     * @param importedBy  导入人 userId
     * @return 导入结果
     */
    ImportResult importQuestions(String fileName, InputStream inputStream, Long importedBy);

    @Data
    class ImportResult {
        private Long batchId;
        private int totalCount;
        private int successCount;
        private int failCount;
        private int duplicateCount;
        private List<ImportError> errors;
    }

    @Data
    class ImportError {
        private int rowIndex;
        private String title;
        private String reason;
    }

    @Data
    class ParsedQuestion {
        private String title;
        private String content;
        private String referenceAnswer;
        private String analysis;
        private String difficulty;
        private String questionType;
        private String experienceLevel;
        private String categoryName;
        private String tags;
    }
}
