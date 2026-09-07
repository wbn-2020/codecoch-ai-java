-- V4_133: 知识点包 #4「MySQL」。
-- 数据模型：一个 question_group = 一个知识点包（main_knowledge_point = 主知识点），
--           其下多道 question 通过 group_id 归属。分类挂到 init.sql 已有的「MySQL」(category_id=6)。
-- 幂等：group/tag 用 NOT EXISTS 守卫；question 用 ON DUPLICATE KEY UPDATE；
--       relation 用 SELECT qid, tid FROM DUAL WHERE NOT EXISTS(...)（扁平形式，避免 V4_129 的 bug）。

-- ===== 1. 标签（10 个 MySQL 子主题，tag id 401–410） =====
INSERT INTO question_tag (id, tag_name, status)
SELECT 401, '索引结构', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 401)
UNION ALL
SELECT 402, '索引失效', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 402)
UNION ALL
SELECT 403, '事务隔离', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 403)
UNION ALL
SELECT 404, '锁机制', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 404)
UNION ALL
SELECT 405, '慢SQL优化', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 405)
UNION ALL
SELECT 406, 'MVCC', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 406)
UNION ALL
SELECT 407, '主从复制', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 407)
UNION ALL
SELECT 408, '备份恢复', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 408)
UNION ALL
SELECT 409, '执行计划EXPLAIN', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 409)
UNION ALL
SELECT 410, '分库分表架构', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 410);

-- ===== 2. 知识点组（7 个主知识点，category_id=6 MySQL） =====
INSERT INTO question_group (id, group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status)
SELECT 4001, '索引结构 B+Tree', 'Why does MySQL InnoDB use B+Tree for indexes?',
       'B+Tree keeps all data in leaf nodes linked as a ordered list, so range scans and order-by are efficient and tree height stays small (3-4 levels for millions of rows). Non-leaf nodes store only keys, allowing more fan-out than B-Tree, and unlike Hash indexes it supports range and prefix queries.',
       'B+Tree structure and why it fits disk-based storage', 'MEDIUM', '考察 B+Tree 结构、聚簇/二级索引、覆盖索引与回表。', 6, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 4001)
UNION ALL
SELECT 4002, '索引失效', 'What are common MySQL index invalidation cases?',
       'Common cases: leading wildcard like %abc, function or calculation on the indexed column, implicit type conversion, violating the leftmost prefix rule of a composite index, and a range query that blocks subsequent composite columns. Verify with EXPLAIN.',
       'Index invalidation and leftmost prefix rule', 'MEDIUM', '考察索引失效场景与 EXPLAIN 验证。', 6, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 4002)
UNION ALL
SELECT 4003, '事务隔离级别', 'What are the transaction isolation levels in MySQL?',
       'Four levels: READ UNCOMMITTED (dirty reads), READ COMMITTED (no dirty read, but non-repeatable read), REPEATABLE READ (MySQL default, prevents non-repeatable read and phantom read via MVCC + gap locks), SERIALIZABLE (full serialization, locks everything). Higher levels trade concurrency for consistency.',
       'Isolation levels and the anomalies they prevent', 'MEDIUM', '考察四种隔离级别、脏读/不可重复读/幻读。', 6, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 4003)
UNION ALL
SELECT 4004, '锁类型', 'What lock types does InnoDB provide?',
       'InnoDB uses row-level record locks, gap locks (locking the gap between records), and next-key locks (record + gap) to prevent phantom reads. It also has intention locks at the table level and auto-increment locks. Locks are taken on indexes; a statement without an index hits the clustered index and can lock many or all rows.',
       'Record, gap, and next-key locks', 'MEDIUM', '考察记录锁/间隙锁/临键锁与加锁的索引前提。', 6, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 4004)
UNION ALL
SELECT 4005, '慢 SQL 调优', 'How do you approach slow SQL tuning?',
       'Approach: capture the slow query with slow log / Performance Schema, run EXPLAIN to see the access path (type, key, rows, Extra), check index usage and cardinality, rewrite the SQL (avoid SELECT *, fix functions around columns, optimize JOIN order and deep pagination), and add or adjust indexes. Always validate with EXPLAIN after changes.',
       'Slow SQL diagnosis with EXPLAIN and rewrite', 'MEDIUM', '考察慢 SQL 诊断流程、EXPLAIN 解读与改写。', 6, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 4005)
UNION ALL
SELECT 4006, 'MVCC 多版本并发控制', 'How does InnoDB MVCC work?',
       'InnoDB MVCC uses a hidden transaction id (DB_TRX_ID) and rollback pointer (DB_ROLL_PTR) on each row, an undo log chain of old versions, and a read view that decides which version a transaction can see. Reads are snapshot reads (non-blocking); writes use current reads with locks. This gives high concurrency with consistent snapshots.',
       'MVCC via undo log, read view, and snapshot reads', 'MEDIUM', '考察隐藏列、undo log、Read View 与快照读。', 6, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 4006)
UNION ALL
SELECT 4007, '主从复制与备份', 'How does MySQL master-slave replication work?',
       'The master writes changes to the binary log; a slave IO thread pulls binlog events into its relay log, and a SQL thread replays them, keeping the slave in sync. Modes range from asynchronous (default, can lose data on failover) to semi-synchronous (master waits for at least one slave ack). Backups use logical (mysqldump) or physical (xtrabackup) tools, plus point-in-time recovery from binlog.',
       'Binlog-based replication and backup strategies', 'MEDIUM', '考察 binlog/relay log 复制链路、半同步与备份恢复。', 6, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 4007);

-- ===== 3. 题目（30 道，category_id=6，group_id 指向知识点组） =====
INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, question_type, experience_level, is_high_frequency, status)
VALUES
  -- —— 索引结构 B+Tree（组 4001）——
  (4001, 'Why does MySQL use B+Tree instead of B-Tree or Hash for indexes?', 'Compare B+Tree with B-Tree and Hash indexes and explain the fit for disk storage.',
   'B+Tree stores data only in leaf nodes, which are linked in order, making range scans and ORDER BY efficient with few levels (3-4 for huge tables). B-Tree stores data in every node, reducing fan-out. Hash indexes give O(1) equality but no range, prefix, or ordering support. B+Tree also keeps non-leaf nodes key-only for a larger fan-out, minimizing disk I/O.',
   'Highlight leaf-only data + linked leaves, small height, range support; contrast Hash lack of range.', 6, 4001, 'MEDIUM', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (4002, 'What is the difference between a clustered index and a secondary index in InnoDB?', 'Explain how data is organized and what a secondary index lookup costs.',
   'The clustered index is the primary key; its leaf nodes hold the full row, so the table is the index (IOT). Secondary indexes store the indexed columns plus the primary key; looking up a non-indexed column requires a second lookup (回表) using that primary key in the clustered index. A covering index avoids the回表 by including all needed columns.',
   'Clustered = PK with full row; secondary stores PK then needs回表 unless covering.', 6, 4001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (4003, 'What is a covering index and what is Index Condition Pushdown?', 'Explain how both reduce回表 and speed queries.',
   'A covering index contains all columns a query needs in its leaf entries, so InnoDB satisfies the query without accessing the clustered index. Index Condition Pushdown (ICP, MySQL 5.6+) lets the storage engine evaluate WHERE conditions on indexed columns inside the index scan, filtering rows before回表, reducing the number of row fetches.',
   'Covering avoids回表 entirely; ICP filters inside the engine before回表.', 6, 4001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (4004, 'How is a composite index ordered and what is the leftmost prefix rule?', 'Explain column order semantics and which queries can use the index.',
   'Columns in a composite index are sorted lexicographically by the first column, then the second, and so on. The leftmost prefix rule means a query can use the index only starting from the first column: (a), (a,b), or (a,b,c) are usable, but (b) or (b,c) alone are not. Equality on the first columns followed by a range on the next still allows the subsequent columns to be used only up to the range boundary.',
   'Emphasize lexicographic ordering and that the prefix must start at column a.', 6, 4001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— 索引失效（组 4002）——
  (4005, 'What are the common index invalidation scenarios in MySQL?', 'List the typical ways a query stops using an index.',
   'Leading wildcard LIKE %abc, applying a function or calculation to the indexed column (WHERE YEAR(created)=2023), implicit type conversion (string column compared to a number), not satisfying the leftmost prefix of a composite index, OR / != / NOT IN on the index, and a range column blocking later composite columns. Always verify with EXPLAIN type=ref/range vs ALL.',
   'Enumerate at least 4 cases and tie each to EXPLAIN evidence.', 6, 4002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (4006, 'How do you verify index usage with EXPLAIN?', 'Explain the key EXPLAIN fields and how to read them.',
   'Run EXPLAIN before the SELECT. type shows access path (const > ref > range > index > ALL; avoid ALL full scan). key shows the index actually used; rows is the estimated rows examined; Extra reveals Using index (covering), Using where, Using filesort, or Using temporary (bad signs). A good plan has a selective key and few rows with no filesort/temporary.',
   'Focus on type, key, rows, and Extra; flag filesort/temporary as problems.', 6, 4002, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (4007, 'Why does implicit type conversion cause an index to fail?', 'Give a concrete example and the mechanism.',
   'If a column is VARCHAR but the query compares it to a number (WHERE phone = 13800138000), MySQL applies a function to convert the column to a number for each row, which is a calculation on the indexed column and forces a full scan. The fix is to quote the value: WHERE phone = '13800138000'. The same happens with date/string mismatches.',
   'Explain the hidden CAST on the column and quote the literal as the fix.', 6, 4002, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),
  (4008, 'Why can a range query invalidate subsequent composite index columns?', 'Explain the boundary effect after a range condition.',
   'In a composite index (a,b,c), once a column is used with a range (a=1 AND b>10 AND c=2), the index can only be used for a and b up to the range; column c cannot further narrow the scan because within the b range the c values are not ordered. So c is filtered by a separate WHERE pass rather than via the index. Equality-first ordering of composite indexes helps here.',
   'Point out the ordering breaks after a range, so later columns fall back to filter.', 6, 4002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— 事务隔离（组 4003）——
  (4009, 'What are the four transaction isolation levels and what problems does each solve?', 'List the levels and the anomalies (dirty/non-repeatable/phantom read) each prevents.',
   'READ UNCOMMITTED allows dirty reads. READ COMMITTED prevents dirty reads but allows non-repeatable reads. REPEATABLE READ (InnoDB default) prevents dirty and non-repeatable reads and, in InnoDB, phantom reads via MVCC + gap locks. SERIALIZABLE prevents all anomalies by full locking but kills concurrency. Higher isolation trades throughput for consistency.',
   'Map each level to which of dirty/non-repeatable/phantom it blocks.', 6, 4003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (4010, 'What is a phantom read and how does it differ from a non-repeatable read?', 'Clarify the distinction between the two anomalies.',
   'A non-repeatable read is when the same row returns different values on re-read within a transaction (another transaction updated/committed that row). A phantom read is when a range query returns a different set of rows (another transaction inserted/deleted rows in that range). InnoDB REPEATABLE READ prevents both: snapshot reads handle row changes, gap/next-key locks block inserts in the range.',
   'Non-repeatable = same row changed; phantom = row set of a range changed.', 6, 4003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (4011, 'How does MySQL REPEATABLE READ prevent phantom reads?', 'Combine MVCC and locking in your explanation.',
   'Snapshot reads use the transaction's read view and undo log so a range query always sees the same committed snapshot, avoiding phantom reads on read. For current reads (SELECT ... FOR UPDATE, UPDATE, DELETE on a range), InnoDB takes next-key locks (record + gap) so no other transaction can insert a row into the locked range, physically preventing phantoms.',
   'Snapshot read via MVCC plus next-key locking on current reads prevents phantoms.', 6, 4003, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (4012, 'When does a transaction deadlock occur in MySQL and how do you avoid it?', 'Explain the mechanism and prevention.',
   'A deadlock happens when two transactions hold locks the other needs in opposite order (e.g. T1 locks row A then wants B, T2 locks B then wants A). InnoDB detects it, rolls back the smaller transaction, and returns error 1213. Avoid by accessing tables/rows in a consistent order, keeping transactions short, using lower isolation or fewer locks, and retrying on deadlock.',
   'Give the cyclic-wait example, the InnoDB rollback behavior, and ordering as prevention.', 6, 4003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— 锁类型（组 4004）——
  (4013, 'What lock types does InnoDB provide?', 'Describe record, gap, next-key, intention, and auto-increment locks.',
   'InnoDB provides row-level record locks (lock on a single row), gap locks (lock the gap between index records to prevent inserts), and next-key locks (record + gap, the default for range/current reads, preventing phantom reads). Table-level intention locks (IS/IX) coordinate with row locks. Auto-increment locks serialize inserts into an AI column. All row locks are taken on index records.',
   'List record/gap/next-key plus intention and AI locks and note index-based locking.', 6, 4004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (4014, 'What are gap locks and next-key locks, and how do they prevent phantom reads?', 'Explain the mechanisms with an example range.',
   'A gap lock locks the open interval between existing index values; a next-key lock is the combination of a record lock on an index entry and a gap lock on the interval before it. For a range query InnoDB locks matched records and the gaps after them, so another transaction cannot INSERT a row that would appear in the range, preventing phantom reads at the locking level.',
   'Gap = interval; next-key = record + preceding gap; blocks inserts into range.', 6, 4004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (4015, 'Why can an UPDATE on a non-indexed column lock the whole table?', 'Walk through what happens when no usable index exists.',
   'If the WHERE clause has no usable index, InnoDB must do a full table scan; because it locks index records, it ends up taking a next-key lock on every row of the clustered index (and the supremum gap), effectively locking the whole table until the transaction commits. The fix is to add a proper index on the filtered column so only matching rows are locked.',
   'No index => full scan => next-key lock on all rows; add an index to limit locking.', 6, 4004, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (4016, 'What is the difference between optimistic and pessimistic locking in MySQL?', 'Compare the two concurrency control styles with examples.',
   'Pessimistic locking uses the database (SELECT ... FOR UPDATE / FOR SHARE) to lock rows before mutating, blocking others; it is safe under high contention but lowers concurrency. Optimistic locking uses a version column or timestamp and checks it at update time (UPDATE ... WHERE version = ?), retrying on conflict; it has higher throughput under low contention but needs conflict handling.',
   'Pessimistic = DB locks upfront; optimistic = version check at commit with retry.', 6, 4004, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),

  -- —— 慢 SQL 调优（组 4005）——
  (4017, 'What is your general approach to tuning a slow SQL query?', 'Give an end-to-end diagnosis and fix workflow.',
   'Capture the SQL from the slow query log or Performance Schema. Run EXPLAIN to inspect access path, index used, rows scanned, and Extra (filesort/temporary). Check whether an index is missing or invalidated, rewrite the SQL to be index-friendly (push filters into the index, avoid SELECT *, fix functions), and add/adjust indexes. Re-run EXPLAIN to confirm type improves and rows drops. Measure under real load.',
   'Workflow: capture -> EXPLAIN -> find index issue -> rewrite/add index -> re-verify.', 6, 4005, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (4018, 'How would you rewrite a slow JOIN or subquery?', 'Explain practical rewrites that help the optimizer.',
   'Prefer JOINs over dependent subqueries (the optimizer handles JOINs better), ensure join columns are indexed and use the same type, and put the smaller table first when the optimizer does not. Convert IN subqueries to EXISTS or JOIN where cheaper, avoid SELECT * so a covering index can be used, and move heavy filters into the ON/WHERE early. Verify with EXPLAIN.',
   'Replace dependent subqueries with JOINs, index join keys, avoid SELECT *.', 6, 4005, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (4019, 'How does large OFFSET pagination hurt performance and how do you optimize it?', 'Explain the cost of LIMIT m, n on big tables and the fix.',
   'LIMIT 100000, 20 must read and discard 100000 rows before returning 20, which gets slower as offset grows. Optimize with keyset (seek) pagination: remember the last seen id and use WHERE id > ? ORDER BY id LIMIT 20, which uses the index to jump directly. Alternatively index the sort column and cap the allowed offset.',
   'Offset scans and discards rows; use WHERE id > last_id keyset pagination instead.', 6, 4005, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (4020, 'When is a full table scan acceptable and how do you force or avoid an index?', 'Discuss the optimizer choice and hints.',
   'A full scan is acceptable for small tables, when most rows qualify (range scan would not help), or for grouped/report queries. The optimizer picks it based on cardinality and cost; you can nudge it with USE INDEX / FORCE INDEX, but better is to fix statistics (ANALYZE TABLE) and the query so the plan is naturally good. Avoid FORCE INDEX as a permanent crutch.',
   'Scan is fine for small/large-fraction tables; prefer fixing stats/query over hints.', 6, 4005, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— MVCC（组 4006）——
  (4021, 'Explain MVCC and how it enables non-blocking reads.', 'Describe the multi-version mechanism and its concurrency benefit.',
   'InnoDB MVCC keeps multiple versions of a row via the undo log; each row has a transaction id and rollback pointer. A read creates a read view that defines which versions are visible, so readers see a consistent snapshot without blocking writers and writers do not block readers. Only writes take locks. This is why REPEATABLE READ reads do not lock rows.',
   'Multiple versions + read view give snapshot, non-blocking reads vs writers.', 6, 4006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (4022, 'What are the hidden columns, undo log, and read view in MVCC?', 'Explain each component and its role.',
   'Each clustered-index row carries DB_TRX_ID (creator transaction id) and DB_ROLL_PTR (pointer to the previous version in the undo log). The undo log chains older row versions. A read view (active transaction list, up/low watermarks) is taken by a transaction to decide which version is visible: a version is visible if its trx_id is committed and below the view threshold. Old versions are purged when no view needs them.',
   'Hidden trx_id + roll_ptr, undo version chain, read view visibility rules.', 6, 4006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (4023, 'How does the read view differ between READ COMMITTED and REPEATABLE READ?', 'Contrast when the read view is created in each level.',
   'Under READ COMMITTED, a new read view is created on every snapshot read, so each statement sees the latest committed data (non-repeatable read possible). Under REPEATABLE READ, the read view is created at the first snapshot read of the transaction and reused for all subsequent reads, giving a stable snapshot across the transaction. This is the main behavioral difference.',
   'RC refreshes read view per statement; RR fixes it once per transaction.', 6, 4006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (4024, 'How does MVCC handle updates: snapshot read vs current read?', 'Explain the two read modes and locking.',
   'Snapshot reads (plain SELECT) use the read view and undo versions without locking, giving consistent non-blocking reads. Current reads (SELECT ... FOR UPDATE/SHARE, INSERT/UPDATE/DELETE) read the latest committed version and take locks (record/next-key) to prevent concurrent modification. An UPDATE first does a current read to get the latest row, then writes a new version linked via undo.',
   'Snapshot = unlocked consistent view; current = latest + locks for writes.', 6, 4006, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (4025, 'What is the purge mechanism in MVCC?', 'Explain how old row versions are cleaned up.',
   'When no active transaction (read view) still needs an old row version, the purge thread reclaims those undo log entries and the space in the history list. Without purge, the undo log and the version chain would grow without bound, hurting performance and disk usage. Long-running transactions delay purge and can cause undo bloat.',
   'Purge reclaims unneeded versions; long transactions block it and cause bloat.', 6, 4006, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),

  -- —— 主从复制与备份（组 4007）——
  (4026, 'How does MySQL master-slave replication work?', 'Describe the binlog, IO thread, and SQL thread flow.',
   'The master writes all data-changing events to its binary log. A slave connects, and its IO thread reads the binlog and writes it to the slave relay log. The slave SQL thread replays the relay log events to apply the same changes. Replication is asynchronous by default, so the slave can lag behind the master. GTID can simplify failover and position tracking.',
   'Master binlog -> slave IO thread -> relay log -> SQL thread applies.', 6, 4007, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (4027, 'What are the replication modes (async vs semi-sync) and their trade-offs?', 'Compare durability and performance of each mode.',
   'Asynchronous (default): the master commits and returns immediately without waiting for slaves; highest throughput but can lose committed data on failover. Semi-synchronous: the master waits until at least one slave has received and acked the binlog (not necessarily applied) before committing, reducing data loss at the cost of latency. Fully synchronous (group replication) waits for all, strongest consistency but slowest.',
   'Async = fast/risky; semi-sync = ack from one slave trades latency for durability.', 6, 4007, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (4028, 'How do you handle master-slave delay and read from a slave safely?', 'Discuss lag causes and read-consistency strategies.',
   'Lag comes from heavy writes, slow slaves, large transactions, or network. Monitor Seconds_Behind_Master. Mitigations: keep transactions small, use semi-sync, scale out slaves, and route reads that need freshness to the master while directing tolerant reads (reports) to slaves. For read-after-write consistency, read the just-written data from the master or use a sticky/forced-read route.',
   'Monitor lag; route fresh reads to master, tolerant reads to slave; small transactions.', 6, 4007, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (4029, 'What backup strategies exist: logical vs physical?', 'Compare mysqldump, xtrabackup, and their trade-offs.',
   'Logical backups (mysqldump, mydumper) export SQL, are portable and easy to restore partially, but are slower and lock/longer for big data. Physical backups (Percona XtraBackup, MySQL Enterprise Backup) copy data files hot with minimal locking, restore fast, and support incremental backup, but are less portable across versions/platforms. Combine with binlog for point-in-time recovery.',
   'Logical = portable/slow; physical = fast/hot but less portable; add binlog for PITR.', 6, 4007, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (4030, 'How do you recover data from the binary log?', 'Explain point-in-time recovery using binlog.',
   'First restore the latest full backup, then apply binlog events from the backup position up to the desired point with mysqlbinlog piped into mysql (or --start/--stop-datetime / --start/--stop-position). This replays only the committed changes and lets you stop just before a mistaken DROP/UPDATE. GTID mode makes positioning explicit via executed_gtid_set.',
   'Restore full backup, then replay binlog via mysqlbinlog to a stop point (PITR).', 6, 4007, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1)
ON DUPLICATE KEY UPDATE
  title = VALUES(title), content = VALUES(content), reference_answer = VALUES(reference_answer),
  analysis = VALUES(analysis), category_id = VALUES(category_id), group_id = VALUES(group_id),
  difficulty = VALUES(difficulty), question_type = VALUES(question_type), experience_level = VALUES(experience_level),
  is_high_frequency = VALUES(is_high_frequency), status = VALUES(status);

-- ===== 4. 题目—标签关系（扁平 SELECT qid, tid FROM DUAL WHERE NOT EXISTS 形式，避免子查询列暴露 bug） =====
INSERT INTO question_tag_relation (question_id, tag_id)
SELECT 4001, 401 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4001 AND r.tag_id = 401)
UNION ALL SELECT 4002, 401 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4002 AND r.tag_id = 401)
UNION ALL SELECT 4003, 401 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4003 AND r.tag_id = 401)
UNION ALL SELECT 4004, 401 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4004 AND r.tag_id = 401)
UNION ALL SELECT 4005, 402 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4005 AND r.tag_id = 402)
UNION ALL SELECT 4006, 409 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4006 AND r.tag_id = 409)
UNION ALL SELECT 4006, 402 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4006 AND r.tag_id = 402)
UNION ALL SELECT 4007, 402 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4007 AND r.tag_id = 402)
UNION ALL SELECT 4008, 402 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4008 AND r.tag_id = 402)
UNION ALL SELECT 4009, 403 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4009 AND r.tag_id = 403)
UNION ALL SELECT 4010, 403 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4010 AND r.tag_id = 403)
UNION ALL SELECT 4011, 403 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4011 AND r.tag_id = 403)
UNION ALL SELECT 4011, 406 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4011 AND r.tag_id = 406)
UNION ALL SELECT 4012, 404 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4012 AND r.tag_id = 404)
UNION ALL SELECT 4013, 404 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4013 AND r.tag_id = 404)
UNION ALL SELECT 4014, 404 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4014 AND r.tag_id = 404)
UNION ALL SELECT 4014, 406 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4014 AND r.tag_id = 406)
UNION ALL SELECT 4015, 404 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4015 AND r.tag_id = 404)
UNION ALL SELECT 4016, 404 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4016 AND r.tag_id = 404)
UNION ALL SELECT 4017, 405 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4017 AND r.tag_id = 405)
UNION ALL SELECT 4017, 409 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4017 AND r.tag_id = 409)
UNION ALL SELECT 4018, 405 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4018 AND r.tag_id = 405)
UNION ALL SELECT 4019, 405 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4019 AND r.tag_id = 405)
UNION ALL SELECT 4020, 405 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4020 AND r.tag_id = 405)
UNION ALL SELECT 4020, 409 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4020 AND r.tag_id = 409)
UNION ALL SELECT 4021, 406 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4021 AND r.tag_id = 406)
UNION ALL SELECT 4022, 406 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4022 AND r.tag_id = 406)
UNION ALL SELECT 4023, 406 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4023 AND r.tag_id = 406)
UNION ALL SELECT 4023, 403 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4023 AND r.tag_id = 403)
UNION ALL SELECT 4024, 406 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4024 AND r.tag_id = 406)
UNION ALL SELECT 4025, 406 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4025 AND r.tag_id = 406)
UNION ALL SELECT 4026, 407 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4026 AND r.tag_id = 407)
UNION ALL SELECT 4027, 407 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4027 AND r.tag_id = 407)
UNION ALL SELECT 4028, 407 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4028 AND r.tag_id = 407)
UNION ALL SELECT 4029, 408 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4029 AND r.tag_id = 408)
UNION ALL SELECT 4030, 408 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4030 AND r.tag_id = 408)
UNION ALL SELECT 4030, 407 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 4030 AND r.tag_id = 407);
