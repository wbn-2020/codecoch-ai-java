-- V4_129: Phase 1 里程碑 #1 — 知识点包 #1「Java 集合框架」。
-- 数据模型：一个 question_group = 一个知识点包（main_knowledge_point = 主知识点），
--           其下多道 question 通过 group_id 归属。分类挂到 init.sql 已有的「集合」(category_id=2)。
-- 幂等：group/tag 用 NOT EXISTS 守卫；question 用 ON DUPLICATE KEY UPDATE。
-- 这是 10 个包中的第 1 个，范式确定后其余包照此复制（每包一个 V4_13x 迁移）。

-- ===== 1. 标签（跨包复用，高 id 避免与 init.sql 1–6 冲突） =====
INSERT INTO question_tag (id, tag_name, status)
SELECT 101, 'HashMap', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 101)
UNION ALL
SELECT 102, 'ConcurrentHashMap', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 102)
UNION ALL
SELECT 103, 'ArrayList', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 103)
UNION ALL
SELECT 104, 'LinkedList', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 104)
UNION ALL
SELECT 105, '迭代器', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 105)
UNION ALL
SELECT 106, '阻塞队列', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 106);

-- ===== 2. 知识点组（6 个主知识点，category_id=2 集合） =====
INSERT INTO question_group (id, group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status)
SELECT 1001, 'HashMap 底层与扩容', 'HashMap 的 put/get 与扩容流程是什么？',
       'HashMap 通过 hash 定位桶位，链表或红黑树解决冲突；size 超过 容量×负载因子 时扩容并迁移节点，JDK 1.8 链表长度≥8 且桶数≥64 时树化为红黑树。',
       'HashMap 冲突与扩容', 'MEDIUM', '考察存储结构、哈希定位、扩容与树化边界。', 2, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 1001)
UNION ALL
SELECT 1002, 'ConcurrentHashMap 线程安全', 'ConcurrentHashMap 1.7 与 1.8 的实现区别？',
       '1.7 用分段锁 Segment 降低锁粒度；1.8 废弃 Segment，改用 Node + CAS + synchronized 锁定单个桶头节点，读几乎无锁，并发度更高。',
       'ConcurrentHashMap 线程安全', 'HARD', '考察并发容器演进与锁粒度优化。', 2, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 1002)
UNION ALL
SELECT 1003, 'List 实现与扩容', 'ArrayList 与 LinkedList 的核心区别？',
       'ArrayList 基于动态数组，随机访问 O(1)、尾部插入 amortized O(1)、中间插入 O(n)；LinkedList 基于双向链表，插入删除 O(1) 但随机访问 O(n)、内存开销更大。',
       'List 实现与扩容', 'EASY', '考察顺序表与链表的取舍。', 2, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 1003)
UNION ALL
SELECT 1004, '迭代器快速失败', 'fail-fast 与 fail-safe 迭代器区别？',
       'fail-fast（如 ArrayList 的 Iterator）在迭代中检测到结构变更会抛 ConcurrentModificationException；fail-safe（如 CopyOnWriteArrayList / 并发容器的弱一致迭代器）基于快照或弱一致，不会抛异常但可能看不到最新变更。',
       '迭代器快速失败', 'MEDIUM', '考察并发修改检测与弱一致语义。', 2, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 1004)
UNION ALL
SELECT 1005, 'Set 去重与有序', 'HashSet / TreeSet 的底层与排序原理？',
       'HashSet 基于 HashMap 的 key 去重，依赖 hashCode+equals；TreeSet 基于红黑树，按 Comparable/Comparator 有序，插入自平衡，查找 O(log n)。',
       'Set 去重与有序', 'MEDIUM', '考察去重机制与有序结构。', 2, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 1005)
UNION ALL
SELECT 1006, '队列与阻塞队列', '阻塞队列如何实现生产-消费？',
       'ArrayBlockingQueue / LinkedBlockingQueue 用可重入锁 + 两个条件变量（notEmpty / notFull）实现入队阻塞与出队阻塞；ArrayBlockingQueue 单锁有界，LinkedBlockingQueue 默认无界（可指定容量）且入队出队各一把锁。',
       '队列与阻塞队列', 'MEDIUM', '考察阻塞队列的锁与条件变量实现。', 2, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 1006);

-- ===== 3. 题目（18 道，category_id=2，group_id 指向知识点组） =====
INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, question_type, experience_level, is_high_frequency, status)
VALUES
  -- —— HashMap（组 1001）——
  (1001, 'HashMap 的 put 流程是怎样的？', '请描述 HashMap 从计算哈希到存入桶位的完整流程。',
   '1) key 的 hashCode 经扰动函数（高 16 位异或低 16 位）得到 hash；2) (n-1)&hash 定位桶下标；3) 桶为空直接放 Node；4) 冲突时若桶是红黑树则按树插入，否则遍历链表，key 已存在则覆盖、否则尾插；5) 链表长度≥8 且表长≥64 时树化；6) size 超 阈值(容量×0.75) 时扩容 2 倍并 rehash 迁移。',
   '要覆盖扰动函数、定位、冲突处理、树化阈值、扩容 rehash 五点。', 2, 1001, 'MEDIUM', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (1002, 'HashMap 为什么线程不安全？多线程下会出什么问题？', '从结构上说明 HashMap 在并发场景的风险。',
   'JDK 1.7 头插法扩容在并发下会形成循环链导致死循环与数据丢失；1.8 尾插虽避免了环，但 put 非原子，多线程同时 put 仍会覆盖、size 统计不准、可能丢数据。因此并发场景应使用 ConcurrentHashMap。',
   '1.7 死循环 + 1.8 覆盖丢失，结论指向 ConcurrentHashMap。', 2, 1001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (1003, '为什么 JDK 1.8 的 HashMap 引入红黑树？何时树化、何时退化？', '解释树化的动机与阈值。',
   '链表过长时查询退化为 O(n)。树化将查找降到 O(log n)。树化条件：链表长度≥8 且 表容量≥64；若容量不足 64 优先扩容而非树化。退化：树节点数≤6 时退化为链表，避免频繁结构切换。',
   '动机=O(n)→O(log n)；树化 8/64，退化 6。', 2, 1001, 'HARD', 'SHORT_ANSWER', 'MID', 1, 1),
  (1004, 'HashMap 的容量为什么是 2 的幂？负载因子和扩容阈值如何取值？', '说明容量设计与取模优化。',
   '容量取 2 的幂，使 (n-1)&hash 等价于取模但更快，且扩容时高位落点均匀分布。默认容量 16、负载因子 0.75，阈值=容量×0.75；因子过大冲突多、过小浪费空间。',
   '2 的幂→位运算取模；默认 16/0.75 的权衡。', 2, 1001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— ConcurrentHashMap（组 1002）——
  (1005, 'ConcurrentHashMap 1.7 和 1.8 的实现有何区别？', '对比两代实现的锁策略。',
   '1.7 使用分段锁 Segment（继承 ReentrantLock），默认 16 段，并发度受段数限制；1.8 移除 Segment，改用 table 数组 + Node，put 时对目标桶头节点 CAS 或 synchronized 加锁，锁粒度细化到单个桶，并发度更高，且 size 统计改用 baseCount + CounterCell 分段累加。',
   '核心差异：分段锁 vs 桶级锁（CAS+synchronized）。', 2, 1002, 'HARD', 'SHORT_ANSWER', 'SENIOR', 1, 1),
  (1006, 'ConcurrentHashMap 1.8 的 put 如何保证线程安全？为什么放弃分段锁？', '说明加锁对象与演进原因。',
   'put 时先 CAS 尝试写入空桶；若桶已存在则对桶头节点 synchronized 加锁再操作，锁粒度是单个桶而非整段，冲突概率更低、并发更高。放弃分段锁是因为段数固定导致并发度上限且内存开销大。',
   '桶头节点加锁；放弃原因是锁粒度与并发度。', 2, 1002, 'HARD', 'SCENARIO', 'SENIOR', 1, 1),
  (1007, 'ConcurrentHashMap 的 size() 是精确的吗？如何统计？', '说明并发下的统计策略。',
   '不是强一致的精确值。1.8 用 baseCount 记录无竞争时的计数，竞争时把增量分散到 CounterCell 数组，size() 返回 baseCount + 各 CounterCell 的累加和，反映的是"最终一致"的近似实时数量。',
   'baseCount + CounterCell 分段累加，弱一致。', 2, 1002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (1008, 'ConcurrentHashMap 的读操作为什么几乎不需要加锁？', '解释可见性保证。',
   'Node 的 value 与 next 用 volatile 修饰，保证写后对其他线程立即可见；读操作只做 volatile 读，无需加锁即可拿到最新值。仅 size 等聚合统计才涉及分段累加。',
   'volatile value/next 提供可见性，故读无锁。', 2, 1002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— List 实现与扩容（组 1003）——
  (1009, 'ArrayList 的扩容机制是怎样的？为什么是 1.5 倍？', '描述扩容过程与系数取舍。',
   '默认空数组，首次 add 扩容到 10；之后当 size==capacity 时，新容量 = 旧容量 + (旧容量>>1) 即 1.5 倍，拷贝旧数组到新数组。1.5 倍在"减少扩容次数"与"降低空间浪费"之间平衡，且便于旧容量与新容量差值的复用。',
   '10 起步、1.5 倍拷贝；系数权衡次数与空间。', 2, 1003, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (1010, 'ArrayList 和 LinkedList 在随机访问、插入删除、内存上有什么区别？', '从数据结构角度对比。',
   'ArrayList 连续数组，随机访问 O(1)、尾部追加快、中间插入需搬移 O(n)；LinkedList 双向链表，随机访问 O(n)、已知节点插入删除 O(1) 但遍历成本高，且每个节点有前后指针额外内存开销。选型的依据是访问模式与插入位置。',
   '数组 vs 链表的三维对比 + 选型依据。', 2, 1003, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (1011, 'ArrayList 遍历中删除元素为什么会抛异常？如何正确删除？', '结合 fail-fast 说明。',
   '普通 for 循环按索引删除会漏元素/越界；foreach 底层用 Iterator，删除会触发 checkForComodification 抛 ConcurrentModificationException。正确做法：用 Iterator 的 remove()（它同步维护 expectedModCount），或用 removeIf / 倒序遍历。',
   'foreach 用 Iterator→fail-fast；正确用 Iterator.remove()/removeIf。', 2, 1003, 'MEDIUM', 'SCENARIO', 'MID', 1, 1),

  -- —— 迭代器快速失败（组 1004）——
  (1012, '什么是 fail-fast 和 fail-safe 迭代器？各举例子。', '解释两类迭代器的设计差异。',
   'fail-fast：迭代时若集合结构被修改（modCount 变化）立即抛 ConcurrentModificationException，如 ArrayList/HashMap 的 Iterator。fail-safe：基于集合快照或弱一致迭代，不抛异常，如 CopyOnWriteArrayList、ConcurrentHashMap 的弱一致迭代器，但可能看不到迭代期间的更新。',
   'fail-fast 抛异常（ArrayList）；fail-safe 快照（COW）。', 2, 1004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (1013, '如何在遍历集合时安全地删除元素？', '给出不止一种正确做法。',
   '推荐用 Iterator.remove()（同步维护 expectedModCount）；或使用 removeIf(Predicate)；单线程且逆序可用普通 for；并发场景直接用并发容器（如 ConcurrentHashMap.keySet().removeIf）。避免在 foreach 体内调用集合自身的 remove。',
   'Iterator.remove()/removeIf/并发容器三条路径。', 2, 1004, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),

  -- —— Set 去重与有序（组 1005）——
  (1014, 'HashSet 如何保证元素不重复？和 HashMap 什么关系？', '说明底层实现。',
   'HashSet 内部持有一个 HashMap，元素作为 map 的 key 存入，value 是一个固定的空对象；因此去重依赖 key 的 hashCode() 与 equals()：先比 hash 定位桶，再 equals 判等。',
   'HashSet = HashMap 的 key 封装；依赖 hashCode+equals。', 2, 1005, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (1015, 'TreeSet / TreeMap 的排序与自平衡原理？', '解释红黑树相关。',
   'TreeMap 底层是红黑树（自平衡二叉查找树），元素按 key 的 Comparable 自然序或 Comparator 插入；插入/删除时通过变色与旋转维持近似平衡，保证查找、插入、删除均为 O(log n)。TreeSet 即 TreeMap 的 key 视图。',
   '红黑树自平衡；Comparable/Comparator；O(log n)。', 2, 1005, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (1016, 'Comparable 与 Comparator 的区别？', '说明使用场景。',
   'Comparable 是元素自身实现的内部比较器（compareTo），定义"自然序"；Comparator 是外部比较器（compare），可临时、灵活、多策略地指定排序规则，优先级高于 Comparable。',
   '内部自然序 vs 外部多策略；Comparator 优先。', 2, 1005, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),

  -- —— 队列与阻塞队列（组 1006）——
  (1017, 'ArrayDeque 与 LinkedList 作为栈/队列有何区别？', '从实现与性能角度对比。',
   'ArrayDeque 基于循环数组，作为栈和双端队列性能优于 Stack 和 LinkedList，且无额外节点开销；LinkedList 基于链表，每个节点有指针开销，且在并发修改下更易出问题。官方推荐用 ArrayDeque 替代 Stack。',
   'ArrayDeque 循环数组更优；替代 Stack。', 2, 1006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (1018, 'ArrayBlockingQueue 与 LinkedBlockingQueue 如何实现阻塞？', '说明锁与条件变量的分工。',
   '二者都用 ReentrantLock + 两个条件变量 notEmpty / notFull 实现阻塞：出队时队列空则 notEmpty.await，入队后 signal；入队时队列满则 notFull.await，出队后 signal。区别在于 ArrayBlockingQueue 单锁有界，LinkedBlockingQueue 默认无界（可设容量）且入队/出队各一把锁，吞吐量更高。',
   '锁+双条件实现阻塞；单锁有界 vs 双锁（默认无界）。', 2, 1006, 'MEDIUM', 'SCENARIO', 'MID', 1, 1)
ON DUPLICATE KEY UPDATE
  title = VALUES(title), content = VALUES(content), reference_answer = VALUES(reference_answer),
  analysis = VALUES(analysis), category_id = VALUES(category_id), group_id = VALUES(group_id),
  difficulty = VALUES(difficulty), question_type = VALUES(question_type), experience_level = VALUES(experience_level),
  is_high_frequency = VALUES(is_high_frequency), status = VALUES(status);

-- ===== 4. 题目—标签关系 =====
INSERT INTO question_tag_relation (question_id, tag_id)
SELECT 1001, 101 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1001 AND r.tag_id = 101)
UNION ALL SELECT 1002, 101 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1002 AND r.tag_id = 101)
UNION ALL SELECT 1003, 101 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1003 AND r.tag_id = 101)
UNION ALL SELECT 1004, 101 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1004 AND r.tag_id = 101)
UNION ALL SELECT 1005, 102 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1005 AND r.tag_id = 102)
UNION ALL SELECT 1006, 102 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1006 AND r.tag_id = 102)
UNION ALL SELECT 1007, 102 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1007 AND r.tag_id = 102)
UNION ALL SELECT 1008, 102 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1008 AND r.tag_id = 102)
UNION ALL SELECT 1009, 103 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1009 AND r.tag_id = 103)
UNION ALL SELECT 1010, 103 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1010 AND r.tag_id = 103)
UNION ALL SELECT 1011, 103 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1011 AND r.tag_id = 103)
UNION ALL SELECT 1012, 105 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1012 AND r.tag_id = 105)
UNION ALL SELECT 1013, 105 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1013 AND r.tag_id = 105)
UNION ALL SELECT 1014, 101 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1014 AND r.tag_id = 101)
UNION ALL SELECT 1015, 101 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1015 AND r.tag_id = 101)
UNION ALL SELECT 1016, 101 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1016 AND r.tag_id = 101)
UNION ALL SELECT 1017, 104 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1017 AND r.tag_id = 104)
UNION ALL SELECT 1018, 106 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 1018 AND r.tag_id = 106);
