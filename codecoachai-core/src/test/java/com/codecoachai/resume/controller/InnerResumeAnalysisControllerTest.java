package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.service.FileContentService;
import com.codecoachai.resume.service.extractor.ResumeTextExtractorDispatcher;
import com.codecoachai.resume.service.support.ResumeImportNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InnerResumeAnalysisControllerTest {

    @Mock
    private ResumeAnalysisRecordMapper analysisRecordMapper;
    @Mock
    private FileContentService fileContentService;
    @Mock
    private ResumeTextExtractorDispatcher textExtractorDispatcher;

    private InnerResumeAnalysisController controller;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        if (TableInfoHelper.getTableInfo(ResumeAnalysisRecord.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    ResumeAnalysisRecord.class);
        }
    }

    @BeforeEach
    void setUp() {
        controller = new InnerResumeAnalysisController(
                analysisRecordMapper,
                fileContentService,
                textExtractorDispatcher,
                new ResumeImportNormalizer(new ObjectMapper()));
    }

    @Test
    void callbackUsesPersistedRawTextForSourceHashWhenPayloadOmitsIt() throws Exception {
        String rawText = "张三 Java 后端工程师";
        ResumeAnalysisRecord existing = new ResumeAnalysisRecord();
        existing.setId(101L);
        existing.setUserId(10L);
        existing.setRawText(rawText);
        existing.setParseStatus("PARSING");
        existing.setDeleted(0);
        when(analysisRecordMapper.selectById(101L)).thenReturn(existing);
        when(analysisRecordMapper.update(isNull(), any())).thenReturn(1);

        InnerResumeAnalysisController.CompleteDTO dto = new InnerResumeAnalysisController.CompleteDTO();
        dto.setParseStatus("WAIT_CONFIRM");
        dto.setStructuredJson(validStructuredJson());

        controller.completeParseForTask(101L, dto);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<LambdaUpdateWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(analysisRecordMapper).update(isNull(), wrapperCaptor.capture());
        String expectedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rawText.getBytes(StandardCharsets.UTF_8)));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(expectedHash));
    }

    @Test
    void failedCallbackClearsEveryStructuredResultField() {
        ResumeAnalysisRecord existing = new ResumeAnalysisRecord();
        existing.setId(102L);
        existing.setUserId(10L);
        existing.setParseStatus("PARSING");
        existing.setDeleted(0);
        when(analysisRecordMapper.selectById(102L)).thenReturn(existing);
        when(analysisRecordMapper.update(isNull(), any())).thenReturn(1);

        InnerResumeAnalysisController.CompleteDTO dto = new InnerResumeAnalysisController.CompleteDTO();
        dto.setParseStatus("failed");
        dto.setStructuredJson(validStructuredJson());
        dto.setErrorMessage("upstream error");

        controller.completeParseForTask(102L, dto);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<LambdaUpdateWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(analysisRecordMapper).update(isNull(), wrapperCaptor.capture());
        String sqlSet = wrapperCaptor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("parse_status"));
        assertTrue(sqlSet.contains("structured_json"));
        assertTrue(sqlSet.contains("schema_version"));
        assertTrue(sqlSet.contains("quality_report_json"));
        assertTrue(sqlSet.contains("generated_at"));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue("FAILED"));
        assertFalse(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(validStructuredJson()));
    }

    private String validStructuredJson() {
        return """
                {
                  "schemaVersion": "resume-import-v1",
                  "basicInfo": {"name": "张三", "phone": "", "email": "", "location": "上海"},
                  "targetPosition": "Java 后端开发工程师",
                  "summary": "负责后端服务开发。",
                  "skills": ["Java"],
                  "workExperiences": [],
                  "projectExperiences": [],
                  "educationExperiences": []
                }
                """;
    }
}
