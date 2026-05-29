package com.codecoachai.common.vector.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "codecoachai.vector")
public class VectorStoreProperties {

    private boolean enabled = false;

    private String provider = "qdrant";

    private String baseUrl = "http://127.0.0.1:6333";

    private String apiKey;

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration requestTimeout = Duration.ofSeconds(15);

    private int defaultLimit = 10;

    /**
     * 检索分数归一化策略。
     * <p>NONE（默认）：原样透传向量库返回的分数。当 embedding 向量已 L2 归一化时，
     * 余弦相似度本就落在 [0,1] 正区间，现有阈值（0.78/0.88 等）直接可用，无需再映射。
     * <p>COSINE_TO_UNIT：将余弦相似度 [-1,1] 线性映射到 [0,1]，公式 (score+1)/2。
     * 仅当确认 embedding 向量未归一化、检索分数可能为负时启用；启用后必须重新标定所有阈值。
     * <p>注意：两套集合（question_embedding / personal_knowledge_chunk）必须使用同一策略。
     */
    private ScoreNormalization scoreNormalization = ScoreNormalization.NONE;

    public enum ScoreNormalization {
        COSINE_TO_UNIT,
        NONE
    }
}
