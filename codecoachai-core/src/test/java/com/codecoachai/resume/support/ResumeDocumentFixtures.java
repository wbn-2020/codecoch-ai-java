package com.codecoachai.resume.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Shared fixture for the resume document v2 logic tests. Kept identical to
 * {@code tests/unit/features/resume-workbench-document.test.ts} so both sides are proven against
 * the same legacy resume.
 */
final class ResumeDocumentFixtures {

    static final String SUMMARY = "八年后端与分布式系统经验，主导过日活千万级交易系统的核心链路重构与稳定性治理。"
            + "曾负责可观测平台建设，将线上故障平均定位时间从小时级压缩到分钟级。"
            + "持有 PMP 与阿里云架构师认证，长期关注高并发场景下的成本与效率平衡。";

    static final String WORK_EXPERIENCE = "字节跳动 · 后端开发    2021.03-至今\n"
            + "负责交易系统稳定性建设，核心接口 P99 降低 40%。\n"
            + "搭建可观测平台，故障定位时间缩短 60%。\n\n"
            + "美团 · Java 开发    2018.07-2021.02\n"
            + "参与订单服务拆分，支撑峰值 QPS 提升 3 倍。";

    static final String EDUCATION_EXPERIENCE = "华中科技大学    2014.09-2018.06\n计算机科学与技术 本科";

    static final String SKILLS = "Java,Spring,MySQL,Redis,Vue,Kafka,Docker,Kubernetes,ELK";

    private ResumeDocumentFixtures() {
    }

    static ObjectNode legacy(ObjectMapper mapper) {
        ObjectNode legacy = mapper.createObjectNode();
        legacy.put("resumeName", "后端简历");
        legacy.put("realName", "张伟");
        legacy.put("email", "zhangwei@example.com");
        legacy.put("phone", "13800000000");
        legacy.put("targetPosition", "高级 Java 工程师");
        legacy.put("summary", SUMMARY);
        legacy.put("skills", SKILLS);
        legacy.put("workSummary", "");
        legacy.put("workExperience", WORK_EXPERIENCE);
        legacy.put("education", "");
        legacy.put("educationExperience", EDUCATION_EXPERIENCE);
        return legacy;
    }

    static List<JsonNode> projects(ObjectMapper mapper) {
        ObjectNode project = mapper.createObjectNode();
        project.put("projectId", 101);
        project.put("projectName", "CodeCoachAI 模拟面试");
        project.put("projectTime", "2025.01-2025.06");
        project.put("role", "后端负责人");
        project.put("techStack", "Java, Spring, MySQL");
        project.put("projectBackground", "面向求职者的 AI 面试训练平台。");
        project.put("coreFeatures", "支持多轮问答与评分报告生成。");
        project.put("technicalChallenges", "高并发下会话状态一致性。");
        project.put("optimizationResult", "面试完成率提升 25%。");
        project.put("extraInfo", "开源项目。");
        return List.<JsonNode>of(project);
    }

    static ObjectNode presentation(ObjectMapper mapper) {
        ObjectNode config = mapper.createObjectNode();
        config.put("templateCode", "ATS_PROJECT_FOCUS");
        config.putArray("sectionOrder")
                .add("summary").add("projects").add("skills").add("experience").add("education");
        config.putArray("hiddenSections").add("education");
        return config;
    }

    static ObjectNode document(ObjectMapper mapper) {
        return ResumeDocumentMigrator.toDocument(
                mapper, legacy(mapper), projects(mapper), presentation(mapper));
    }

    static ObjectNode section(ObjectMapper mapper, JsonNode document, String builtinKey) {
        for (JsonNode section : document.path("sections")) {
            if (builtinKey.equals(section.path("builtinKey").asText(""))) {
                return (ObjectNode) section;
            }
        }
        throw new AssertionError("missing builtin section: " + builtinKey);
    }

    static ArrayNode items(JsonNode section) {
        return (ArrayNode) section.path("content").path("items");
    }
}
