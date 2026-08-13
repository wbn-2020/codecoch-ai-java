package com.codecoachai.question.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.question.config.QuestionDocxZipSecurity;
import com.codecoachai.question.config.QuestionImportProperties;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.service.QuestionDuplicateService;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@ResourceLock("apache-poi-zip-secure-file")
class QuestionDocxZipSecurityTest {

    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionEmbeddingIndexService questionEmbeddingIndexService;
    @Mock
    private QuestionDuplicateService questionDuplicateService;

    private double previousMinInflateRatio;
    private long previousMaxEntrySize;
    private long previousMaxTextSize;
    private long previousMaxFileCount;

    @BeforeEach
    void savePoiGlobalLimits() {
        previousMinInflateRatio = ZipSecureFile.getMinInflateRatio();
        previousMaxEntrySize = ZipSecureFile.getMaxEntrySize();
        previousMaxTextSize = ZipSecureFile.getMaxTextSize();
        previousMaxFileCount = ZipSecureFile.getMaxFileCount();
    }

    @AfterEach
    void restorePoiGlobalLimits() {
        synchronized (ZipSecureFile.class) {
            ZipSecureFile.setMinInflateRatio(previousMinInflateRatio);
            ZipSecureFile.setMaxEntrySize(previousMaxEntrySize);
            ZipSecureFile.setMaxTextSize(previousMaxTextSize);
            ZipSecureFile.setMaxFileCount(previousMaxFileCount);
        }
    }

    @Test
    void appliesConfiguredPoiArchiveLimits() {
        QuestionImportProperties properties = new QuestionImportProperties();
        properties.setDocxMinInflateRatio(0.02d);
        properties.setDocxMaxEntryBytes(2L * 1024L * 1024L);
        properties.setDocxMaxTextBytes(1024L * 1024L);
        properties.setDocxMaxFileCount(250L);

        new QuestionDocxZipSecurity(properties).apply();

        assertEquals(0.02d, ZipSecureFile.getMinInflateRatio());
        assertEquals(2L * 1024L * 1024L, ZipSecureFile.getMaxEntrySize());
        assertEquals(1024L * 1024L, ZipSecureFile.getMaxTextSize());
        assertEquals(250L, ZipSecureFile.getMaxFileCount());
    }

    @Test
    void rejectsDocxWhoseExpandedEntryExceedsConfiguredLimit() throws Exception {
        byte[] docx = largeDocx();
        QuestionImportProperties properties = new QuestionImportProperties();
        properties.setDocxMaxEntryBytes(64L * 1024L);
        properties.setDocxMaxTextBytes(64L * 1024L);
        QuestionDocxZipSecurity zipSecurity = new QuestionDocxZipSecurity(properties);
        QuestionImportServiceImpl service = new QuestionImportServiceImpl(
                questionMapper,
                questionEmbeddingIndexService,
                questionDuplicateService,
                properties,
                zipSecurity);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.importQuestions(
                        "questions.docx",
                        new ByteArrayInputStream(docx),
                        1L,
                        true));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(questionMapper, never()).insert(any(Question.class));
    }

    private byte[] largeDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("A".repeat(256 * 1024));
            document.write(output);
            return output.toByteArray();
        }
    }
}
