package com.codecoachai.resume.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.dto.ParsedResumeStructuredDTO;
import com.codecoachai.resume.service.support.ResumeImportNormalizer.NormalizationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResumeImportNormalizerTest {

    private ResumeImportNormalizer normalizer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        normalizer = new ResumeImportNormalizer(objectMapper);
    }

    @Test
    void normalizesContactsTextAndDuplicateProjectsWithoutJsonEncoding() {
        NormalizationResult result = normalizer.normalize(validStructuredJson(
                "***@example.invalid",
                "138****0000",
                """
                AI 解析简历: 5 年 Java 后端经验
                targetPosition: 高级 Java 后端工程师
                """,
                true));

        ParsedResumeStructuredDTO normalized = result.structuredResume();
        assertEquals(ParsedResumeStructuredDTO.CURRENT_SCHEMA_VERSION, normalized.getSchemaVersion());
        assertNull(normalized.getBasicInfo().getEmail());
        assertNull(normalized.getBasicInfo().getPhone());
        assertFalse(normalized.getSummary().contains("AI 解析简历"));
        assertFalse(normalized.getSummary().contains("targetPosition"));
        assertEquals(1, normalized.getProjectExperiences().size());
        assertEquals(1, result.qualityReport().getDuplicateProjectsRemoved());
        assertTrue(result.qualityReport().getMissingContacts().contains("邮箱"));
        assertTrue(result.qualityReport().getMissingContacts().contains("手机号"));
        assertTrue(result.workExperienceText().contains("澄明云科有限公司"));
        assertTrue(result.educationExperienceText().contains("华东理工大学"));
        assertFalse(result.workExperienceText().startsWith("["));
        assertFalse(result.workExperienceText().contains("\"company\""));
        assertFalse(result.educationExperienceText().contains("\"school\""));
        assertTrue(result.qualityReport().isConfirmable());
    }

    @Test
    void preservesValidatedRealContactsAndMasksOnlyPreview() {
        NormalizationResult result = normalizer.normalize(validStructuredJson(
                "candidate@career.dev",
                "+8613812345678",
                "负责 Java 后端服务设计和稳定性交付。",
                false));

        assertEquals("candidate@career.dev", result.structuredResume().getBasicInfo().getEmail());
        assertEquals("+8613812345678", result.structuredResume().getBasicInfo().getPhone());
        assertTrue(result.qualityReport().getMissingContacts().isEmpty());
        assertTrue(result.qualityReport().getWritePreview().stream()
                .anyMatch(item -> "email".equals(item.getFieldKey())
                        && item.getValue().startsWith("ca***@")));
        assertTrue(result.qualityReport().getWritePreview().stream()
                .anyMatch(item -> "phone".equals(item.getFieldKey())
                        && item.getValue().contains("****")));
    }

    @Test
    void rejectsUnsupportedSchemaAndSecondaryEncodedArrays() {
        String unsupported = validStructuredJson("", "", "", false)
                .replace("resume-import-v1", "resume-import-v0");
        String encodedArray = validStructuredJson("", "", "", false)
                .replace("\"workExperiences\":[{", "\"workExperiences\":\"[{");

        assertThrows(BusinessException.class, () -> normalizer.normalize(unsupported));
        assertThrows(BusinessException.class, () -> normalizer.normalize(encodedArray));
    }

    @Test
    void rejectsFieldLevelJsonStringsInTextAndStringArrayFields() throws Exception {
        JsonNode skillsRoot = objectMapper.readTree(validStructuredJson("", "", "", false));
        ((ArrayNode) skillsRoot.path("skills")).set(0, objectMapper.getNodeFactory().textNode("[\"Java\"]"));

        JsonNode techStackRoot = objectMapper.readTree(validStructuredJson("", "", "", false));
        ObjectNode project = (ObjectNode) techStackRoot.path("projectExperiences").get(0);
        ((ArrayNode) project.path("techStack")).set(0,
                objectMapper.getNodeFactory().textNode("{\"name\":\"Java\"}"));

        JsonNode summaryRoot = objectMapper.readTree(validStructuredJson("", "", "", false));
        ((ObjectNode) summaryRoot).put("summary", "{\"experienceYears\":5}");

        assertThrows(BusinessException.class,
                () -> normalizer.normalize(objectMapper.writeValueAsString(skillsRoot)));
        assertThrows(BusinessException.class,
                () -> normalizer.normalize(objectMapper.writeValueAsString(techStackRoot)));
        assertThrows(BusinessException.class,
                () -> normalizer.normalize(objectMapper.writeValueAsString(summaryRoot)));
    }

    @Test
    void cleansFieldLevelJsonStringsWhenNormalizingDirectDtoInput() throws Exception {
        ParsedResumeStructuredDTO source = objectMapper.readValue(
                validStructuredJson("", "", "可靠的后端交付经验。", false),
                ParsedResumeStructuredDTO.class);
        source.setSummary("{\"experienceYears\":5}");
        source.getSkills().set(0, "[\"Java\"]");
        source.getProjectExperiences().get(0).getTechStack().set(0, "{\"name\":\"Java\"}");

        NormalizationResult result = normalizer.normalize(source);

        assertNull(result.structuredResume().getSummary());
        assertFalse(result.structuredResume().getSkills().stream()
                .anyMatch(item -> item.startsWith("[") || item.startsWith("{")));
        assertFalse(result.structuredResume().getProjectExperiences().get(0).getTechStack().stream()
                .anyMatch(item -> item.startsWith("[") || item.startsWith("{")));
        assertTrue(result.qualityReport().getWarnings().stream()
                .anyMatch(item -> item.contains("JSON 数组或对象字符串")));
    }

    @Test
    void validButEmptyContractProducesConfirmationBlocker() {
        NormalizationResult result = normalizer.normalize("""
                {
                  "schemaVersion":"resume-import-v1",
                  "basicInfo":{"name":"","phone":"","email":"","location":""},
                  "targetPosition":"",
                  "summary":"",
                  "skills":[],
                  "workExperiences":[],
                  "projectExperiences":[],
                  "educationExperiences":[]
                }
                """);

        assertFalse(result.qualityReport().isConfirmable());
        assertEquals("BLOCKED", result.qualityReport().getValidationStatus());
        assertFalse(result.qualityReport().getBlockers().isEmpty());
    }

    @Test
    void sourceHashIsStableAndSensitiveToContent() {
        String first = normalizer.sourceHash("same resume text");
        String second = normalizer.sourceHash("same resume text");
        String different = normalizer.sourceHash("different resume text");

        assertEquals(first, second);
        assertEquals(64, first.length());
        assertFalse(first.equals(different));
    }

    private String validStructuredJson(
            String email, String phone, String summary, boolean duplicateProject) {
        String project = """
                {
                  "projectName":"学习平台稳定性治理",
                  "period":"2025.03-2026.02",
                  "background":"在线学习核心链路稳定性治理",
                  "role":"后端负责人",
                  "description":"负责学习进度和作业链路改造",
                  "techStack":["Java","Spring Boot","Redis"],
                  "responsibilities":["拆分核心链路","建设幂等消费"],
                  "coreFeatures":["学习进度状态机"],
                  "technicalDifficulties":["异步消息一致性"],
                  "optimizationResults":["核心接口稳定性提升"],
                  "achievements":["完成稳定性治理闭环"]
                }
                """;
        return """
                {
                  "schemaVersion":"resume-import-v1",
                  "basicInfo":{
                    "name":"张伟",
                    "phone":"%s",
                    "email":"%s",
                    "location":"上海"
                  },
                  "targetPosition":"高级 Java 后端工程师",
                  "summary":"%s",
                  "skills":["Java","Spring Boot","Java"],
                  "workExperiences":[{
                    "company":"澄明云科有限公司",
                    "position":"高级 Java 后端工程师",
                    "period":"2023.04-至今",
                    "description":"负责核心服务建设",
                    "responsibilities":["服务设计","稳定性治理"],
                    "achievements":["交付核心业务链路"]
                  }],
                  "projectExperiences":[%s%s],
                  "educationExperiences":[{
                    "school":"华东理工大学",
                    "degree":"本科",
                    "major":"软件工程",
                    "period":"2016.09-2020.06",
                    "description":""
                  }]
                }
                """.formatted(
                jsonEscape(phone),
                jsonEscape(email),
                jsonEscape(summary),
                project,
                duplicateProject ? "," + project : "");
    }

    private String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
