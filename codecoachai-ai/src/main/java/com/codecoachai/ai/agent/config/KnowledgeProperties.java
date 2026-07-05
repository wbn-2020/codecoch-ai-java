package com.codecoachai.ai.agent.config;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.knowledge")
public class KnowledgeProperties {
    private String collection = "personal_knowledge_chunk";
    private String chunkStrategy = "STRUCTURED_MARKDOWN_BLOCK_800_OVERLAP_80";
    private int chunkSize = 800;
    private int chunkOverlap = 80;
    private int minChunkSize = 180;
    private int embeddingBatchSize = 64;
    private int askDefaultLimit = 5;
    private long uploadMaxBytes = 8L * 1024 * 1024;
    private int uploadMaxTextChars = 100_000;
    private int uploadMaxPdfPages = 50;
    private double nearDuplicateThreshold = 0.88D;
    private double askMinScore = 0.55D;
    /**
     * 是否将知识库向量索引与近重统计异步化（移出上传主链路）。
     * <p>true（默认）：合并为单次 embedding，索引在事务提交后由专用线程池执行，上传请求快速返回。
     * <p>false：回退到改造前行为（请求线程内同步统计近重 + 提交后同步索引，embedding 会调用两次）。
     */
    private boolean asyncIndexEnabled = true;
    /**
     * 检索召回放大倍数：向量召回数量 = limit * recallMultiplier，再经 MMR 选出最终 limit 条。
     */
    private int recallMultiplier = 4;
    /**
     * MMR 多样性系数 λ：score = λ*相关性 - (1-λ)*与已选集合最大相似度。
     * 越接近 1 越偏相关性，越接近 0 越偏多样性。
     */
    private double mmrLambda = 0.7D;
    /** 是否启用 MMR 去冗余（关闭时退回纯按分排序）。 */
    private boolean mmrEnabled = true;
    /** 是否启用答案 grounding 校验（句子与引用片段相似度）。 */
    private boolean groundingCheckEnabled = true;
    /** grounding 相似度阈值，低于此值的带引用句子标记为疑似未支撑。 */
    private double groundingThreshold = 0.12D;
    private Set<String> uploadExtensions = new LinkedHashSet<>(Set.of("txt", "md", "markdown", "pdf", "docx", "doc"));

    public int safeChunkSize() {
        return Math.max(chunkSize, 1);
    }

    public int safeChunkOverlap() {
        return Math.max(Math.min(chunkOverlap, safeChunkSize() - 1), 0);
    }

    public int safeMinChunkSize() {
        return Math.max(Math.min(minChunkSize, safeChunkSize()), 1);
    }

    public int safeEmbeddingBatchSize() {
        return Math.max(embeddingBatchSize, 1);
    }

    public int safeAskDefaultLimit() {
        return Math.max(askDefaultLimit, 1);
    }

    public int safeUploadMaxTextChars() {
        return Math.max(uploadMaxTextChars, 1);
    }

    public int safeUploadMaxPdfPages() {
        return Math.max(uploadMaxPdfPages, 1);
    }

    public double safeNearDuplicateThreshold() {
        return Math.min(Math.max(nearDuplicateThreshold, 0D), 1D);
    }

    public double safeAskMinScore() {
        return Math.min(Math.max(askMinScore, 0D), 1D);
    }

    public int safeRecallMultiplier() {
        return Math.max(recallMultiplier, 1);
    }

    public double safeMmrLambda() {
        return Math.min(Math.max(mmrLambda, 0D), 1D);
    }
}
