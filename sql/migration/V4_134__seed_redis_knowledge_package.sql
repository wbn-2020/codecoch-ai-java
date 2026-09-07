-- V4_134: Phase 1 里程碑 — 知识点包「Redis」。
-- 数据模型：一个 question_group = 一个知识点（main_knowledge_point = 主知识点），
--           其下多道 question 通过 group_id 归属。分类挂到 init.sql 已有的「Redis」(category_id=7)。
-- 幂等：group/tag 用 NOT EXISTS 守卫；question 用 ON DUPLICATE KEY UPDATE；
--       关系用 SELECT qid, tid FROM DUAL WHERE NOT EXISTS(...)。
-- ID 区间：groups 5001-5006，questions 5001-5025，tags 501-510（与并发/集合等包无交集）。

-- ===== 1. 标签（10 个，高 id 避免冲突） =====
INSERT INTO question_tag (id, tag_name, status)
SELECT 501, 'Redis Data Types', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 501)
UNION ALL
SELECT 502, 'Persistence', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 502)
UNION ALL
SELECT 503, 'Cache Penetration', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 503)
UNION ALL
SELECT 504, 'Cache Consistency', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 504)
UNION ALL
SELECT 505, 'Distributed Lock', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 505)
UNION ALL
SELECT 506, 'Cluster', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 506)
UNION ALL
SELECT 507, 'Expiration and Eviction', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 507)
UNION ALL
SELECT 508, 'High Availability', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 508)
UNION ALL
SELECT 509, 'Memory Management', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 509)
UNION ALL
SELECT 510, 'Performance Tuning', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 510);

-- ===== 2. 知识点组（6 个主知识点，category_id=7 Redis） =====
INSERT INTO question_group (id, group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status)
SELECT 5001, 'Data Structures and Types', 'What are the main data types in Redis and their underlying implementations?',
       'Redis provides string, list, hash, set, sorted set (zset) as core types, plus bitmap, hyperloglog, geohash, and stream. string uses SDS (simple dynamic string) which is binary safe and pre-allocates; list is quicklist (ziplist + linked list) in recent versions; hash/zset use ziplist/listpack when small and hashtable/skiplist when large; zset combines skiplist + dict for range and point queries.',
       'Redis data types and encodings', 'MEDIUM', '考察五大基础类型及其底层编码（SDS、quicklist、ziplist/listpack、skiplist）。', 7, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 5001)
UNION ALL
SELECT 5002, 'Persistence RDB and AOF', 'How do RDB and AOF persistence work and how do you choose?',
       'RDB snapshots memory to a compact binary file via fork+bgsave, good for backup and fast restart but loses recent data. AOF logs every write command, fsync policy (always/everysec/no) trades durability for performance; it is rewritten into a minimal command set when too large. Redis 4.0+ supports hybrid persistence (RDB header + AOF incremental) for both fast load and low data loss.',
       'RDB vs AOF and hybrid persistence', 'MEDIUM', '考察两种持久化机制、fsync 策略与混合持久化取舍。', 7, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 5002)
UNION ALL
SELECT 5003, 'Cache Penetration Breakdown Avalanche', 'What are cache penetration, breakdown, and avalanche, and how do you prevent them?',
       'Penetration: queries for non-existent keys hit DB because cache and DB both miss; fix with bloom filter or caching null with short TTL. Breakdown: a single hot key expires and a burst of requests hits DB at once; fix with mutex/double-check or logical (logical) expiry. Avalanche: many keys expire simultaneously; fix with random TTL jitter, multi-level cache, and circuit breaker.',
       'Three cache failure modes and defenses', 'MEDIUM', '考察穿透/击穿/雪崩的区别与各自应对方案。', 7, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 5003)
UNION ALL
SELECT 5004, 'Cache Consistency', 'How do you keep Redis cache and the database consistent?',
       'The common cache-aside pattern updates the database first, then deletes (not updates) the cache, relying on TTL and retry for eventual consistency. Concurrent read/write and delete failures can cause inconsistency, mitigated by delayed double-delete or binlog-based synchronization (canal) for stronger guarantees. Strong consistency is costly and usually unnecessary for caches.',
       'Cache-aside consistency strategies', 'HARD', '考察缓存与数据库一致性的边界与补偿方案。', 7, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 5004)
UNION ALL
SELECT 5005, 'Distributed Lock', 'How do you implement a reliable distributed lock with Redis?',
       'Use SET key value NX EX seconds to atomically acquire the lock with a timeout, where value must be a unique token (client id) so only the owner can release it. Release must be done by a Lua script that checks the token and deletes atomically, preventing deleting another client lock. For production, Redisson provides watch-dog auto-renewal; Redlock spans multiple masters for higher safety at higher cost.',
       'Redis distributed lock correctness', 'HARD', '考察加锁原子性、唯一值、Lua 释放与看门狗续期。', 7, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 5005)
UNION ALL
SELECT 5006, 'Cluster and Sentinel', 'How do Redis Sentinel and Cluster provide high availability and sharding?',
       'Sentinel monitors masters, performs automatic failover to a replica, and notifies clients of the new master. Redis Cluster shards data across 16384 hash slots distributed among masters, supports resharding, and uses gossip for node discovery; clients get MOVED/ASK redirects on slot moves. Both improve availability, but Cluster also solves horizontal data scaling.',
       'Sentinel failover and Cluster sharding', 'HARD', '考察哨兵故障转移与集群槽位分片、重定向。', 7, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 5006);

-- ===== 3. 题目（25 道，category_id=7，group_id 指向知识点组） =====
INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, question_type, experience_level, is_high_frequency, status)
VALUES
  -- —— Data Structures and Types（组 5001）——
  (5001, 'What are the main data types in Redis and their typical use cases?', 'List the core types and give one scenario for each.',
   'Core types: string (counters, cache, session), list (queue, timeline), hash (user profile, object fields), set (tags, deduplication, intersection), sorted set (ranking, delayed queue by score). Extended types include bitmap (sign-in, online status), hyperloglog (UV approximation), geohash (nearby search), and stream (message queue with consumer groups).',
   'good answer covers the five core types plus at least one extended type and a concrete scenario per type.', 7, 5001, 'MEDIUM', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (5002, 'Why does Redis use SDS instead of a C native string?', 'Explain the benefits of simple dynamic string over char*.',
   'SDS stores length explicitly (O(1) len), is binary safe (can contain null bytes), avoids buffer overflow by pre-allocating and checking capacity, and reduces reallocation with free space accounting. It also keeps a buf with trailing null for partial C-API compatibility while not relying on it for length.',
   'emphasize binary safety, O(1) length, and no overflow as the key advantages.', 7, 5001, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (5003, 'When does a Redis Hash switch from ziplist/listpack to hashtable encoding?', 'Describe the thresholds that trigger the encoding upgrade.',
   'A small hash uses ziplist/listpack (compact, memory efficient) while every field and value is below hash-max-ziplist-value and the number of fields is below hash-max-ziplist-entries. Once either threshold is exceeded Redis converts to a hashtable. listpack replaced ziplist in newer versions to avoid the cascading update problem.',
   'mention the two thresholds (field count and element size) and the ziplist to listpack evolution.', 7, 5001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (5004, 'How is a Redis Sorted Set (ZSET) implemented and why use both skiplist and dict?', 'Explain the dual-structure design and its trade-offs.',
   'ZSET keeps a dict mapping member to score for O(1) point lookup, and a skip list (plus a ziplist/listpack when small) ordered by score for range queries like ZRANGE. The skiplist and dict share the same member/score objects to avoid duplication. This gives O(log n) range scans and O(1) single-member score reads.',
   'key point: dict for point query, skiplist for range, both reference the same data to save memory.', 7, 5001, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),

  -- —— Persistence RDB and AOF（组 5002）——
  (5005, 'How does RDB persistence work and what is the bgsave process?', 'Describe snapshotting and why it does not block the main thread.',
   'RDB serializes the in-memory dataset to a compact binary dump file. SAVE blocks the server; BGSAVE forks a child process so the parent keeps serving while the child writes the snapshot, using copy-on-write to share unchanged pages. RDB is fast to load and great for backup but can lose data since the last snapshot.',
   'cover fork, copy-on-write, and the trade-off of potential data loss.', 7, 5002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (5006, 'What is AOF rewrite and why is it needed?', 'Explain how the AOF file is compacted without losing commands.',
   'AOF records every write command; over time it grows and contains redundant operations (e.g. incr 100 times). BGREWRITEAOF forks a child that reads current data and writes the minimal set of commands to recreate it, while new commands are buffered and appended after the rewrite, so the file shrinks without data loss.',
   'mention that rewrite rebuilds from current data, not by parsing the old file, plus the command buffering.', 7, 5002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (5007, 'Compare RDB and AOF and explain hybrid persistence.', 'Give guidance on choosing and combining them.',
   'RDB: compact, fast restart, point-in-time backup, but loses recent writes and fork can stall on large datasets. AOF: better durability (everysec loses at most ~1s), but files are larger and slower to load. Hybrid persistence (Redis 4.0+) writes an RDB snapshot at the start of the AOF file followed by incremental AOF commands, combining fast load with low data loss.',
   'good answer contrasts durability, size, and restart speed, then explains the hybrid RDB+AOF header.', 7, 5002, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (5008, 'On restart, which file does Redis load and in what order?', 'Describe the recovery priority between RDB and AOF.',
   'If AOF is enabled, Redis loads the AOF file (it is the more complete source). If AOF is disabled, it loads the RDB file. With hybrid persistence the AOF file itself begins with an RDB snapshot plus appended commands. The choice is controlled by the appendonly config and loading falls back to RDB only when AOF is off or its file is missing.',
   'emphasize AOF takes priority over RDB when enabled, and hybrid is still an AOF file.', 7, 5002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— Cache Penetration Breakdown Avalanche（组 5003）——
  (5009, 'What is cache penetration and how do you prevent it?', 'Explain the missing-key attack and two common defenses.',
   'Penetration happens when requests query keys that exist neither in cache nor database (e.g. malicious non-existent ids), so every request hits the DB. Defenses: cache null/empty results with a short TTL so repeated misses are absorbed, and use a bloom filter to reject keys that definitely do not exist before hitting the database.',
   'must distinguish penetration (non-existent key) from the other two modes, and name bloom filter plus null caching.', 7, 5003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (5010, 'What is cache breakdown and how do you protect a hot key on expiry?', 'Describe the hot-key single-point failure and mutex solutions.',
   'Breakdown occurs when a single very hot key expires and a burst of concurrent requests all miss the cache and stampede the database at once. Solutions: use a mutex or distributed lock so only one thread rebuilds the cache while others wait; or use logical expiration where the value carries its own expiry and a background thread refreshes it, never removing the key from cache.',
   'key is single hot key (not many keys) and the mutex/double-check or logical expiry fix.', 7, 5003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (5011, 'What is cache avalanche and how do you avoid it?', 'Explain mass simultaneous expiry and mitigation strategies.',
   'Avalanche is when a large set of cache keys expire at the same time (or the cache cluster goes down), causing a sudden DB overload. Mitigations: add random jitter to TTL so expirations spread out, deploy a multi-level cache (local + Redis), use a circuit breaker to shed load, and ensure the cache tier itself is highly available.',
   'contrast with breakdown (many keys vs one hot key) and list jitter, multi-level cache, breaker.', 7, 5003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (5012, 'Compare cache penetration, breakdown, and avalanche with examples.', 'Show you understand the difference and the right fix for each.',
   'Penetration: querying a key that never exists (fix: bloom filter, null cache). Breakdown: one hot key expires and a spike hits DB (fix: mutex, logical expiry). Avalanche: many keys expire together or cache down (fix: TTL jitter, multi-level cache, breaker). They differ in scope (one key vs many keys) and in whether the key is real.',
   'a strong answer states all three with the precise distinguishing factor and matching solution.', 7, 5003, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (5013, 'What are the limitations of a bloom filter in cache penetration defense?', 'Explain false positives and how to handle them.',
   'A bloom filter can have false positives: it may say a key might exist when it does not, so a few requests still reach the database, but it never reports a real key as absent. To bound impact, combine it with null caching for the rare false positives, and size the filter and choose hash functions to keep the false-positive rate low.',
   'the crux is false positives are possible but false negatives are not; pair with null caching.', 7, 5003, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),

  -- —— Cache Consistency（组 5004）——
  (5014, 'In the cache-aside pattern, why delete the cache instead of updating it after a DB write?', 'Explain the read/write flow and the delete choice.',
   'Standard flow: on read, load from cache, miss then load from DB and fill cache; on write, update DB first, then delete the cache. Deleting (not updating) avoids the race where two writers update DB and cache in different orders producing a stale cached value, and avoids waste when a written value is never read. A short TTL provides a final safety net.',
   'emphasize update-DB-then-delete and that delete avoids a write-order race and useless writes.', 7, 5004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (5015, 'Under what concurrent scenarios can cache and DB become inconsistent?', 'Walk through a read/write race that leaves stale data.',
   'A classic race: thread A reads a miss, loads old DB value; thread B updates DB then deletes cache; then A writes its stale value into cache, leaving a stale entry. Similar issues arise if cache deletion fails. Mitigations include delayed double-delete, retry of failed deletes, and binlog-driven cache invalidation that orders operations by commit time.',
   'good answer constructs a concrete interleaving that produces a stale cache value and proposes an ordering fix.', 7, 5004, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (5016, 'What is the delayed double-delete strategy and what are its flaws?', 'Describe the timing-based mitigation for consistency.',
   'After updating the DB, delete the cache, then sleep a short time (e.g. 500ms) and delete again. The second delete cleans up a stale value that a slow reader may have written. Flaws: the sleep adds latency, the delay is a guess, and it still cannot guarantee strict consistency under all races; it only reduces the window of inconsistency.',
   'point out it reduces but does not eliminate inconsistency, and the sleep is heuristic.', 7, 5004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (5017, 'How can you get stronger cache consistency using binlog?', 'Explain the CDC-based invalidation approach.',
   'Use a change-data-capture tool (e.g. canal/Debezium) to subscribe to the database binlog. On every committed change, an external consumer deletes or refreshes the corresponding cache key, ordered by actual commit time. This decouples cache invalidation from application write paths and avoids application-side delete races, though it still provides eventual rather than strict consistency.',
   'key is binlog gives authoritative, ordered change events independent of app logic.', 7, 5004, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),

  -- —— Distributed Lock（组 5005）——
  (5018, 'How do you implement a Redis distributed lock and why must the value be unique?', 'Show the acquire command and the safety requirement.',
   'Acquire with SET lock_key unique_token NX EX 30 to set the key only if absent, with a TTL and a unique token (e.g. UUID). The unique value ensures that when releasing, only the owner whose token matches can delete it; otherwise a client could delete a lock held by another client after its own lock expired. Release must be atomic via a Lua script.',
   'must include NX (atomic take), EX (auto expiry), unique token, and atomic Lua release.', 7, 5005, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (5019, 'How does Redisson implement lock renewal with a watch dog?', 'Explain automatic lease extension.',
   'When a lock is acquired without an explicit lease time, Redisson starts a watch-dog background task that periodically (default every 1/3 of the 30s lease) extends the lock TTL via a Lua script while the holding thread is alive. If the client crashes, the watch dog stops and the lock auto-expires, preventing a deadlock from a dead holder.',
   'emphasize auto-renewal only happens with no explicit lease, and stops on client crash to avoid permanent lock.', 7, 5005, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (5020, 'What is the Redlock algorithm and what are the criticisms against it?', 'Discuss the multi-master approach and its debate.',
   'Redlock acquires the lock from a majority of independent Redis master nodes (N/2+1) within a bounded time, requiring most to succeed. It targets higher safety under master failure. Critics (e.g. Kleppmann) argue that without a bound on clock drift and GC pauses, a client can hold a lock that has already expired on another node; for the strongest guarantees people prefer consensus systems like etcd/ZooKeeper.',
   'good answer notes the majority-quorum design and the clock/GC-assumption criticism, not just praise.', 7, 5005, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (5021, 'What happens if a Redis distributed lock is never released?', 'Explain the deadlock and the mitigation.',
   'If the holder crashes or forgets to release, the lock stays held and other clients block forever, causing a deadlock. The fix is to always set a TTL on the lock so it auto-expires, and to release in a finally block. The watch dog or a short TTL bounds the worst-case block even on crash.',
   'state the deadlock risk and that TTL plus finally-release bounds it.', 7, 5005, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),
  (5022, 'Compare Redis, ZooKeeper, and database based distributed locks.', 'Give the trade-offs of each mechanism.',
   'Redis lock: fast, simple, but with TTL-based approximate safety and weaker guarantees under failure. ZooKeeper/etcd: use temporary ephemeral nodes or lease primitives, offer stronger consistency and automatic release on session loss, but add operational complexity and lower throughput. Database lock: easy via unique row or悲观锁 but is a single point under load and slow. Choice depends on required safety vs performance.',
   'compare along safety, auto-release, throughput, and operational cost.', 7, 5005, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),

  -- —— Cluster and Sentinel（组 5006）——
  (5023, 'How does Redis Sentinel perform failover?', 'Walk through detection, election, and promotion.',
   'Sentinel nodes monitor masters and replicas via heartbeat; when a quorum of sentinels agrees a master is down, they elect a leader sentinel which selects the best replica (highest replication offset) and sends SLAVEOF NO ONE to promote it, then reconfigures other replicas to follow the new master and notifies clients through the sentinel API of the new address.',
   'cover quorum, leader election, replica promotion by offset, and client notification.', 7, 5006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (5024, 'How does Redis Cluster shard data and what is resharding?', 'Explain hash slots and moving data between nodes.',
   'Redis Cluster splits keyspace into 16384 hash slots; a key maps to slot = CRC16(key) mod 16384, and slots are distributed among masters. Resharding uses the reshard command to migrate slot ownership from one node to another, moving the actual key-value pairs; clients are told the new owner via MOVED/ASK redirects during the transition.',
   'mention 16384 slots, CRC16 mapping, and slot migration with redirects.', 7, 5006, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (5025, 'What are MOVED and ASK redirects in Redis Cluster?', 'Explain when each is returned and how clients handle them.',
   'MOVED is returned when a key permanently belongs to another node after slot ownership changes; the client should update its slot-to-node cache and retry there. ASK is a temporary redirect during resharding, telling the client to send the command to the importing node once with an ASKING command but without updating the cached mapping. ASK is transient, MOVED is authoritative.',
   'distinguish permanent (MOVED, update cache) vs transient (ASK, single retry with ASKING).', 7, 5006, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1)
ON DUPLICATE KEY UPDATE
  title = VALUES(title), content = VALUES(content), reference_answer = VALUES(reference_answer),
  analysis = VALUES(analysis), category_id = VALUES(category_id), group_id = VALUES(group_id),
  difficulty = VALUES(difficulty), question_type = VALUES(question_type), experience_level = VALUES(experience_level),
  is_high_frequency = VALUES(is_high_frequency), status = VALUES(status);

-- ===== 4. 题目—标签关系（每题 1 个标签，扁平 FROM DUAL WHERE NOT EXISTS 形式） =====
INSERT INTO question_tag_relation (question_id, tag_id)
SELECT 5001, 501 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5001 AND r.tag_id = 501)
UNION ALL SELECT 5002, 501 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5002 AND r.tag_id = 501)
UNION ALL SELECT 5003, 501 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5003 AND r.tag_id = 501)
UNION ALL SELECT 5004, 501 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5004 AND r.tag_id = 501)
UNION ALL SELECT 5005, 502 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5005 AND r.tag_id = 502)
UNION ALL SELECT 5006, 502 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5006 AND r.tag_id = 502)
UNION ALL SELECT 5007, 502 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5007 AND r.tag_id = 502)
UNION ALL SELECT 5008, 502 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5008 AND r.tag_id = 502)
UNION ALL SELECT 5009, 503 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5009 AND r.tag_id = 503)
UNION ALL SELECT 5010, 503 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5010 AND r.tag_id = 503)
UNION ALL SELECT 5011, 503 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5011 AND r.tag_id = 503)
UNION ALL SELECT 5012, 503 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5012 AND r.tag_id = 503)
UNION ALL SELECT 5013, 503 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5013 AND r.tag_id = 503)
UNION ALL SELECT 5014, 504 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5014 AND r.tag_id = 504)
UNION ALL SELECT 5015, 504 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5015 AND r.tag_id = 504)
UNION ALL SELECT 5016, 504 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5016 AND r.tag_id = 504)
UNION ALL SELECT 5017, 504 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5017 AND r.tag_id = 504)
UNION ALL SELECT 5018, 505 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5018 AND r.tag_id = 505)
UNION ALL SELECT 5019, 505 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5019 AND r.tag_id = 505)
UNION ALL SELECT 5020, 505 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5020 AND r.tag_id = 505)
UNION ALL SELECT 5021, 505 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5021 AND r.tag_id = 505)
UNION ALL SELECT 5022, 505 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5022 AND r.tag_id = 505)
UNION ALL SELECT 5023, 508 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5023 AND r.tag_id = 508)
UNION ALL SELECT 5024, 506 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5024 AND r.tag_id = 506)
UNION ALL SELECT 5025, 506 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 5025 AND r.tag_id = 506);
