-- V4_137: 知识点包 #?「Design Patterns」（设计模式）。
-- 数据模型：一个 question_group = 一个知识点（main_knowledge_point = 主知识点），
--           其下多道 question 通过 group_id 归属。分类挂到 init.sql 已有「Design Patterns」(category_id=9)。
-- 幂等：group/tag 用 NOT EXISTS 守卫；question 用 ON DUPLICATE KEY UPDATE；
--       relation 用 SELECT qid, tid FROM DUAL WHERE NOT EXISTS(...)（扁平形式，避开 V4_129 的 bug）。

-- ===== 1. 标签（10 个，id 801–810，避免与既有标签冲突） =====
INSERT INTO question_tag (id, tag_name, status)
SELECT 801, '创建型模式', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 801)
UNION ALL
SELECT 802, '单例模式', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 802)
UNION ALL
SELECT 803, '工厂模式', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 803)
UNION ALL
SELECT 804, '结构型模式', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 804)
UNION ALL
SELECT 805, '代理模式', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 805)
UNION ALL
SELECT 806, '装饰器模式', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 806)
UNION ALL
SELECT 807, '行为型模式', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 807)
UNION ALL
SELECT 808, '策略模式', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 808)
UNION ALL
SELECT 809, '观察者模式', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 809)
UNION ALL
SELECT 810, 'Spring模式', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 810);

-- ===== 2. 知识点组（6 个，category_id=9 设计模式） =====
INSERT INTO question_group (id, group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status)
SELECT 8001, '创建型：单例与工厂', '单例模式有哪几种写法？工厂方法/抽象工厂有什么区别？',
       '单例保证全局唯一实例：饿汉（类加载即创建）、懒汉（双重检查锁+volatile）、静态内部类（懒加载且线程安全）、枚举（防反射与反序列化）。工厂方法由子类决定实例化哪一个产品；抽象工厂提供创建一系列相关产品的接口。选型看是否需要产品族。',
       '单例安全写法与工厂族对比', 'MEDIUM', '考察单例线程安全、volatile 作用与工厂方法/抽象工厂差异。', 9, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 8001)
UNION ALL
SELECT 8002, '结构型：适配器/装饰器/代理', '代理与装饰器、适配器有何区别？',
       '适配器改变接口以兼容（convert interface）；装饰器增强原有接口能力且保持接口一致、可嵌套叠加；代理控制对原对象的访问（延迟加载、鉴权、事务）。三者都基于组合/委托，但意图不同：适配为兼容、装饰为增强、代理为控制。',
       '适配器/装饰器/代理意图区分', 'MEDIUM', '考察三种结构型模式的意图差异与组合实现。', 9, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 8002)
UNION ALL
SELECT 8003, '行为型：策略/观察者/模板', '策略、观察者、模板方法分别解决什么问题？',
       '策略封装可互换算法，消除大量条件分支；观察者定义一对多依赖，状态变化时自动通知；模板方法在父类固化算法骨架、子类重写步骤钩子。它们分别解耦算法选择、对象通知与流程步骤。',
       '策略/观察者/模板方法语义', 'MEDIUM', '考察行为型三模式解决的问题域。', 9, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 8003)
UNION ALL
SELECT 8004, 'Spring 中的设计模式', 'Spring 框架在哪些地方使用了设计模式？',
       'Spring 大量运用模式：工厂（BeanFactory/ApplicationContext）、单例（默认 Bean 作用域）、代理（AOP 的 JDK/CGLIB 动态代理）、模板方法（JdbcTemplate/RestTemplate）、策略（Resource、BeanPostProcessor 扩展）、观察者（ApplicationEvent）。理解这些有助于读懂源码与设计自定义扩展。',
       'Spring 中的典型模式应用', 'MEDIUM', '考察 Spring 各模块对设计模式的实际运用。', 9, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 8004)
UNION ALL
SELECT 8005, 'MVC 与分层架构', 'MVC 三层各自的职责？为什么要分层？',
       'Controller 接收请求与参数校验、编排调用；Service 承载业务逻辑与事务；DAO/Repository 负责数据持久化。分层实现关注点分离、职责单一、可测试与可替换。违反分层（如 Controller 直接写 SQL）会导致逻辑散落、难以测试与维护。',
       '分层职责与分层价值', 'EASY', '考察三层职责、分层收益与反模式。', 9, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 8005)
UNION ALL
SELECT 8006, '组合优于继承', '组合优于继承是什么意思？如何避免类爆炸？',
       '继承带来强耦合、脆弱基类与菱形问题；组合通过持有对象、委托行为能力，更灵活可运行时替换。需要叠加多种能力（缓存+日志+限流）时，用组合（如装饰器链）而非为每种组合写子类，避免类爆炸。原则是优先用组合表达"has-a"。',
       '组合优于继承与类爆炸治理', 'MEDIUM', '考察继承缺陷、组合优势与装饰器解决类爆炸。', 9, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 8006);

-- ===== 3. 题目（22 道，category_id=9，group_id 指向知识点组） =====
INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, question_type, experience_level, is_high_frequency, status)
VALUES
  -- —— 创建型：单例与工厂（组 8001）——
  (8001, '单例模式有哪几种实现？为什么推荐枚举或静态内部类？', '请列举饿汉、懒汉、双重检查、静态内部类、枚举并说明线程安全。',
   '饿汉：类加载即创建，天然线程安全但可能浪费；懒汉+同步方法：安全但性能差；双重检查锁：需 volatile 防止指令重排导致的半初始化；静态内部类：类加载不创建，首次调用才加载内部类，线程安全且懒加载；枚举：JVM 保证单例、天然防反射与反序列化攻击，最推荐。',
   '要点：五种写法+线程安全，枚举最安全（防反射/反序列化）。', 9, 8001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (8002, '饿汉式与懒汉式单例的区别？双重检查锁为什么要加 volatile？', '请从加载时机、线程安全与指令重排角度解释。',
   '饿汉在类加载时实例化，简单但可能提前占用资源；懒汉延迟到首次使用，节省资源但需处理并发。双重检查锁中 instance = new Singleton() 分分配内存、初始化对象、赋值引用三步，JVM 可能重排序使引用先非空但对象未初始化，另一线程读到半初始化实例；volatile 禁止该重排并保证可见性。',
   '要点：加载时机差异；volatile 防重排避免半初始化。', 9, 8001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (8003, '工厂方法模式与抽象工厂模式的区别？', '请说明产品粒度与适用场景差异。',
   '工厂方法：一个工厂只生产一种产品，由子类决定创建哪个具体产品，关注单一产品创建。抽象工厂：一个工厂生产一族相互关联的产品（如 Windows 风格按钮+文本框），关注产品族的一致性。前者适合单一产品维度的扩展，后者适合多产品、需保证搭配一致的场景。',
   '要点：工厂方法单产品、抽象工厂产品族，差异在粒度。', 9, 8001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (8004, '需要根据配置动态创建不同数据库连接，用哪种工厂模式？为什么？', '请设计类结构并说明扩展点。',
   '使用工厂方法或简单工厂：定义 Connection 产品接口与 MysqlConnection/OracleConnection 实现，工厂根据配置字符串创建对应实例。若未来连接类型会持续增加，用工厂方法（每类一个工厂子类）或注册式工厂（map 映射类型到构造器）更利于开闭原则；若产品单一可简单工厂收纳。核心是调用方不依赖具体类。',
   '要点：工厂屏蔽具体类，按配置返回实现；关注开闭与解耦。', 9, 8001, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),

  -- —— 结构型：适配器/装饰器/代理（组 8002）——
  (8005, '适配器模式与桥接模式的区别？', '请说明两者解决的问题方向。',
   '适配器让不兼容的接口协同工作（转换已有接口），常用于集成遗留或第三方系统，是事后补救；桥接把抽象与实现解耦，使两者可独立变化（如形状与颜色正交组合），是事前设计。前者关注接口转换，后者关注维度拆分。',
   '要点：适配器事后兼容转换，桥接事前维度解耦。', 9, 8002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (8006, '装饰器模式与代理模式的区别？', '请说明增强意图与控制意图的差异。',
   '装饰器：为对象动态叠加增强能力，保持相同接口，可多层嵌套（如 InputStream 套 BufferedInputStream），关注"增强功能"。代理：控制对原对象的访问（延迟加载、鉴权、事务、远程代理），关注"访问控制"，通常只包一层且客户端未必知情。前者重能力叠加，后者重访问治理。',
   '要点：装饰增强可嵌套、代理控制访问通常单层。', 9, 8002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (8007, '静态代理与动态代理（JDK/CGLIB）的区别？', '请说明实现机制与限制。',
   '静态代理手写代理类，源码在编译期确定，冗余。JDK 动态代理基于接口，在运行期用 Proxy+InvocationHandler 生成实现接口的代理类，要求目标有接口。CGLIB 通过继承目标类、运行时生成子类并重写方法（用 ASM 字节码），无需接口但目标类不能是 final。Spring AOP 优先 JDK，无接口则用 CGLIB。',
   '要点：JDK 需接口、CGLIB 继承免接口但禁 final。', 9, 8002, 'HARD', 'SHORT_ANSWER', 'SENIOR', 1, 1),
  (8008, '如何为已有第三方接口做兼容适配，使其符合我们系统的接口规范？', '请设计适配器结构。',
   '用对象适配器：定义一个符合本系统规范的目标接口，适配器类持有第三方类实例，在实现方法中调用第三方方法并做字段映射/异常转换。这样业务只依赖目标接口，第三方变更被限制在适配器内。比类适配器（继承第三方）更灵活，且符合组合优先。',
   '要点：对象适配器包装第三方、做映射、隔离变化。', 9, 8002, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),

  -- —— 行为型：策略/观察者/模板（组 8003）——
  (8009, '策略模式与状态模式的区别？', '请说明意图与状态转换的归属。',
   '策略：客户端主动选择某个算法/策略，策略之间通常无状态切换，强调可互换算法。状态：对象内部状态决定行为，状态之间可自动转换（如订单状态机），行为随状态改变而改变，强调对象自身状态驱动。策略由调用方选，状态由上下文自行流转。',
   '要点：策略外部选择算法、状态内部自动流转。', 9, 8003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (8010, '观察者模式的实现原理？JDK 与 Guava 有什么实现？', '请说明主题/观察者结构与推拉模型。',
   '观察者定义一对多依赖：Subject 维护观察者列表，状态变化时遍历通知 update。JDK 提供 Observable/Observer（已过时），现代用 java.util 的 PropertyChangeSupport 或 Guava 的 EventBus（基于注解 @Subscribe 的发布订阅）。推模型主动传数据，拉模型观察者自行获取数据。',
   '要点：主题维护列表+通知；JDK 旧 API 与 Guava EventBus。', 9, 8003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (8011, '模板方法模式的典型应用场景？', '请说明骨架与钩子的设计。',
   '模板方法在父类定义算法骨架（final 方法），把可变步骤声明为抽象方法由子类实现，并可用钩子方法（默认空实现）让子类选择性覆盖。典型场景：JdbcTemplate 的"获取连接-执行-关闭"骨架、构建流程、测试 setUp/tearDown。优势是复用流程、统一约束。',
   '要点：父类定骨架 final、子类填步骤、钩子可选覆盖。', 9, 8003, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),
  (8012, '多个支付方式（微信/支付宝/银行卡）如何避免大量 if-else？用策略模式设计。', '请设计策略接口与上下文调度。',
   '定义 PayStrategy 接口含 pay(amount)，每种支付实现对应策略；用工厂或 Spring 注入 Map<String, PayStrategy> 按类型取策略；上下文根据订单支付方式调用对应策略，消除 if-else。新增支付只需加实现类并注册，符合开闭原则。关键是所有策略实现统一接口。',
   '要点：统一策略接口+Map 注册分发，消除分支、易扩展。', 9, 8003, 'MEDIUM', 'CASE_ANALYSIS', 'MID', 0, 1),

  -- —— Spring 中的设计模式（组 8004）——
  (8013, 'Spring 中哪些地方用到了工厂模式？BeanFactory 与 ApplicationContext 的关系？', '请说明工厂体现在何处及两者差异。',
   'Spring 用工厂创建与管理 Bean：BeanFactory 是最基础的 IoC 容器工厂，懒加载、按需创建；ApplicationContext 是其子接口，扩展事件、国际化、AOP 等，启动即预实例化单例。两者都是工厂，ApplicationContext 是更丰富的工厂门面。用户很少直接使用 BeanFactory。',
   '要点：容器即工厂；BeanFactory 懒加载、ApplicationContext 功能更全且预加载。', 9, 8004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (8014, 'Spring AOP 底层使用哪种代理？JDK 动态代理与 CGLIB 如何选择？', '请说明选择规则与限制。',
   '目标类实现接口时默认用 JDK 动态代理（基于接口）；无接口时用 CGLIB（继承子类）。可通过 proxy-target-class=true 强制 CGLIB。JDK 代理只能拦截接口方法，CGLIB 可代理类方法但无法代理 final 类/方法，且构造器调用不会走代理。理解这点能避免自调用不增强的坑。',
   '要点：有接口 JDK、无接口 CGLIB；final 不可代理、自调用不增强。', 9, 8004, 'HARD', 'SHORT_ANSWER', 'SENIOR', 1, 1),
  (8015, '如何自定义一个 Spring 的 BeanPostProcessor？它在生命周期哪个阶段生效？', '请说明接口方法与典型用途。',
   '实现 BeanPostProcessor 的 postProcessBeforeInitialization/AfterInitialization，在 Bean 实例化与属性填充后、初始化前后织入逻辑（如标记注解、代理包装）。它对所有 Bean 生效，是 AOP、@Autowired 等能力的底层机制。注意它处理的是已构造的 Bean 实例，早于 @PostConstruct。',
   '要点：初始化前后织入、作用于所有 Bean、是 AOP 等基础。', 9, 8004, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),

  -- —— MVC 与分层架构（组 8005）——
  (8016, 'MVC 三层架构（Controller/Service/DAO）各自的职责？', '请说明每层关注点。',
   'Controller：接收 HTTP 请求、参数校验与响应封装，不含业务。Service：核心业务逻辑、事务边界与领域规则。DAO/Repository：数据访问与持久化，屏蔽数据库细节。三层各司其职，依赖方向自顶向下，下层不反向依赖上层。',
   '要点：控制层管请求、服务层管业务事务、DAO 管持久化。', 9, 8005, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),
  (8017, '为什么要分层？违反分层（如 Controller 直接查数据库）会有什么问题？', '请结合可测试性与耦合说明。',
   '分层实现关注点分离与单一职责，便于测试（Service 可单测）、替换与维护。若 Controller 直接写 SQL，业务规则散落、无法复用、事务难以统一、单元测试需起容器，且一处改动牵动多处，耦合度陡增，演化困难。',
   '要点：分层为可测试可替换；越层导致耦合与不可测试。', 9, 8005, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),
  (8018, '一个接口既要返回 Web 又要返回 RPC 结果，如何复用业务逻辑而不耦合？', '请设计分层与防腐。',
   '把业务核心放在 Service 层，返回领域模型/DTO，不依赖具体传输协议；Controller 与 RPC Provider 都调用同一 Service，各自做协议适配（VO/Proto 转换）。通过防腐层（ACL）隔离外部模型，保证业务逻辑与技术框架解耦，Web 与 RPC 仅差适配层。',
   '要点：核心逻辑下沉 Service、靠适配层适配多协议、ACL 隔离。', 9, 8005, 'MEDIUM', 'CASE_ANALYSIS', 'MID', 0, 1),

  -- —— 组合优于继承（组 8006）——
  (8019, '组合优于继承是什么意思？什么场景该用继承、什么该用组合？', '请说明两者的取舍。',
   '继承表达"is-a"，编译期绑定、强耦合、易形成脆弱基类与菱形问题；组合表达"has-a"，运行期可替换、低耦合、灵活。优先组合：需要复用行为而非类型关系时。仅在确实是类型层次、且子类完全复用父类契约（如模板方法的抽象基类）时用继承，并避免多层深继承。',
   '要点：is-a 才继承、has-a 用组合；组合更灵活低耦合。', 9, 8006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (8020, '装饰器模式如何体现"组合优于继承"？', '请说明用组合叠加能力而非写子类。',
   '装饰器持有一个同接口组件，通过组合而非继承来扩展功能（如 BufferedInputStream 包装 FileInputStream）。相比为"带缓冲的文件流""带加密的文件流"写一摞子类，装饰器可任意嵌套组合，能力正交拆分、按需叠加，避免类数量爆炸，正是组合优于继承的典范。',
   '要点：装饰器用组合嵌套叠加能力，避免子类爆炸。', 9, 8006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (8021, '继承带来的问题（脆弱基类、菱形继承）如何在 Java 中规避？', '请说明 Java 单继承与接口的应对。',
   'Java 单继承避免了 C++ 的菱形继承二义性；脆弱基类问题（父类改动破坏子类）通过优先组合、控制继承层次深度、用接口定义契约而非抽象基类来规避。多能力复用用接口+组合实现，必要时用默认方法（default）提供可选行为，而非深继承树。',
   '要点：单继承避菱形、用接口+组合降脆弱基类风险。', 9, 8006, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (8022, '设计一个既支持缓存又支持日志又支持限流的业务组件，如何避免类爆炸？', '请设计组合方案。',
   '用装饰器链组合：基础业务组件 + CacheDecorator + LogDecorator + RateLimitDecorator，每个装饰器实现同一业务接口并持有下一层组件，调用前后插入缓存/日志/限流逻辑，按需在运行时拼装。相比为每种组合写具体子类（2^n 爆炸），装饰器用组合以线性方式叠加横切能力。',
   '要点：装饰器链线性叠加横切能力，规避 2^n 子类爆炸。', 9, 8006, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1)
ON DUPLICATE KEY UPDATE
  title = VALUES(title), content = VALUES(content), reference_answer = VALUES(reference_answer),
  analysis = VALUES(analysis), category_id = VALUES(category_id), group_id = VALUES(group_id),
  difficulty = VALUES(difficulty), question_type = VALUES(question_type), experience_level = VALUES(experience_level),
  is_high_frequency = VALUES(is_high_frequency), status = VALUES(status);

-- ===== 4. 题目—标签关系（每题 1 个标签，扁平 FROM DUAL WHERE NOT EXISTS 形式） =====
INSERT INTO question_tag_relation (question_id, tag_id)
SELECT 8001, 802 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8001 AND r.tag_id = 802)
UNION ALL SELECT 8002, 802 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8002 AND r.tag_id = 802)
UNION ALL SELECT 8003, 803 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8003 AND r.tag_id = 803)
UNION ALL SELECT 8004, 803 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8004 AND r.tag_id = 803)
UNION ALL SELECT 8005, 804 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8005 AND r.tag_id = 804)
UNION ALL SELECT 8006, 804 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8006 AND r.tag_id = 804)
UNION ALL SELECT 8007, 805 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8007 AND r.tag_id = 805)
UNION ALL SELECT 8008, 804 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8008 AND r.tag_id = 804)
UNION ALL SELECT 8009, 807 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8009 AND r.tag_id = 807)
UNION ALL SELECT 8010, 809 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8010 AND r.tag_id = 809)
UNION ALL SELECT 8011, 807 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8011 AND r.tag_id = 807)
UNION ALL SELECT 8012, 808 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8012 AND r.tag_id = 808)
UNION ALL SELECT 8013, 810 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8013 AND r.tag_id = 810)
UNION ALL SELECT 8014, 810 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8014 AND r.tag_id = 810)
UNION ALL SELECT 8015, 810 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8015 AND r.tag_id = 810)
UNION ALL SELECT 8016, 805 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8016 AND r.tag_id = 805)
UNION ALL SELECT 8017, 805 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8017 AND r.tag_id = 805)
UNION ALL SELECT 8018, 804 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8018 AND r.tag_id = 804)
UNION ALL SELECT 8019, 801 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8019 AND r.tag_id = 801)
UNION ALL SELECT 8020, 806 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8020 AND r.tag_id = 806)
UNION ALL SELECT 8021, 801 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8021 AND r.tag_id = 801)
UNION ALL SELECT 8022, 806 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 8022 AND r.tag_id = 806);
