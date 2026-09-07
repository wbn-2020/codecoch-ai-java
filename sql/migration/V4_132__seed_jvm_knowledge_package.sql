-- V4_132: 知识点包 #3「JVM」。
-- 数据模型：一个 question_group = 一个知识点包（main_knowledge_point = 主知识点），
--           其下多道 question 通过 group_id 归属。分类挂到 init.sql 已有的「JVM」(category_id=4)。
-- 幂等：group/tag 用 NOT EXISTS 守卫；question 用 ON DUPLICATE KEY UPDATE；
--       relation 用 SELECT qid, tid FROM DUAL WHERE NOT EXISTS(...)（扁平形式，避免 V4_129 的 bug）。

-- ===== 1. 标签（12 个 JVM 子主题，tag id 301–312） =====
INSERT INTO question_tag (id, tag_name, status)
SELECT 301, '运行时数据区', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 301)
UNION ALL
SELECT 302, '垃圾回收算法', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 302)
UNION ALL
SELECT 303, '垃圾收集器', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 303)
UNION ALL
SELECT 304, '类加载机制', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 304)
UNION ALL
SELECT 305, 'JVM调优参数', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 305)
UNION ALL
SELECT 306, '内存溢出OOM', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 306)
UNION ALL
SELECT 307, 'GC日志分析', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 307)
UNION ALL
SELECT 308, '对象内存布局', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 308)
UNION ALL
SELECT 309, '引用类型', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 309)
UNION ALL
SELECT 310, '逃逸分析', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 310)
UNION ALL
SELECT 311, '元空间', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 311)
UNION ALL
SELECT 312, '线上排查', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 312);

-- ===== 2. 知识点组（8 个主知识点，category_id=4 JVM） =====
INSERT INTO question_group (id, group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status)
SELECT 3001, 'JVM 运行时数据区', 'What are the JVM runtime data areas and which are thread-private?',
       'The JVM divides memory into thread-private areas (PC register, Java virtual machine stack, native method stack) and thread-shared areas (heap, method area / metaspace). The heap holds objects and is the main GC target; the method area stores class metadata, constants and static variables.',
       'Runtime data areas and thread-private vs shared', 'MEDIUM', '考察运行时数据区划分、线程私有与共享区域、各区域职责。', 4, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 3001)
UNION ALL
SELECT 3002, '垃圾回收算法', 'What are the main garbage collection algorithms and their trade-offs?',
       'Mark-Sweep marks live objects then reclaims free space; simple but fragments memory. Copy divides space into two halves and copies live objects to the other half; no fragmentation but wastes half the space, suited to the young generation. Mark-Compact marks then compacts live objects to one end; no fragmentation and no wasted space but slower, suited to the old generation.',
       'Mark-Sweep vs Copy vs Mark-Compact', 'MEDIUM', '考察三大基础 GC 算法及其空间/时间权衡。', 4, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 3002)
UNION ALL
SELECT 3003, '垃圾收集器 G1/ZGC', 'How do modern collectors G1 and ZGC achieve low pause times?',
       'G1 partitions the heap into regions and tracks remembered sets, collecting the regions with the most garbage first within a target pause time, using SATB for concurrent marking. ZGC uses colored pointers in the object reference and load barriers to relocate objects concurrently with application threads, keeping pauses within a few milliseconds even for very large heaps.',
       'G1 region-based collection and ZGC colored pointers', 'MEDIUM', '考察 G1 分区收集与 ZGC 染色指针、并发转移。', 4, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 3003)
UNION ALL
SELECT 3004, '类加载机制', 'What is the class loading process and the parent delegation model?',
       'Class loading has three phases: loading (find and read bytecode into a Class object), linking (verification, preparation which sets static default values, resolution of symbolic references), and initialization (running the static initializer and field assignments). The parent delegation model lets a class loader first delegate to its parent before attempting to load itself, ensuring core classes load once and cannot be tampered with.',
       'Class loading phases and parent delegation', 'MEDIUM', '考察加载/链接/初始化三阶段与双亲委派。', 4, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 3004)
UNION ALL
SELECT 3005, 'JVM 内存与 GC 调优参数', 'What are the key JVM memory and GC tuning parameters?',
       'Heap size is set by -Xms and -Xmx; the young/old ratio by -XX:NewRatio; young size by -Xmn; the survivor ratio by -XX:SurvivorRatio. GC choice uses -XX:+UseG1GC / -XX:+UseZGC. Metaspace is bounded by -XX:MaxMetaspaceSize. GC logs are enabled with -Xlog:gc* (JDK 9+) or -XX:+PrintGCDetails (JDK 8).',
       'Core JVM heap, GC, and metaspace tuning flags', 'MEDIUM', '考察堆、新生代、GC、元空间与日志相关关键参数。', 4, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 3005)
UNION ALL
SELECT 3006, '内存溢出 OOM 排查', 'What are the common OutOfMemoryError types and how do you diagnose them?',
       'Common types: Java heap space (objects exceed the heap, fix by raising -Xmx or finding leaks via heap dump), GC overhead limit exceeded (too much time in GC with little reclaimed), Metaspace (too many loaded classes, raise MaxMetaspaceSize), and Unable to create new native thread (too many threads or too small a stack). Diagnosis uses heap dumps, GC logs and jstat.',
       'OOM types and heap dump diagnosis', 'MEDIUM', '考察各类 OOM 的成因与排查手段。', 4, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 3006)
UNION ALL
SELECT 3007, 'GC 日志与监控分析', 'How do you read and interpret GC logs for troubleshooting?',
       'GC logs show pause type (Young / Mixed / Full), heap occupancy before and after collection, and pause duration. A healthy service has short young pauses and rare full GC. A full GC storm indicates old generation pressure, metaspace pressure, or explicit System.gc. Key metrics to monitor are GC pause frequency and duration, old generation growth rate, and promotion failure.',
       'GC log structure and Full GC diagnosis', 'MEDIUM', '考察 GC 日志字段含义与 Full GC 风暴定位。', 4, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 3007)
UNION ALL
SELECT 3008, '对象创建与回收生命周期', 'From new to GC: how are objects created, allocated, and reclaimed?',
       'Object creation runs class init if needed, allocates memory in the young eden (often via TLAB using a bump pointer), runs the constructor, and returns a reference. After becoming garbage, young objects are collected by minor GC via copy to survivor/old; reaching old age triggers major collection. Reachability is decided by GC roots; reference strength (strong/soft/weak/phantom) changes reclamation timing.',
       'Object allocation, TLAB, and reachability-based GC', 'MEDIUM', '考察对象分配、TLAB、可达性判定与引用强度。', 4, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 3008);

-- ===== 3. 题目（30 道，category_id=4，group_id 指向知识点组） =====
INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, question_type, experience_level, is_high_frequency, status)
VALUES
  -- —— 运行时数据区（组 3001）——
  (3001, 'What are the JVM runtime data areas and which are thread-private?', 'List the memory regions of the JVM and classify them as thread-private or thread-shared, with the responsibility of each.',
   'Thread-private: PC register (holds the address of the currently executing instruction), Java virtual machine stack (frame with local variables, operand stack, dynamic linking), and native method stack (for native methods). Thread-shared: heap (object instances and arrays, the main GC target) and method area / metaspace (class metadata, constants, static variables, JIT code cache).',
   'A good answer lists all five regions, clearly marks private vs shared, and states what each holds.', 4, 3001, 'MEDIUM', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (3002, 'How is a Java object laid out in heap memory?', 'Explain the object header, instance data, and alignment padding, and what the header stores.',
   'An object in heap consists of an object header (mark word holding hashcode, GC age, lock state, plus a type pointer to the class metadata), instance data (field values, with alignment rules across inheritance), and alignment padding to a multiple of 8 bytes. On 64-bit JVMs with compressed oops the type pointer is 4 bytes.',
   'Cover mark word, klass pointer, instance data ordering, and 8-byte alignment padding.', 4, 3001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (3003, 'What is the difference between the Java virtual machine stack and the native method stack?', 'Explain their purpose and what each one stores.',
   'The JVM stack serves Java method execution with stack frames (local variables, operand stack, return address). The native method stack serves native (JNI) methods and is implemented by the native platform. The HotSpot VM merges the two into one stack in practice, but conceptually they serve different languages.',
   'Distinguish Java-method frames vs native-method frames and mention HotSpot merging them.', 4, 3001, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),
  (3004, 'Why is the PC register thread-private and what does it store?', 'Explain the role of the program counter in a multithreaded JVM.',
   'Each thread needs its own program counter because the CPU switches between threads; the PC holds the address of the next bytecode instruction for that thread. If the thread is executing a native method the PC is undefined. It is the only JVM area without an OutOfMemoryError.',
   'Emphasize per-thread isolation and that native methods leave the PC undefined.', 4, 3001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— 垃圾回收算法（组 3002）——
  (3005, 'Explain the mark-sweep, copy, and mark-compact algorithms and their trade-offs.', 'Compare the three foundational GC algorithms in terms of fragmentation, space usage, and speed.',
   'Mark-Sweep marks live objects then reclaims dead ones; it is fast but leaves memory fragmentation. Copy divides space in two, copies live objects to the other half, and clears the source; no fragmentation but wastes half the space, ideal for the young generation. Mark-Compact marks then slides live objects to one end; no fragmentation and no wasted space but is slower due to object movement, ideal for the old generation.',
   'Trade-off table: fragmentation (copy/compact good, sweep bad), space (copy wastes half), speed (sweep/copy fast, compact slow).', 4, 3002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (3006, 'How does generational garbage collection work and why use generations?', 'Explain the young/old split and the weak generational hypothesis.',
   'The heap is split into young (eden + survivor) and old generations based on the weak generational hypothesis: most objects die young. New objects go to eden; surviving minor GCs are copied to survivor and eventually promoted to old. Collecting the young frequently and the old rarely reduces pause time and work, since only a small live set must be traced.',
   'Mention weak generational hypothesis, minor vs major GC, and promotion / tenuring threshold.', 4, 3002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (3007, 'What is three-color marking and how do concurrent collectors stay correct?', 'Explain the tri-color abstraction and how a collector avoids missing objects when the app mutates the graph.',
   'Three-color marking classifies objects as white (unvisited), gray (visited but children not yet), black (fully scanned). A concurrent collector must preserve the invariant that a black object never points to a white object without a gray path, or the white object is reclaimed prematurely. CMS uses incremental update (record when a black gets a new white ref), while G1/ZGC use SATB (snapshot at beginning) to treat pre-snapshot white objects as live.',
   'Cover the invariant, why mutation is dangerous, and the CMS-vs-SATB difference.', 4, 3002, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (3008, 'How does reference counting differ from tracing garbage collection?', 'Compare the two fundamental approaches and their limitations.',
   'Reference counting keeps a counter per object and reclaims when it hits zero; it collects immediately and locally but cannot handle cycles and adds overhead on every assignment. Tracing GC (mark-sweep / copy) starts from GC roots and marks reachable objects, handling cycles naturally but requiring a global pause or concurrent phases. JVM uses tracing with generational collection, not reference counting.',
   'State the cycle problem of ref-counting and that the JVM uses tracing GC.', 4, 3002, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),

  -- —— 垃圾收集器（组 3003）——
  (3009, 'Compare Serial, Parallel, CMS, and G1 collectors.', 'Outline the concurrency model, pause characteristics, and typical use of each collector.',
   'Serial uses a single thread and is for small/heap clients. Parallel (Parallel Old) uses multiple threads for throughput-oriented batch work but still stops the world. CMS collects the old generation concurrently with the app to minimize pause, but fragments and has remark pauses. G1 is the modern default: region-based, predictable pause targets, and handles both young and old without a separate CMS phase.',
   'Compare by thread model, STW vs concurrent, pause behavior, and default suitability.', 4, 3003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (3010, 'How does G1 achieve predictable pause times?', 'Explain regions, remembered sets, and the pause-time target.',
   'G1 divides the heap into equal regions (eden / survivor / old / humongous). It tracks inter-region references with remembered sets (cards). During a mixed collection it selects the regions with the highest garbage (garbage-first) to meet the -XX:MaxGCPauseMillis target, reclaiming the most space for the allowed pause budget. Concurrent marking uses SATB to avoid missing objects.',
   'Mention regions, remembered sets / card tables, garbage-first selection, and the pause target flag.', 4, 3003, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 1, 1),
  (3011, 'What are ZGC core designs: colored pointers and load barriers?', 'Explain how ZGC relocates objects concurrently with such low pauses.',
   'ZGC stores metadata (mark, relocation, remap, finalizable) in the reference itself via colored pointers (using unused high bits of a 64-bit address). A load barrier on every object load checks those bits and, if the object has been moved, heals the reference by remapping it to the new location. Because relocation is done concurrently and references are fixed lazily on access, pauses stay under a few milliseconds regardless of heap size.',
   'Cover colored pointers (high bits), load barrier, concurrent relocation, and sub-millisecond pauses.', 4, 3003, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (3012, 'When would you choose ZGC over G1?', 'Give the decision criteria between the two modern collectors.',
   'Choose ZGC when the heap is very large (tens to hundreds of GB) and the service needs consistent single-digit-millisecond pause times (low-latency trading, real-time APIs). Choose G1 for general-purpose workloads where a few hundred millisecond pauses are acceptable and memory overhead should be lower, since ZGC colored pointers and load barriers add some CPU and footprint cost.',
   'Decision hinges on heap size, latency SLA, and the CPU/footprint overhead of ZGC.', 4, 3003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— 类加载机制（组 3004）——
  (3013, 'Explain the class loading process: loading, linking, and initialization.', 'Describe what happens in each of the three phases.',
   'Loading finds the class binary and builds a Class object in the method area. Linking has three sub-steps: verification (bytecode safety), preparation (allocates static fields and sets default zero values, not initial values), and resolution (replaces symbolic references with direct pointers, may be lazy). Initialization runs static initializers and field assignments in order, triggered by first active use (new, static call, reflection, etc.).',
   'Cover the three phases, preparation zeroing vs initialization assignment, and the active-use trigger.', 4, 3004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (3014, 'What is the parent delegation model?', 'Explain how class loaders delegate and why it matters.',
   'A class loader first asks its parent to load a class before attempting itself, climbing up to the bootstrap loader; only if the parent fails does it try its own path. This ensures core JDK classes are loaded once by the bootstrap loader and cannot be shadowed by user classes, protecting type safety and preventing duplicate classes.',
   'Mention the upward delegation chain, load-once guarantee, and security against tampering.', 4, 3004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (3015, 'How can parent delegation be broken, and with what examples?', 'Give real cases where the delegation chain is bypassed.',
   'SPI mechanisms (JDBC, JAXP) load implementation classes via thread-context class loader so the bootstrap-loaded API can reach application classes, breaking strict upward delegation. Web containers like Tomcat use WebAppClassLoader to isolate web apps and load app classes before the parent for hot redeploy. OSGi and custom loaders also redefine the hierarchy for modularity.',
   'Examples: SPI context class loader, Tomcat WebAppClassLoader, OSGi; show why bypass is needed.', 4, 3004, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (3016, 'When does class initialization happen, and how does it differ from loading?', 'Distinguish loading from initialization and list the triggering actions.',
   'Loading only reads bytecode; initialization runs static initializers and assigns static field values. It triggers on first active use: new instance, static field access/assignment (except final constants), static method call, reflection, subclass init, and JVM startup main class. Passive references (accessing a superclass static field from a subclass, or a final compile-time constant) do not trigger initialization.',
   'Contrast loading vs init and list active-use triggers plus passive-reference exceptions.', 4, 3004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— JVM 调优参数（组 3005）——
  (3017, 'What are the common JVM heap and GC tuning parameters?', 'List the key flags for heap size, generation ratio, GC selection, and logging.',
   '-Xms / -Xmx set initial and max heap. -Xmn sets young generation size; -XX:NewRatio sets old/young ratio; -XX:SurvivorRatio sets eden/survivor ratio. GC is chosen by -XX:+UseG1GC, -XX:+UseZGC, etc. -XX:MaxMetaspaceSize bounds metaspace. GC logs: -Xlog:gc* (JDK 9+) or -XX:+PrintGCDetails -Xloggc:file (JDK 8).',
   'Expect the heap, young, survivor, GC-choice, and logging flags at minimum.', 4, 3005, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (3018, 'How do you size the heap and pick a GC for a latency-sensitive service?', 'Walk through a tuning approach for a low-latency backend.',
   'Start with a heap large enough that the old generation grows slowly (avoid frequent GC), set -Xms = -Xmx to avoid resize pauses, and choose G1 or ZGC. Set a G1 pause target via -XX:MaxGCPauseMillis, or pick ZGC for sub-10ms SLA. Tune metaspace bound, enable GC logging, then measure pause frequency/duration under load and adjust generation ratios rather than guessing.',
   'Approach: equal min/max heap, pick concurrent collector, set pause target, measure then adjust.', 4, 3005, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (3019, 'What are the Metaspace parameters and what do they control?', 'Explain Metaspace sizing and its relation to class metadata.',
   'Metaspace (replacing PermGen since JDK 8) holds class metadata off the heap, using native memory. -XX:MetaspaceSize is the initial high-water mark that triggers GC; -XX:MaxMetaspaceSize caps it (unlimited by default, risking native OOM); -XX:MinMetaspaceFreeRatio / -XX:MaxMetaspaceFreeRatio control GC thresholds. A class-loader leak causes Metaspace OOM.',
   'Mention MetaspaceSize trigger, MaxMetaspaceSize cap, and class-loader leak risk.', 4, 3005, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (3020, 'How do you enable and read JVM GC logging parameters?', 'Show the flags for GC logs and the key fields to watch.',
   'On JDK 9+: -Xlog:gc*:file=/path/gc.log:time,uptime,level:filecount=5,filesize=100M. On JDK 8: -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:/path/gc.log. Watch: GC cause, collected region type, heap used before/after, and pause time. Pair with -XX:+HeapDumpOnOutOfMemoryError for crash analysis.',
   'Give both JDK 8 and 9+ flags and note the fields to watch plus OOM dump flag.', 4, 3005, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— 内存溢出 OOM（组 3006）——
  (3021, 'What are the common types of OutOfMemoryError and their causes?', 'Enumerate the major OOM subtypes and the typical root cause of each.',
   'java.lang.OutOfMemoryError: Java heap space (live objects exceed -Xmx, often a leak). GC overhead limit exceeded (over 98% time in GC yet under 2% reclaimed). Metaspace (too many loaded classes). Unable to create new native thread (thread count or stack size too high, or OS limits). Direct buffer / native memory exhaustion also occurs.',
   'List at least heap, GC overhead, metaspace, and unable-to-create-thread with causes.', 4, 3006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (3022, 'How do you analyze a heap OOM from a heap dump?', 'Describe an end-to-end diagnosis using MAT or a similar tool.',
   'Capture a dump with -XX:+HeapDumpOnOutOfMemoryError or jmap -dump. Open it in MAT / VisualVM, look at the Dominator Tree and Histogram to find the class holding the most retained bytes, inspect GC Roots paths (Path to GC Roots) to see who keeps it alive, and identify leaks such as unbounded caches or static collections. Then fix by bounding the cache or releasing references.',
   'Steps: capture dump, find top retained class, trace GC roots, locate the leak holder.', 4, 3006, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (3023, 'How do StackOverflowError and thread-stack OOM differ?', 'Explain the two failure modes related to stacks.',
   'StackOverflowError is thrown when a single thread's stack exceeds -Xss because of deep recursion or an unbounded chain (the thread itself runs out of its own stack). OutOfMemoryError: Unable to create new native thread is thrown when the JVM as a whole cannot allocate another thread, usually because of too many threads, too large an -Xss, or hitting OS/user process limits.',
   'Distinguish one-thread deep recursion from whole-process thread exhaustion.', 4, 3006, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),
  (3024, 'What causes Metaspace OOM and how do you fix it?', 'Explain why class metadata can overflow and the remedies.',
   'Metaspace OOM happens when classes are loaded faster than they are unloaded: typical causes are class-loader leaks in app servers / frameworks, dynamic proxy or bytecode generation (CGLIB, reflection) without cleanup, and redeploys that keep old loaders alive. Fix by raising -XX:MaxMetaspaceSize, finding and releasing the leaking class loader, or limiting proxy generation.',
   'Root cause is class-loader leak; fix via bound, leak hunt, and limiting proxy generation.', 4, 3006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— GC 日志分析（组 3007）——
  (3025, 'How do you read a G1 GC log (young, mixed, full)?', 'Interpret the main entries of a G1 pause and what each number means.',
   'A young collection shows Eden regions reclaimed, survivor promotion, and pause time. A mixed collection additionally reclaims parts of the old generation selected by garbage ratio. A Full GC in G1 is a stop-the-world fallback (often from humongous allocation failure or System.gc) and is the event to avoid. Watch heap used before/after and the pause (user/sys/real) lines.',
   'Distinguish young vs mixed vs full, and flag full GC as the anomaly to eliminate.', 4, 3007, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (3026, 'How do you detect and diagnose a Full GC storm?', 'Give a monitoring and root-cause approach for frequent full GC.',
   'Monitor GC pause count and duration (Prometheus/JMX, GC logs). A full GC storm shows rising old generation occupancy and repeated long pauses. Root causes: memory leak filling old gen, metaspace pressure, explicit System.gc (disable with -XX:+DisableExplicitGC), or humongous-object allocation. Confirm with heap dump and GC log cause field, then size the heap or fix the leak.',
   'Approach: alert on full GC frequency, read cause field, check old/metaspace growth, fix leak or disable explicit gc.', 4, 3007, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (3027, 'What GC metrics should be monitored in production?', 'List the key indicators for GC health.',
   'Key metrics: GC pause frequency and duration (especially full GC), young/old generation occupancy and growth rate, promotion rate (how fast objects reach old gen), allocation rate, and metaspace usage. Alert on full GC count > 0 and on old-gen growth trend. These predict OOM before it happens.',
   'List pause, occupancy, promotion/allocation rate, and metaspace; alert on full GC.', 4, 3007, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— 对象生命周期（组 3008）——
  (3028, 'From new to GC: how are objects allocated and collected?', 'Trace object creation, TLAB allocation, and reclamation.',
   'new triggers class init if needed, then the JVM allocates memory in Eden, usually via a thread-local TLAB using a bump pointer for speed. The constructor runs and a reference is returned. On minor GC, live eden/survivor objects are copied to the other survivor (or promoted if old enough); dead ones are dropped. Old objects are reclaimed by major/mixed collection when unreachable from GC roots.',
   'Cover TLAB bump-pointer allocation, minor copy collection, promotion, and root reachability.', 4, 3008, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (3029, 'What are strong, soft, weak, and phantom references and how do they affect GC?', 'Explain each reference type and when its object is collected.',
   'Strong references keep the object alive as long as reachable. Soft references are cleared before an OOM, good for caches. Weak references are cleared on the next GC regardless, used by WeakHashMap. Phantom references are enqueued after finalization for post-mortem cleanup (e.g. off-heap memory) and cannot be resurrected. Reference queues notify when cleared.',
   'Distinguish the four by collection timing and give a use case for each.', 4, 3008, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (3030, 'What is escape analysis and how does it enable stack allocation and scalar replacement?', 'Explain how the JIT removes heap allocation for non-escaping objects.',
   'Escape analysis (enabled by -XX:+DoEscapeAnalysis) determines whether an object escapes the method or thread. If it does not escape, the JIT can allocate it on the stack instead of the heap (stack allocation) and, if the object is only used field-by-field, replace it with individual scalar variables (scalar replacement), eliminating allocation and GC entirely. Synchronization on a non-escaping object is also eliminated (lock elision).',
   'Cover escape test, stack allocation, scalar replacement, and lock elision as consequences.', 4, 3008, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1)
ON DUPLICATE KEY UPDATE
  title = VALUES(title), content = VALUES(content), reference_answer = VALUES(reference_answer),
  analysis = VALUES(analysis), category_id = VALUES(category_id), group_id = VALUES(group_id),
  difficulty = VALUES(difficulty), question_type = VALUES(question_type), experience_level = VALUES(experience_level),
  is_high_frequency = VALUES(is_high_frequency), status = VALUES(status);

-- ===== 4. 题目—标签关系（扁平 SELECT qid, tid FROM DUAL WHERE NOT EXISTS 形式，避免子查询列暴露 bug） =====
INSERT INTO question_tag_relation (question_id, tag_id)
SELECT 3001, 301 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3001 AND r.tag_id = 301)
UNION ALL SELECT 3002, 308 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3002 AND r.tag_id = 308)
UNION ALL SELECT 3002, 301 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3002 AND r.tag_id = 301)
UNION ALL SELECT 3003, 301 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3003 AND r.tag_id = 301)
UNION ALL SELECT 3004, 301 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3004 AND r.tag_id = 301)
UNION ALL SELECT 3005, 302 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3005 AND r.tag_id = 302)
UNION ALL SELECT 3006, 302 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3006 AND r.tag_id = 302)
UNION ALL SELECT 3007, 302 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3007 AND r.tag_id = 302)
UNION ALL SELECT 3008, 302 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3008 AND r.tag_id = 302)
UNION ALL SELECT 3009, 303 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3009 AND r.tag_id = 303)
UNION ALL SELECT 3010, 303 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3010 AND r.tag_id = 303)
UNION ALL SELECT 3011, 303 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3011 AND r.tag_id = 303)
UNION ALL SELECT 3012, 303 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3012 AND r.tag_id = 303)
UNION ALL SELECT 3013, 304 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3013 AND r.tag_id = 304)
UNION ALL SELECT 3014, 304 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3014 AND r.tag_id = 304)
UNION ALL SELECT 3015, 304 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3015 AND r.tag_id = 304)
UNION ALL SELECT 3016, 304 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3016 AND r.tag_id = 304)
UNION ALL SELECT 3017, 305 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3017 AND r.tag_id = 305)
UNION ALL SELECT 3018, 305 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3018 AND r.tag_id = 305)
UNION ALL SELECT 3019, 305 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3019 AND r.tag_id = 305)
UNION ALL SELECT 3019, 311 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3019 AND r.tag_id = 311)
UNION ALL SELECT 3020, 305 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3020 AND r.tag_id = 305)
UNION ALL SELECT 3020, 307 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3020 AND r.tag_id = 307)
UNION ALL SELECT 3021, 306 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3021 AND r.tag_id = 306)
UNION ALL SELECT 3022, 306 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3022 AND r.tag_id = 306)
UNION ALL SELECT 3023, 306 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3023 AND r.tag_id = 306)
UNION ALL SELECT 3024, 306 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3024 AND r.tag_id = 306)
UNION ALL SELECT 3024, 311 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3024 AND r.tag_id = 311)
UNION ALL SELECT 3025, 307 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3025 AND r.tag_id = 307)
UNION ALL SELECT 3026, 307 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3026 AND r.tag_id = 307)
UNION ALL SELECT 3026, 312 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3026 AND r.tag_id = 312)
UNION ALL SELECT 3027, 307 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3027 AND r.tag_id = 307)
UNION ALL SELECT 3027, 312 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3027 AND r.tag_id = 312)
UNION ALL SELECT 3028, 308 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3028 AND r.tag_id = 308)
UNION ALL SELECT 3029, 309 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3029 AND r.tag_id = 309)
UNION ALL SELECT 3030, 310 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 3030 AND r.tag_id = 310);
