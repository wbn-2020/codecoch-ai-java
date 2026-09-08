-- V4_130: Phase 1 里程碑 #1 — 知识点包 #2「Java 并发」。
-- 数据模型：一个 question_group = 一个知识点包（main_knowledge_point = 主知识点），
--           其下多道 question 通过 group_id 归属。分类挂到 init.sql 已有的「并发」(category_id=3)。
-- 幂等：group/tag 用 NOT EXISTS 守卫；question 用 ON DUPLICATE KEY UPDATE。
-- 这是 10 个包中的第 2 个，结构与 V4_129（集合框架）保持一致。

-- ===== 1. 标签（跨包复用，高 id 避免与 init.sql 1–6 及 V4_129 的 101–106 冲突） =====
INSERT INTO question_tag (id, tag_name, status)
SELECT 201, '线程池', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 201 OR tag_name = '线程池')
UNION ALL
SELECT 202, 'synchronized', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 202 OR tag_name = 'synchronized')
UNION ALL
SELECT 203, 'ReentrantLock', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 203 OR tag_name = 'ReentrantLock')
UNION ALL
SELECT 204, 'volatile', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 204 OR tag_name = 'volatile')
UNION ALL
SELECT 205, 'CAS', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 205 OR tag_name = 'CAS')
UNION ALL
SELECT 206, 'JUC', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 206 OR tag_name = 'JUC')
UNION ALL
SELECT 207, 'ThreadLocal', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 207 OR tag_name = 'ThreadLocal')
UNION ALL
SELECT 208, '并发容器', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 208 OR tag_name = '并发容器')
UNION ALL
SELECT 209, '死锁', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 209 OR tag_name = '死锁')
UNION ALL
SELECT 210, 'AQS', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 210 OR tag_name = 'AQS');

-- ===== 2. 知识点组（7 个主知识点，category_id=3 并发） =====
INSERT INTO question_group (id, group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status)
SELECT 2001, '线程池与任务调度', 'ThreadPoolExecutor 的核心参数与任务执行流程是什么？',
       'ThreadPoolExecutor 由 corePoolSize、maximumPoolSize、keepAliveTime、unit、workQueue、threadFactory、handler 七个参数决定行为；任务先创建核心线程执行，核心满了入队，队列满了才扩容到最大线程数，再满则执行拒绝策略。生产上应手动构造线程池并指定有界队列与拒绝策略，不要用 Executors 的无界队列工厂方法。',
       'ThreadPoolExecutor 参数与执行流程', 'MEDIUM', '考察线程池七大参数、任务流转顺序与拒绝策略。', 3, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 2001)
UNION ALL
SELECT 2002, '锁机制与 AQS', 'synchronized、ReentrantLock 与 AQS 的关系是怎样的？',
       'synchronized 基于对象头的 Mark Word 与 Monitor 实现，锁按 无锁 → 偏向锁 → 轻量级锁 → 重量级锁 单向升级，语义简单但能力有限；ReentrantLock 基于 AQS 的独占模式实现，用 volatile state 加 CLH 等待队列完成加锁、排队与唤醒，额外提供可中断、超时、公平锁与多 Condition。JDK 1.6 后两者性能接近，一般优先 synchronized，需要高级能力时选 ReentrantLock。',
       'synchronized 锁升级与 AQS 原理', 'HARD', '考察锁升级路径、AQS 的 state 与等待队列、两者选型。', 3, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 2002)
UNION ALL
SELECT 2003, 'volatile 与 Java 内存模型', 'volatile 能保证什么？能否保证原子性？',
       'JMM 通过主内存与工作内存的抽象加 happens-before 规则描述可见性、有序性与原子性。volatile 通过内存屏障保证可见性并禁止特定重排序，但不保证复合操作的原子性，volatile++ 仍需 CAS 或加锁。',
       'volatile 可见性、有序性与 JMM', 'MEDIUM', '考察可见性、禁止重排序、happens-before 及原子性边界。', 3, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 2003)
UNION ALL
SELECT 2004, 'CAS 与原子类', 'CAS 的原理、问题与 JUC 原子类的适用场景？',
       'CAS 是 CPU 提供的原子指令（cmpxchg），由 Unsafe 暴露，比较并交换 V 是否等于预期值 A，是则更新为 B，否则自旋重试。它存在 ABA、长时间自旋开销大、只能原子更新单个变量三个问题。AtomicInteger 用 volatile value + 自旋 CAS 实现；高竞争下 LongAdder 用 base + Cell[] 分段累加，吞吐更高但只能保证最终一致。',
       'CAS 原理、ABA 与原子类选型', 'MEDIUM', '考察 CAS 机制、ABA 问题与 LongAdder 分段思想。', 3, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 2004)
UNION ALL
SELECT 2005, 'JUC 并发工具', 'CountDownLatch、CyclicBarrier、Semaphore 有什么区别？',
       '三者都基于 AQS：CountDownLatch 是一次性倒数计数器，线程等待事件完成，count 减到 0 即放行且不可重置；CyclicBarrier 让一组线程相互等待到齐后一起继续，可 reset 复用并支持 barrierAction；Semaphore 用 permits 做限流，acquire 获取许可、release 归还，可配公平或非公平。',
       '三大同步工具的区别与场景', 'MEDIUM', '考察三个工具的等待语义、可重用性与底层实现。', 3, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 2005)
UNION ALL
SELECT 2006, 'ThreadLocal 与上下文传递', 'ThreadLocal 的实现原理与内存泄漏原因？',
       '每个 Thread 持有自己的 ThreadLocalMap，key 是 ThreadLocal 的弱引用，value 是业务对象，因此天然线程封闭。但弱引用 key 被回收后 value 仍被线程强引用，线程池中线程长期存活就会导致泄漏，必须在 finally 中调用 remove()。父线程向子线程传递需用 InheritableThreadLocal 或 TransmittableThreadLocal。',
       'ThreadLocal 原理与内存泄漏', 'MEDIUM', '考察线程封闭实现、弱引用泄漏与上下文传递。', 3, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 2006)
UNION ALL
SELECT 2007, '并发容器与死锁排查', '并发容器如何选型？死锁如何预防与排查？',
       '并发容器按场景选型：ConcurrentHashMap 做高并发 Map、CopyOnWriteArrayList 做读多写少的列表、BlockingQueue 做生产消费。死锁需满足互斥、占有且等待、不可抢占、循环等待四个条件，破坏任一即可预防；线上用 jstack 或 Arthas thread -b 定位，jstack 会直接打印 Java-level deadlock 及锁的持有关系。',
       '并发容器选型与死锁排查', 'MEDIUM', '考察容器选型、死锁四条件与排查工具链。', 3, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 2007);

-- ===== 3. 题目（28 道，category_id=3，group_id 指向知识点组） =====
INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, question_type, experience_level, is_high_frequency, status)
VALUES
  -- —— 线程池（组 2001）——
  (2001, 'ThreadPoolExecutor 有哪几个核心参数？任务提交后的执行流程是什么？', '请列出七大参数，并说明任务从提交到执行/拒绝的完整判断顺序。',
   '七个参数：corePoolSize、maximumPoolSize、keepAliveTime、unit、workQueue、threadFactory、handler。流程：1) 运行线程数 < corePoolSize 时直接创建核心线程执行；2) 达到 corePoolSize 后任务进入 workQueue 排队；3) 队列已满且线程数 < maximumPoolSize 时创建非核心线程执行新任务；4) 线程数达到 maximumPoolSize 且队列已满则触发 RejectedExecutionHandler；5) 非核心线程空闲超过 keepAliveTime 后被回收（allowCoreThreadTimeOut 可让核心线程也超时回收）。',
   'good answer 应覆盖：七个参数名称、核心→队列→最大线程→拒绝的四级判断顺序、keepAliveTime 的回收语义。', 3, 2001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (2002, '为什么开发规范不建议使用 Executors 创建线程池？', '请说明各个工厂方法的隐患，以及正确的创建方式。',
   'newFixedThreadPool 和 newSingleThreadExecutor 使用无界的 LinkedBlockingQueue（默认容量 Integer.MAX_VALUE），任务堆积会撑爆堆内存导致 OOM；newCachedThreadPool 和 newScheduledThreadPool 的 maximumPoolSize 是 Integer.MAX_VALUE，高并发下会创建大量线程导致 OOM 或过度上下文切换。正确做法是显式 new ThreadPoolExecutor(...)，指定有界队列容量、合适的拒绝策略并给线程起有意义的名字。',
   '要答出无界队列与最大线程数两种 OOM 路径，并给出手动构造线程池的结论。', 3, 2001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (2003, '线程池有哪几种拒绝策略？分别适用什么场景？', '请说明四个内置策略的行为差异与自定义方式。',
   'AbortPolicy（默认）抛出 RejectedExecutionException，适合必须感知失败的核心链路；CallerRunsPolicy 让提交任务的线程自己执行，天然形成反压、削峰，适合不希望丢任务的场景；DiscardPolicy 静默丢弃新任务，适合可容忍丢失的埋点日志；DiscardOldestPolicy 丢弃队列头部的任务再重试提交，适合时效性数据。也可实现 RejectedExecutionHandler 做降级、落盘或告警。',
   '四种策略的行为要说全，并说明 CallerRunsPolicy 的反压作用与自定义 handler。', 3, 2001, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (2004, 'CPU 密集型和 IO 密集型任务分别如何设置线程池大小？', '请给出估算公式与落地时的注意事项。',
   'CPU 密集型：线程数约为 CPU 核数 N 或 N+1，过多线程只会增加上下文切换开销。IO 密集型：线程大部分时间在阻塞，可设为 2N，或用 线程数 = N / (1 - 阻塞系数) 估算，阻塞系数通常取 0.8~0.9。落地时必须配合有界队列与监控（活跃线程数、队列堆积、拒绝次数），再按压测结果调整，不要直接照搬公式。',
   '要区分 N 与 2N、给出阻塞系数公式，并强调压测与监控而非照搬公式。', 3, 2001, 'MEDIUM', 'SCENARIO', 'SENIOR', 0, 1),

  -- —— 锁机制与 AQS（组 2002）——
  (2005, 'synchronized 可以作用在哪些位置？底层是怎么实现的？', '请说明三种用法及其对应的字节码实现。',
   '三种用法：修饰实例方法（锁是当前实例 this）、修饰静态方法（锁是当前类的 Class 对象）、修饰代码块（锁是 synchronized(obj) 里的对象）。同步代码块编译后插入 monitorenter 与 monitorexit 指令（后者有异常路径的第二个副本保证释放），同步方法则用方法表的 ACC_SYNCHRONIZED 标志。底层依赖对象头 Mark Word 指向的 Monitor（ObjectMonitor），未优化时通过操作系统互斥量实现阻塞与唤醒。',
   '要覆盖三种作用范围、monitorenter/monitorexit 与 ACC_SYNCHRONIZED、对象 Monitor。', 3, 2002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (2006, 'synchronized 的锁升级过程是怎样的？', '请按无锁到重量级锁的顺序说明状态变化与触发条件。',
   '路径为 无锁 → 偏向锁 → 轻量级锁 → 重量级锁，记录在对象头 Mark Word 中。偏向锁把线程 ID CAS 写入 Mark Word，同一线程重入只需比对不做同步；一旦有第二个线程竞争就撤销偏向，升级为轻量级锁，线程在栈帧建立 Lock Record 并 CAS 替换 Mark Word，失败者自旋重试；自旋超过阈值或竞争加剧（JDK 1.6 起的自适应自旋）则膨胀为重量级锁，未抢到锁的线程进入 ObjectMonitor 的 EntryList 阻塞并陷入内核态。锁只能单向升级，不能降级（偏向锁在 JDK 15 起已默认关闭并逐步废弃）。',
   '要答出四态顺序、Mark Word、CAS 与自旋、膨胀到阻塞，以及只能升级不能降级。', 3, 2002, 'HARD', 'SHORT_ANSWER', 'SENIOR', 1, 1),
  (2007, 'ReentrantLock 相比 synchronized 有哪些能力？什么时候该选它？', '请从可中断、超时、公平性、条件队列等方面对比。',
   'ReentrantLock 提供 synchronized 没有的能力：lockInterruptibly() 可响应中断、tryLock(timeout) 可超时放弃、构造函数 new ReentrantLock(true) 支持公平锁、可绑定多个 Condition 实现精确唤醒（如生产者只唤醒消费者）。代价是必须手动在 finally 中 unlock()，忘记释放会造成死锁。JDK 1.6 后 synchronized 性能已与其接近，一般场景优先 synchronized，只有需要可中断、超时、公平锁或多条件队列时才用 ReentrantLock。',
   '要覆盖可中断、超时、公平锁、多 Condition 四点，并给出选型建议与手动 unlock 的风险。', 3, 2002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (2008, 'AQS 的核心原理是什么？它是如何实现加锁、排队与唤醒的？', '请说明 state、等待队列与 acquire/release 的关键步骤。',
   'AQS 维护一个 volatile int state 表示同步状态，以及一个 FIFO 的 CLH 变体双向等待队列（Node 含 thread、waitStatus、prev、next）。acquire 流程：子类实现 tryAcquire 修改 state，失败则 addWaiter 把当前线程封装成 Node 入队，acquireQueued 中只有当前驱节点是 head 时才重试 tryAcquire，否则把前驱 waitStatus 置为 SIGNAL 后 LockSupport.park 阻塞。release 流程：tryRelease 改 state 成功后 unpark 后继节点。由此派生出独占模式（ReentrantLock，state 为可重入次数）与共享模式（Semaphore 的 permits、CountDownLatch 的 count）。',
   'good answer 应覆盖：volatile state、CLH 双向队列、前驱为 head 才尝试、park/unpark、独占与共享两种模式。', 3, 2002, 'HARD', 'SHORT_ANSWER', 'SENIOR', 1, 1),

  -- —— volatile 与 JMM（组 2003）——
  (2009, 'volatile 能保证什么？它能保证原子性吗？举例说明。', '请说明可见性、有序性，并论证原子性的边界。',
   'volatile 提供两层语义：一是可见性，写操作立即刷新到主内存并使其他缓存行失效，读操作从主内存读取；二是有序性，通过内存屏障禁止特定重排序（写前 StoreStore、写后 StoreLoad，读后 LoadLoad/LoadStore）。但它不保证复合操作的原子性：volatile++ 仍是无锁的读-改-写三步，多线程下会丢失更新，需要用 synchronized、AtomicInteger 或 LongAdder。',
   '要答出可见性 + 禁止重排序两层语义，并明确指出 volatile++ 非原子及替代方案。', 3, 2003, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (2010, '什么是 Java 内存模型（JMM）？为什么需要它？', '请说明它解决的问题与核心抽象。',
   'JMM 是一套屏蔽硬件与操作系统内存差异的规范，定义了线程与主内存之间的抽象关系：共享变量存放在主内存，每个线程有自己的工作内存（缓存、寄存器等副本），线程只能操作副本再写回。它围绕并发的三大特性——原子性、可见性、有序性——给出保证，并用 happens-before 规则判断一个写操作是否对另一个线程可见，从而让程序员在无数据竞争时获得确定的可见性语义。',
   '要覆盖主内存/工作内存抽象、三大特性、屏蔽硬件差异与 happens-before 的作用。', 3, 2003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (2011, '什么是 happens-before 规则？列举几条典型规则。', '请说明它的含义与常见规则。',
   'happens-before 表示前一个操作的结果对后一个操作可见，是一种偏序关系，并不等同于时间上的先后。典型规则：程序次序规则（线程内按代码顺序）、监视器锁规则（unlock 先于后续对同一锁的 lock）、volatile 变量规则（写先于后续读）、线程启动规则（start 先于线程内任何动作）、线程终止规则（线程内动作先于 join 返回）、中断规则与对象终结规则，以及传递性。',
   '要点：可见性偏序而非时间先后，并至少列出锁、volatile、start、join、传递性五条。', 3, 2003, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (2012, '双重检查锁定（DCL）单例为什么必须给 instance 加 volatile？', '请结合对象创建过程与重排序说明。',
   'instance = new Singleton() 可分解为三步：分配内存空间、初始化对象（执行构造方法）、把引用赋给 instance。JIT 可能把第 2 步和第 3 步重排序，导致引用先被赋值但对象尚未初始化；此时另一个线程在第一次判空时看到非 null 并直接返回，拿到的是一个未构造完成的残缺对象。volatile 通过内存屏障禁止 2 与 3 之间的重排序，同时保证 instance 的可见性。',
   '要答出三步分解、2 与 3 的重排序、另一线程拿到残缺对象，以及 volatile 的屏障作用。', 3, 2003, 'HARD', 'SHORT_ANSWER', 'SENIOR', 1, 1),

  -- —— CAS 与原子类（组 2004）——
  (2013, 'CAS 的原理是什么？它有哪些典型问题？', '请说明指令层实现、三个经典问题及应对。',
   'CAS 即 CompareAndSwap(V, A, B)：当且仅当 V 的值等于预期值 A 时，才把它原子地更新为 B，否则不修改并返回失败。Java 通过 Unsafe 的 compareAndSwapInt 等本地方法调用 CPU 的 cmpxchg 指令，配合缓存锁定保证原子性，失败方通常自旋重试。三个问题：ABA（值从 A 被改成 B 又改回 A，可用 AtomicStampedReference 加版本号解决）、竞争激烈时长时间自旋导致 CPU 空转、只能原子地操作一个共享变量（多个变量需封装成对象用 AtomicReference）。',
   '要覆盖 V/A/B 语义、CPU 指令、ABA + 自旋开销 + 单变量三个问题及对应解法。', 3, 2004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (2014, 'AtomicInteger 是如何实现原子自增的？', '请说明内部字段与自增的执行过程。',
   'AtomicInteger 内部用 private volatile int value 存值，配合 Unsafe 的 objectFieldOffset 拿到 value 的内存偏移。JDK 8 的 getAndIncrement 调用 Unsafe.getAndAddInt，这是一个 do-while 循环：先 volatile 读当前值 v，再 compareAndSwapInt(this, offset, v, v + 1)，失败说明有并发修改，则重新读值再试，直到 CAS 成功。因此它是无锁自旋而非加锁，低竞争下开销极小。',
   '要点：volatile value + Unsafe 偏移 + 自旋 CAS 循环。', 3, 2004, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (2015, 'LongAdder 为什么在高并发下比 AtomicLong 快？它有什么代价？', '请说明分段累加的结构与一致性语义。',
   'LongAdder 内部维护一个 volatile long base 和一个 Cell[] 数组：无竞争时直接 CAS 累加 base；一旦发生竞争，就按线程的 probe 哈希把累加分散到不同的 Cell 上，把对单个热点变量的竞争拆成多个变量的竞争，冲突概率大幅下降；Cell 用 @Contended 做缓存行填充以避免伪共享。代价是 sum() 需要遍历累加 base 与全部 Cell，只能得到最终一致的近似值而非实时精确值，且扩容与求和期间并发更新可能丢失到快照外。低竞争场景下开销与 AtomicLong 相当，追求精确统计（如全局唯一序号）仍应用 AtomicLong。',
   '要覆盖 base + Cell[] 分段、降低 CAS 冲突、伪共享填充，以及 sum() 弱一致的代价。', 3, 2004, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (2016, 'AtomicReference 与 AtomicStampedReference 有什么区别？ABA 问题怎么解决？', '请说明两者的数据结构差异与典型用法。',
   'AtomicReference 对一个对象引用做 CAS，只能判断引用是否变化，无法察觉值被改成其他对象后又改回来的 ABA 情形。AtomicStampedReference 内部把引用与一个 int 版本戳打包成 Pair，compareAndSet 必须同时满足引用与戳都匹配，每次更新递增戳，因此可以识别中间过程；若只关心是否被改过而不需要计数，可用 AtomicMarkableReference（用 boolean 标记）。典型场景：链表/栈的无锁实现中用版本戳避免 ABA 导致的错误出栈。',
   '要点：引用 + 版本戳 Pair 同时比较；AtomicMarkableReference 用布尔标记；ABA 场景举例。', 3, 2004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),

  -- —— JUC 并发工具（组 2005）——
  (2017, 'CountDownLatch、CyclicBarrier、Semaphore 有什么区别？', '请从等待语义、可重用性、底层实现三个维度对比。',
   'CountDownLatch 是一次性倒数计数器：线程 await 等待 count 被其他线程 countDown 到 0，基于 AQS 共享模式（state 即 count），不可重置。CyclicBarrier 是一组线程相互等待到齐后一起继续，计数加到 parties 后放行并自动开启下一轮，可 reset 复用，还支持 barrierAction 做汇总，基于 ReentrantLock + Condition 实现。Semaphore 是信号量限流：acquire 扣减 permits、release 归还，permits 为 0 时阻塞，基于 AQS 共享模式，可指定公平或非公平。',
   '三个维度都要答：等待事件 vs 相互等待 vs 限流；一次性 vs 可复用；AQS 共享 vs Lock+Condition。', 3, 2005, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (2018, '如何用 CountDownLatch 模拟一批线程同时发起的高并发场景？', '请说明起跑线与完成计数的两个闩锁如何配合。',
   '准备两个闩锁：new CountDownLatch(1) 作为发令枪，new CountDownLatch(n) 作为完成计数。n 个工作线程启动后先 await 起跑线，主线程 countDown 发令，所有线程几乎同时被唤醒开始工作；每个线程执行完在 finally 中调用完成闩锁的 countDown；主线程 await 等待全部结束后统计总耗时与吞吐。关键是 countDown 必须放在 finally 中，否则异常会导致主线程永久等待。',
   '要点：双闩锁设计、发令枪与完成计数分工、countDown 放 finally、主线程统计耗时。', 3, 2005, 'MEDIUM', 'SCENARIO', 'MID', 0, 1),
  (2019, 'Semaphore 如何实现限流？公平模式和非公平模式有什么差异？', '请说明 permits 的增减过程与两种模式的取舍。',
   'Semaphore 以 AQS 的 state 作为可用许可数 permits。acquire() 走共享模式：tryAcquireShared 把 state 减去需要的许可数，结果小于 0 则入队阻塞；release() 把许可加回并 unpark 后继等待节点，可一次释放多个许可。公平模式（FairSync）在 tryAcquireShared 前先调用 hasQueuedPredecessors 检查队列中是否有更早的等待者，有则排队，保证 FIFO、避免饥饿但吞吐较低；非公平模式允许新来的线程直接 CAS 抢许可，吞吐更高但可能导致等待线程长时间饥饿。',
   '要点：state 即 permits、acquire 减/release 加、hasQueuedPredecessors 决定公平性、吞吐与饥饿的取舍。', 3, 2005, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (2020, 'CyclicBarrier 和 CountDownLatch 在计数方向与可重用性上有什么不同？', '请说明两者计数方式与设计意图的差异。',
   '计数方向不同：CountDownLatch 从初始值往下减到 0 放行，CyclicBarrier 从 0 往上加到 parties（构造时指定的线程数）后放行。等待语义不同：latch 是线程等待外部事件完成，barrier 是线程组内部相互等待。可重用性不同：latch 的 count 归零后不可恢复，属于一次性；barrier 放行后自动重置开启新一代（generation），也可显式 reset，适合分阶段并行计算。',
   '要答出减到 0 vs 加到 parties、等待事件 vs 相互等待、一次性 vs 自动重置。', 3, 2005, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),

  -- —— ThreadLocal（组 2006）——
  (2021, 'ThreadLocal 的实现原理是什么？', '请说明数据存储在哪里，以及为何能线程隔离。',
   'ThreadLocal 自身并不存值，它只是 key。每个 Thread 对象持有一个 ThreadLocalMap，set 时先取当前线程，再以 this（当前 ThreadLocal 实例）为 key、业务对象为 value 存入该线程自己的 map，get 时同理只查当前线程的 map，因此不同线程互不可见，实现线程封闭。ThreadLocalMap 是定制的哈希表，用开放地址法（线性探测）解决冲突，与 HashMap 的链地址法不同，且扩容阈值为容量的 2/3。',
   '要点：值存在 Thread 的 ThreadLocalMap 里、ThreadLocal 只作 key、开放地址法解决冲突。', 3, 2006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (2022, 'ThreadLocal 为什么会产生内存泄漏？如何避免？', '请结合引用关系与线程池场景说明。',
   'ThreadLocalMap 的 Entry 继承 WeakReference，key 是对 ThreadLocal 的弱引用，value 是强引用。当外部不再持有 ThreadLocal 实例时，GC 后 key 变为 null，但 value 仍被线程的 Entry 强引用，且线程本身由线程池长期持有、不会销毁，这条 value → Entry → ThreadLocalMap → Thread 的强引用链使其无法回收，堆积即泄漏。避免方式：每次使用完在 finally 中调用 remove()；用 static final 修饰 ThreadLocal 保证 key 不被回收；不要在线程池线程里存放大对象。',
   '要答出弱引用 key + 强引用 value、线程池线程长生命周期、remove() 与 static final 两个手段。', 3, 2006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (2023, '父线程的 ThreadLocal 能传给子线程吗？线程池里为什么失效？', '请说明传递机制与线程池场景的坑。',
   '普通 ThreadLocal 不会传递。InheritableThreadLocal 在 Thread 构造时把父线程的 inheritableThreadLocals 拷贝到子线程，因此新建线程可以继承父线程上下文；但线程池的核心线程只在创建时拷贝一次，之后复用不再拷贝，导致提交的任务拿到的是旧值或拿不到值。线程池场景应使用 TransmittableThreadLocal（TTL），或在提交任务时捕获上下文并在任务执行前回放、finally 中清理；异步链路同理。',
   '要点：InheritableThreadLocal 在 Thread 构造时拷贝；线程池复用导致失效；TTL 或手动捕获回放。', 3, 2006, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (2024, 'Web 项目中 ThreadLocal 的典型用法有哪些？需要注意什么风险？', '请结合请求上下文场景说明用法与坑。',
   '典型用法是在拦截器中解析 token 后把用户身份、链路 traceId、租户 ID 等请求级上下文 set 到 ThreadLocal，业务代码直接读取，并在 finally 中 remove。风险有三：一是忘记 remove，配合 Tomcat 线程池复用会造成内存泄漏；二是线程复用导致下一个请求读到上一个请求的用户信息，造成越权数据串号；三是把上下文交给异步线程或线程池任务时会丢失，需要显式传递。',
   '要点：拦截器 set + finally remove；泄漏、串号、异步丢失三个风险都要提及。', 3, 2006, 'MEDIUM', 'SCENARIO', 'MID', 0, 1),

  -- —— 并发容器与死锁排查（组 2007）——
  (2025, 'ConcurrentHashMap 相比 Hashtable 和 Collections.synchronizedMap 好在哪里？', '请从锁粒度、迭代器语义、复合操作三方面对比。',
   'Hashtable 与 Collections.synchronizedMap 都是在方法上加 synchronized 的全局锁，同一时刻只有一个线程能访问，读也串行；ConcurrentHashMap 在 JDK 1.7 用分段锁 Segment，JDK 1.8 起改为 Node + CAS + synchronized 锁定桶头节点，锁粒度细化到单个桶，读操作靠 volatile 的 value/next 几乎无锁。此外它提供 putIfAbsent、computeIfAbsent 等原子复合操作，迭代器是弱一致的，不会抛 ConcurrentModificationException。',
   '要覆盖全局锁 vs 桶级锁、弱一致迭代不抛异常、原子复合操作三点。', 3, 2007, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (2026, 'CopyOnWriteArrayList 的原理是什么？适合什么场景？', '请说明写时复制的过程与优缺点。',
   '内部持有一个 volatile 数组 array，所有读操作无锁直接读该引用；写操作（add、set、remove）先加 ReentrantLock，复制一份长度 +1 的新数组，在新数组上修改后把 array 引用指向新数组，最后解锁。优点是读完全无锁且并发安全，迭代基于快照不会抛 ConcurrentModificationException；缺点是每次写都要复制整个数组，写多时内存与 GC 压力大，且读只能看到写完成前的快照，弱一致。适合监听器列表、黑白名单、配置等读极多写极少的场景。',
   '要点：写时复制 + volatile 引用 + ReentrantLock；读无锁但弱一致；适用读多写少。', 3, 2007, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),
  (2027, '产生死锁的四个必要条件是什么？如何预防？', '请列出条件并给出对应的破坏手段。',
   '四个必要条件：互斥（资源同一时刻只能被一个线程占有）、占有且等待（持有一把锁时又去申请另一把）、不可抢占（已获得的锁不能被强行剥夺）、循环等待（形成头尾相接的等待环）。破坏任意一个即可预防：统一全局加锁顺序破坏循环等待；一次性申请所有资源破坏占有且等待；用 tryLock(timeout) 超时主动释放破坏不可抢占；减少锁嵌套与使用无锁/并发容器降低互斥范围。',
   '四个条件要全（互斥/占有且等待/不可抢占/循环等待），并给出至少两条对应的破坏手段。', 3, 2007, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (2028, '线上如何排查死锁和 CPU 飙高的线程？', '请给出完整的定位命令与判读方法。',
   '死锁：jps 查到进程 PID 后执行 jstack -l <pid> > dump.txt，输出末尾会直接给出 Found one Java-level deadlock，列出每个线程持有的锁与正在等待的锁；也可用 Arthas 的 thread -b 直接定位阻塞其他线程的根因线程，或用 VisualVM/JConsole 检测死锁。CPU 飙高：top -Hp <pid> 找出占用最高的线程 tid，用 printf %x 转成十六进制，再在 jstack 输出中按 nid=0x... 匹配到具体线程的调用栈。建议多次采样对比，避免偶发栈造成误判。',
   '要覆盖 jstack 的 deadlock 提示、Arthas thread -b、top -H + 十六进制 tid 匹配 nid，以及多次采样。', 3, 2007, 'HARD', 'SCENARIO', 'SENIOR', 1, 1)
ON DUPLICATE KEY UPDATE
  title = VALUES(title), content = VALUES(content), reference_answer = VALUES(reference_answer),
  analysis = VALUES(analysis), category_id = VALUES(category_id), group_id = VALUES(group_id),
  difficulty = VALUES(difficulty), question_type = VALUES(question_type), experience_level = VALUES(experience_level),
  is_high_frequency = VALUES(is_high_frequency), status = VALUES(status);

-- ===== 4. 题目—标签关系（每题 1 个标签） =====
INSERT INTO question_tag_relation (question_id, tag_id)
SELECT 2001, 201 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2001 AND r.tag_id = 201)
UNION ALL SELECT 2002, 201 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2002 AND r.tag_id = 201)
UNION ALL SELECT 2003, 201 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2003 AND r.tag_id = 201)
UNION ALL SELECT 2004, 201 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2004 AND r.tag_id = 201)
UNION ALL SELECT 2005, 202 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2005 AND r.tag_id = 202)
UNION ALL SELECT 2006, 202 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2006 AND r.tag_id = 202)
UNION ALL SELECT 2007, 203 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2007 AND r.tag_id = 203)
UNION ALL SELECT 2008, 210 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2008 AND r.tag_id = 210)
UNION ALL SELECT 2009, 204 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2009 AND r.tag_id = 204)
UNION ALL SELECT 2010, 204 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2010 AND r.tag_id = 204)
UNION ALL SELECT 2011, 204 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2011 AND r.tag_id = 204)
UNION ALL SELECT 2012, 204 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2012 AND r.tag_id = 204)
UNION ALL SELECT 2013, 205 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2013 AND r.tag_id = 205)
UNION ALL SELECT 2014, 205 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2014 AND r.tag_id = 205)
UNION ALL SELECT 2015, 205 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2015 AND r.tag_id = 205)
UNION ALL SELECT 2016, 205 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2016 AND r.tag_id = 205)
UNION ALL SELECT 2017, 206 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2017 AND r.tag_id = 206)
UNION ALL SELECT 2018, 206 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2018 AND r.tag_id = 206)
UNION ALL SELECT 2019, 206 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2019 AND r.tag_id = 206)
UNION ALL SELECT 2020, 206 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2020 AND r.tag_id = 206)
UNION ALL SELECT 2021, 207 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2021 AND r.tag_id = 207)
UNION ALL SELECT 2022, 207 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2022 AND r.tag_id = 207)
UNION ALL SELECT 2023, 207 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2023 AND r.tag_id = 207)
UNION ALL SELECT 2024, 207 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2024 AND r.tag_id = 207)
UNION ALL SELECT 2025, 208 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2025 AND r.tag_id = 208)
UNION ALL SELECT 2026, 208 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2026 AND r.tag_id = 208)
UNION ALL SELECT 2027, 209 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2027 AND r.tag_id = 209)
UNION ALL SELECT 2028, 209 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 2028 AND r.tag_id = 209);
