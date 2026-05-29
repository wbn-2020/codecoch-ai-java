# Vector RAG And Dedupe Evaluation

This file is a lightweight baseline for tuning Qdrant-backed question dedupe and personal knowledge RAG. It is intentionally small enough to run manually during local development, then expand into automated fixtures later.

## Question Dedupe Samples

Use these cases after running `/admin/questions/embedding/rebuild`. A good run should put positive pairs above the semantic review threshold and keep negative pairs out of pending duplicate reviews.

| Case | Source | Candidate | Expected | Notes |
| --- | --- | --- | --- | --- |
| QD-01 | HashMap 扩容机制是什么？ | HashMap 在 JDK 8 中如何 resize？ | duplicate | Same intent, wording differs. |
| QD-02 | MySQL 索引失效的常见原因有哪些？ | 哪些写法会导致 MySQL 不走索引？ | duplicate | Same intent with broader phrasing. |
| QD-03 | Redis 缓存穿透怎么解决？ | Redis 缓存击穿和雪崩有什么区别？ | not_duplicate | Related but different answer scope. |
| QD-04 | Spring Bean 生命周期有哪些阶段？ | Spring MVC 请求处理流程是什么？ | not_duplicate | Same framework, different concept. |
| QD-05 | JVM GC Roots 包括哪些对象？ | Java 对象什么时候会被垃圾回收？ | review | Related enough for manual review, not automatic merge. |

Suggested initial bands:

| Band | Final Score | Action |
| --- | --- | --- |
| Strong | >= 88 | High-confidence review item. |
| Review | 78-88 | Manual review. |
| Ignore | < 78 | Do not show by default. |

## Personal Knowledge RAG Samples

Create three personal documents and rebuild vectors before testing.

### Document PK-01: HashMap Notes

```text
# HashMap
HashMap uses an array plus linked lists or red-black trees. Resize happens when size exceeds threshold, which is capacity multiplied by load factor. JDK 8 keeps relative order split by old capacity during resize.
```

### Document PK-02: Redis Cache Notes

```text
# Redis Cache Protection
Cache penetration is commonly handled with null-value cache and Bloom filters. Cache breakdown can use mutex rebuild or logical expiration. Cache avalanche needs randomized TTL and degradation plans.
```

### Document PK-03: MySQL Index Notes

```text
# MySQL Index Usage
Functions on indexed columns, leading wildcard LIKE, implicit type conversion, and low-selectivity conditions may prevent effective index usage. Composite indexes follow the leftmost prefix rule.
```

| Case | Query | Expected Source | Expected Behavior |
| --- | --- | --- | --- |
| RAG-01 | HashMap 什么时候扩容？ | PK-01 | Answer cites HashMap Notes and mentions threshold/load factor. |
| RAG-02 | 缓存穿透怎么处理？ | PK-02 | Answer cites Redis Cache Notes and mentions Bloom filter or null-value cache. |
| RAG-03 | LIKE 为什么可能不走索引？ | PK-03 | Answer cites MySQL Index Notes and mentions leading wildcard. |
| RAG-04 | Kafka ISR 是什么？ | none | Should say the knowledge base lacks enough evidence. |

## Manual Run Checklist

1. Rebuild question vectors with `POST /admin/questions/embedding/rebuild`.
2. Check `GET /admin/questions/embedding/stats`; failed count should be zero before evaluation.
3. Create the three personal knowledge documents above, then call `POST /agent/knowledge/vectors/rebuild`.
4. Check `GET /admin/vector-store/health`; both core collections should exist and dimensions should match.
5. Run the RAG queries with `POST /agent/knowledge/ask` and record top reference score, cited source, and whether the answer used only retrieved evidence.
6. If any vector indexing fails, run `POST /admin/questions/embedding/retry-failed` or `POST /agent/knowledge/vectors/retry-failed` before tuning thresholds.
