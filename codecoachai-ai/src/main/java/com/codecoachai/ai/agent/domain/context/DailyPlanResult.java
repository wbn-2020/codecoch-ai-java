package com.codecoachai.ai.agent.domain.context;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class DailyPlanResult {

    private String summary;
    private List<FocusSkill> focusSkills = new ArrayList<>();
    private List<PlanTask> tasks = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonDeserialize(using = FocusSkill.Deserializer.class)
    public static class FocusSkill {
        private String code;
        private String name;

        /**
         * 模型偶尔把 focusSkills 输出为字符串数组（如 ["系统设计"]）而非对象数组，
         * 自定义反序列化兼容两种形态：字符串按 name 承载、code 留空，避免整份计划解析失败。
         */
        public static class Deserializer extends StdDeserializer<FocusSkill> {
            public Deserializer() {
                super(FocusSkill.class);
            }

            @Override
            public FocusSkill deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                if (parser.currentToken() == JsonToken.VALUE_STRING) {
                    FocusSkill skill = new FocusSkill();
                    skill.name = parser.getText();
                    return skill;
                }
                FocusSkill skill = new FocusSkill();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String field = parser.currentName();
                    parser.nextToken();
                    if ("code".equals(field)) {
                        skill.code = parser.getValueAsString();
                    } else if ("name".equals(field)) {
                        skill.name = parser.getValueAsString();
                    } else {
                        parser.skipChildren();
                    }
                }
                return skill;
            }
        }
    }

    @Data
    public static class PlanTask {
        private String candidateId;
        private String type;
        private String title;
        private String description;
        private String reason;
        private String priority;
        private Integer estimatedMinutes;
        private String relatedSkillCode;
        private String relatedSkillName;
        private String relatedBizType;
        private Long relatedBizId;
        private String actionUrl;
    }
}
