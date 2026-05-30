# Vector RAG And Dedupe Runbook

This runbook validates the Qdrant-backed question dedupe and personal knowledge RAG flow in a local development environment.

## Scope

- Keep MySQL as the business database.
- Keep Qdrant as the vector store for `question_embedding` and `personal_knowledge_chunk`.
- Use temporary shell environment variables for local runs. Do not use `setx` or edit machine/user environment variables for this workflow.
- Do not delete or recreate Qdrant collections during validation unless you have reviewed the affected collection names and intentionally want to rebuild vector data.

## Local Infra

Start the required infrastructure from the backend repository:

```powershell
docker compose up -d mysql redis nacos qdrant
```

The Compose MySQL service initializes fresh volumes with `MYSQL_DATABASE=codecoachai_v1`, matching the Nacos datasource files and Flyway target database. This variable is only applied when the `mysql-data` volume is created for the first time. If the volume already existed from an older setup, verify that `codecoachai_v1` exists before running Flyway or starting services.

Optional services for broader system testing:

```powershell
docker compose up -d rocketmq-namesrv rocketmq-broker elasticsearch
```

Ports used by the vector path:

| Service | Default URL |
| --- | --- |
| Nacos | `http://127.0.0.1:8848` |
| Qdrant HTTP | `http://127.0.0.1:6333` |
| MySQL | `127.0.0.1:3306` |

## Temporary Runtime Variables

Use these in the terminal that starts the backend services:

```powershell
$env:CODECOACHAI_VECTOR_ENABLED = "true"
$env:QDRANT_BASE_URL = "http://127.0.0.1:6333"
$env:QDRANT_API_KEY = ""
$env:DASHSCOPE_EMBEDDING_MODEL = "text-embedding-v3"
$env:DASHSCOPE_API_KEY = "<your DashScope key>"
```

`CODECOACHAI_VECTOR_ENABLED=false` is still the safe default in `.env.example`; enable it only for vector validation.

## Database Migration

Apply the migration that adds vector observability fields:

```powershell
mvn flyway:migrate
```

Relevant migration:

- `sql/migration/V4_013__vector_index_observability_fields.sql`
- `sql/migration/V4_014__question_duplicate_review_score_detail.sql`
- `sql/migration/V4_015__vector_delete_outbox.sql`

Expected schema additions:

- `question_embedding.embedding_model`
- `question_embedding.embedding_dimension`
- `question_embedding.indexed_at`
- `question_embedding.index_status`
- `question_embedding.last_error`
- `question_duplicate_review.score_band`
- `question_duplicate_review.score_detail_json`
- `personal_knowledge_chunk.embedding_model`
- `personal_knowledge_chunk.embedding_dimension`
- `personal_knowledge_chunk.indexed_at`
- `personal_knowledge_chunk.index_status`
- `personal_knowledge_chunk.last_error`
- `vector_delete_outbox` for durable Qdrant point-delete retries

Qdrant point ids must be valid UUIDs or unsigned integers. The application derives stable UUID point ids from business ids for both `question_embedding` and `personal_knowledge_chunk`, so upsert and delete compensation use the same id format.

## Service Startup

For focused validation, start at least these backend services:

```powershell
mvn -pl codecoachai-gateway spring-boot:run
mvn -pl codecoachai-auth spring-boot:run
mvn -pl codecoachai-user spring-boot:run
mvn -pl codecoachai-question spring-boot:run
mvn -pl codecoachai-ai spring-boot:run
```

Start the frontend from `C:\my-claude\CodeCoachAI-vue` when you need to validate the admin buttons:

```powershell
npm run dev
```

## Validation Checklist

1. Check Qdrant directly:

```powershell
Invoke-RestMethod http://127.0.0.1:6333/healthz
```

2. Check the app vector health endpoint:

```text
GET /admin/vector-store/health
POST /admin/vector-store/delete-outbox/retry?limit=500
```

Expected:

- `checks.enabled=true`
- `checks.collectionsPresent=true` after vectors have been built at least once
- `checks.dimensionMatched=true`
- `checks.deleteOutboxClear=true` when no Qdrant delete compensation is pending
- `deleteOutbox.retryable=0` when all stale vector point deletes have been retried successfully

3. Rebuild or retry question vectors:

```text
POST /admin/questions/embedding/rebuild
GET  /admin/questions/embedding/stats
POST /admin/questions/embedding/retry-failed
```

Expected:

- `index_status=INDEXED` for active questions that can be embedded.
- Failed rows keep `index_status=FAILED` and `last_error` for diagnosis.

4. Validate question dedupe:

```text
POST /admin/questions/check-duplicate
GET  /admin/question-duplicate-reviews
```

Expected:

- Semantic candidates come from Qdrant search over existing points.
- Review rows include vector/text/final score parts for `SEMANTIC_SIMILAR`.
- New semantic rows persist `scoreBand` and structured score parts, while old rows can still be read from `matchReason`.

5. Rebuild or retry personal knowledge vectors:

```text
POST /agent/knowledge/vectors/rebuild
POST /agent/knowledge/vectors/retry-failed
```

Expected:

- Chunks move from `PENDING` to `INDEXED`.
- Failed chunks keep `FAILED` and `last_error`.
- `vectorDeleted` reports Qdrant point deletes retried from `vector_delete_outbox`.
- The knowledge-base chunk drawer shows each chunk's index status, embedding model, dimension, indexed time, and any last error.

6. Validate personal RAG:

```text
GET  /agent/knowledge/search
POST /agent/knowledge/ask
```

Expected:

- Search merges Qdrant vector hits with MySQL keyword fallback.
- Ask responses include references and citation validation fields:
  - `citationValid`
  - `citedReferenceNumbers`
  - `invalidReferenceNumbers`
- If no references pass the threshold, the answer says the personal knowledge base lacks enough relevant content.

## Tuning Loop

Use `docs/vector-rag-dedupe-evaluation.md` as the first manual sample set.

Recommended sequence:

1. Run question vector rebuild.
2. Run the dedupe samples and export review scores.
3. Tune semantic threshold and weights only after recording false positives and false negatives.
4. Run personal knowledge rebuild.
5. Run the RAG samples and record top reference score, citation validity, and whether unsupported claims appear.
6. Retry failed vector jobs before changing thresholds.

## Troubleshooting

| Symptom | Likely Cause | Check |
| --- | --- | --- |
| Vector health disabled | `CODECOACHAI_VECTOR_ENABLED` is not true in the backend process | `GET /admin/vector-store/health` |
| Collection dimension mismatch | Embedding model changed after collection creation | Compare health endpoint collection dimensions with current embedding response dimension |
| All vector writes fail | Qdrant not reachable or API key mismatch | `http://127.0.0.1:6333/healthz`, backend logs, `last_error` |
| Deleted questions/chunks still appear in vector search | Delete compensation is pending or failed | `GET /admin/vector-store/health`, then `POST /admin/vector-store/delete-outbox/retry?limit=500` |
| RAG answer has no citations | Model ignored citation instruction | Inspect `citationValid=false` and references returned by `/agent/knowledge/ask` |
| Dedupe misses obvious semantic duplicates | Existing questions are not indexed or threshold too high | `/admin/questions/embedding/stats`, review score distribution |

If `vector_delete_outbox.last_error` says the point id is not an unsigned integer or UUID, it is a stale record from the old prefixed id format. Rebuild the affected vectors first, then mark or clean only those confirmed stale outbox rows after reviewing the exact `collection_name` and `point_id` values.
