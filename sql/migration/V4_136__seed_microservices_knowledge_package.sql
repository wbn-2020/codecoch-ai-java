-- V4_136: 知识点包 #?「Microservices」（微服务 / 分布式）。
-- 数据模型：一个 question_group = 一个知识点（main_knowledge_point = 主知识点），
--           其下多道 question 通过 group_id 归属。分类挂到 init.sql 已有「Microservices」(category_id=8)。
-- 幂等：group/tag 用 NOT EXISTS 守卫；question 用 ON DUPLICATE KEY UPDATE；
--       relation 用 SELECT qid, tid FROM DUAL WHERE NOT EXISTS(...)（扁平形式，避开 V4_129 的 bug）。

-- ===== 1. 标签（10 个，id 701–710，避免与既有标签冲突） =====
INSERT INTO question_tag (id, tag_name, status)
SELECT 701, '服务拆分', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 701 OR tag_name = '服务拆分')
UNION ALL
SELECT 702, '注册中心', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 702 OR tag_name = '注册中心')
UNION ALL
SELECT 703, '分布式事务', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 703 OR tag_name = '分布式事务')
UNION ALL
SELECT 704, 'Seata', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 704 OR tag_name = 'Seata')
UNION ALL
SELECT 705, 'API网关', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 705 OR tag_name = 'API网关')
UNION ALL
SELECT 706, '熔断限流', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 706 OR tag_name = '熔断限流')
UNION ALL
SELECT 707, 'Sentinel', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 707 OR tag_name = 'Sentinel')
UNION ALL
SELECT 708, '链路追踪', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 708 OR tag_name = '链路追踪')
UNION ALL
SELECT 709, '配置中心', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 709 OR tag_name = '配置中心')
UNION ALL
SELECT 710, '服务容错', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 710 OR tag_name = '服务容错');

-- ===== 2. 知识点组（6 个，category_id=8 微服务） =====
INSERT INTO question_group (id, group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status)
SELECT 7001, '服务拆分与边界', '单体应用如何拆分为微服务？服务边界如何划分？',
       '服务拆分应以业务能力或领域驱动设计（DDD）的限界上下文为边界，每个服务拥有独立的数据存储与独立部署能力。常见拆分维度：业务域、变更频率、团队边界、资源隔离。要避免过度拆分导致的分布式复杂度，也要避免拆分不清造成的"分布式单体"。',
       '服务拆分维度与限界上下文', 'MEDIUM', '考察拆分维度、DDD 限界上下文与分布式单体陷阱。', 8, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 7001)
UNION ALL
SELECT 7002, '服务注册与发现', '服务注册与发现的工作原理？Eureka 与 Nacos 有何区别？',
       '服务启动时向注册中心注册自身元数据（IP、端口、健康状态），消费者从注册中心拉取或订阅可用实例列表并通过负载均衡调用。Eureka 是 AP 模型、基于心跳与自我保护；Nacos 同时支持 AP（Distro）与 CP（Raft），并内置配置中心能力，生态更完整。',
       '注册发现原理与注册中心选型', 'MEDIUM', '考察注册发现流程、心跳/健康检查与 Eureka/Nacos 差异。', 8, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 7002)
UNION ALL
SELECT 7003, '分布式事务与 Seata', '分布式事务有哪些方案？Seata 的 AT 模式如何工作？',
       '常见方案：2PC（强一致但阻塞）、TCC（业务侵入、性能好）、Saga（长事务、补偿）、本地消息表/事务消息（最终一致）。Seata AT 模式在业务无侵入下，通过解析 SQL 生成 undo_log，在一阶段提交本地事务与 undo_log、二阶段异步提交或基于 undo_log 回滚，实现最终一致。',
       '分布式事务方案与 Seata AT', 'HARD', '考察 2PC/TCC/Saga 选型与 Seata AT 的 undo_log 机制。', 8, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 7003)
UNION ALL
SELECT 7004, 'API 网关', 'API 网关的作用是什么？与 Nginx 有何区别？',
       '网关统一入口，承担路由转发、认证鉴权、限流熔断、灰度发布、日志审计与协议转换。Nginx 偏传输层/边缘反向代理，性能高但业务逻辑弱；API 网关（如 Spring Cloud Gateway）是应用层、编程模型丰富、易于集成鉴权与限流等微服务治理能力。',
       '网关职责与边缘代理差异', 'MEDIUM', '考察网关统一治理职责及其与 Nginx 的定位差异。', 8, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 7004)
UNION ALL
SELECT 7005, '熔断与弹性容错', '熔断、降级、限流的区别？熔断器状态如何转换？',
       '限流控制进入系统的请求速率；熔断在服务异常比例超阈值时快速失败以隔离故障；降级是在资源不足或依赖不可用时返回兜底结果保证核心可用。熔断器通常有三态：Closed（正常）→ Open（熔断，直接拒绝）→ Half-Open（试探恢复），依赖滑动窗口统计错误率。',
       '熔断降级限流与状态机', 'MEDIUM', '考察三者的语义边界与熔断器三态转换。', 8, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 7005)
UNION ALL
SELECT 7006, '链路追踪与配置中心', '分布式链路追踪原理？配置中心解决什么问题？',
       '链路追踪通过 TraceId 串联一次请求在多个服务间的调用，每个服务内用 SpanId 记录本地操作，借助埋点（如 Sleuth）与上报（如 Zipkin）还原调用链。配置中心（Nacos/Apollo）集中管理配置并通过长连接推送实现热更新，避免分散配置与重启发布。',
       '链路追踪与配置热更新', 'MEDIUM', '考察 TraceId/SpanId 串联与配置中心热更新机制。', 8, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 7006);

-- ===== 3. 题目（25 道，category_id=8，group_id 指向知识点组） =====
INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, question_type, experience_level, is_high_frequency, status)
VALUES
  -- —— 服务拆分与边界（组 7001）——
  (7001, '单体应用拆分成微服务有哪些拆分维度？如何划分服务边界？', '请从业务能力、领域模型、团队与变更频率等角度说明拆分依据，并指出常见反模式。',
   '主要维度：1) 业务能力/子域拆分；2) DDD 限界上下文划分；3) 按团队或康威定律对齐；4) 按变更频率与资源使用隔离。边界划分遵循高内聚低耦合，服务间通过明确契约（API/事件）通信。反模式是只按技术层拆分（如 user-service 包所有用户相关）或拆得太细形成分布式单体，跨服务调用链过长。',
   '要点：业务能力/DDD/团队/变更频率四维度，契约通信，警惕分布式单体。', 8, 7001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (7002, '给定一个电商系统，请设计服务拆分方案并说明要规避的坑。', '系统包含用户、商品、订单、库存、支付、物流。请划分服务边界并指出跨服务一致性如何处理。',
   '可按子域拆为：用户服务、商品服务、订单服务、库存服务、支付服务、物流服务，每个服务独立库。订单创建需跨库存、账户一致性，采用最终一致：本地事务写订单+发送事务消息，库存/账户消费消息扣减，失败重试+补偿。规避坑：避免共享数据库、避免同步长调用链、明确服务依赖方向。',
   '要点：按子域独立库、跨服务用事务消息最终一致、避免共享库与长同步链。', 8, 7001, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (7003, '什么是 DDD 中的限界上下文？它如何指导微服务拆分？', '请解释限界上下文的含义及其与微服务边界的关系。',
   '限界上下文（Bounded Context）是领域中一段语义明确、模型自洽的边界，边界内术语与模型一致，边界间通过上下文映射（如防腐层 ACL）集成。微服务边界通常应与限界上下文对齐，使一个服务对应一个上下文，模型不被外部污染，从而降低耦合、提升自治。',
   '要点：语义边界+模型自洽，服务边界对齐上下文，跨上下文用 ACL。', 8, 7001, 'MEDIUM', 'SHORT_ANSWER', 'SENIOR', 1, 1),
  (7004, '微服务拆分后数据库应如何设计？是否每个服务都要独立数据库？', '请说明数据共享与独立库之间的取舍，以及跨库查询怎么做。',
   '推荐每个服务拥有私有数据库（Database per Service），保证自治与独立演进，禁止跨服务直连对方库。需要跨域数据时通过 API 调用或异步事件同步（CQRS 读模型），避免分布式事务与强耦合。例外：强事务一致的小范围可用同一库分表，但需谨慎。',
   '要点：Database per Service、禁止跨库直连、用 API/事件同步。', 8, 7001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— 服务注册与发现（组 7002）——
  (7005, '服务注册与发现的工作原理是什么？Eureka 与 Nacos 有什么区别？', '请描述注册、心跳、发现、负载均衡的完整流程，并对比两者。',
   '服务启动向注册中心注册元数据，定期心跳续约；消费者拉取或订阅实例列表，经客户端负载均衡（Ribbon）调用。Eureka 强调 AP，依赖心跳与自我保护应对网络分区；Nacos 支持 AP（Distro）与 CP（Raft）切换，且内置配置管理，提供临时与持久实例、权重路由等更细能力。',
   '要点：注册-心跳-发现-负载均衡流程，Eureka AP 自我保护 vs Nacos AP/CP 可切换。', 8, 7002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (7006, '注册中心为什么需要心跳与健康检查？Eureka 自我保护机制是什么？', '请说明心跳超时、剔除与自我保护触发条件。',
   '心跳用于探测实例存活，超过续约阈值（默认 90s 无心跳）注册中心剔除实例。自我保护：当短时间内大量实例心跳丢失（如网络抖动）超过阈值比例，Eureka 暂停剔除以保护可用性，宁可返回可能过期的实例也不清空注册表，避免雪崩。恢复后退出保护。',
   '要点：心跳探测存活、超时剔除、自我保护牺牲一致性保可用。', 8, 7002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (7007, '服务实例已宕机但注册中心未感知，请求仍打到死实例，如何治理？', '请结合注册延迟与健康探针给出完整方案。',
   '多层防护：1) 注册中心配置合理心跳与剔除时间；2) 实例侧提供真实健康探针（readiness/liveness），异常时摘除；3) 调用端使用熔断（如 Sentinel）对连续失败实例快速失败并配合负载均衡剔除；4) 网关层做存活检测。综合降低坏实例窗口。',
   '要点：健康探针+合理剔除+调用端熔断，缩短坏实例暴露窗口。', 8, 7002, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (7008, 'CAP 理论下，注册中心应选 CP 还是 AP？为什么？', '请结合注册中心的可用性诉求解释取舍。',
   '注册中心主要服务于"发现可用实例"，短暂数据不一致（少一两个实例）比整体不可用更可接受，因此多数选 AP 优先保证可用（Eureka、Nacos Distro）。但在强一致配置/选主场景需要 CP（如 Zookeeper、Nacos Raft）。取舍依据：发现类要 AP，协调/锁类要 CP。',
   '要点：发现类 AP 优先可用，协调类 CP，取决于场景诉求。', 8, 7002, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),

  -- —— 分布式事务与 Seata（组 7003）——
  (7009, '分布式事务有哪些解决方案？各自适用什么场景？', '请列举 2PC、TCC、Saga、事务消息并说明选型。',
   '1) 2PC：强一致、阻塞、单点，适合短事务与强一致内部系统；2) TCC：业务侵入（Try/Confirm/Cancel）、性能好，适合核心资金类；3) Saga：长事务、异步补偿，适合跨多服务的业务流程；4) 事务消息/本地消息表：最终一致、低侵入，适合可异步的数据同步。选型看一致性与性能要求。',
   '要点：2PC强一致阻塞、TCC高性能侵入、Saga长流程、消息最终一致。', 8, 7003, 'HARD', 'SHORT_ANSWER', 'SENIOR', 1, 1),
  (7010, '什么是两阶段提交（2PC）？它有什么缺陷？', '请说明 Prepare/Commit 两阶段与单点、阻塞问题。',
   '2PC 分准备阶段（协调者询问各参与者能否提交，参与者锁定资源并回复）与提交阶段（全 OK 则提交，否则回滚）。缺陷：协调者单点、参与者在 Prepare 后阻塞等待直至超时、锁定资源时间长、网络分区下可能数据不一致（协调者宕机时参与者不确定）。因此不适合高并发长事务。',
   '要点：Prepare 锁资源 + Commit 决策；单点、阻塞、长锁、脑裂不一致。', 8, 7003, 'HARD', 'SHORT_ANSWER', 'SENIOR', 1, 1),
  (7011, 'TCC 与 Saga 模式有什么区别？分别适合什么业务？', '请从一致性、补偿方式与业务侵入对比。',
   'TCC 是业务级两阶段：Try 预留资源、Confirm 确认、Cancel 释放，强调短事务、强预留、实时一致，适合资金扣减等。Saga 是一串本地事务+补偿操作，任一失败则反向执行前序补偿，异步、长流程、最终一致，适合订单履约等跨多服务的长业务。TCC 侵入大、Saga 补偿逻辑需幂等。',
   '要点：TCC 预留+实时、短事务；Saga 补偿+最终一致、长流程。', 8, 7003, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (7012, 'Seata 的 AT 模式是如何工作的？与 TCC 有何区别？', '请说明一阶段、undo_log 与二阶段提交/回滚。',
   'AT 对业务无侵入：一阶段解析 SQL 生成前置镜像与后置镜像写入 undo_log，本地事务与 undo_log 同时提交；二阶段若全局提交则异步删除 undo_log，若回滚则按 undo_log 反向补偿（乐观锁防脏写）。区别于 TCC 需要手写 Try/Confirm/Cancel，AT 自动生成补偿，但依赖本地事务与全局锁。',
   '要点：AT 自动 undo_log 补偿、无业务侵入；TCC 需手写三阶段。', 8, 7003, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (7013, '订单服务创建订单后需调用库存和账户服务，如何保证一致性？', '请设计一个分布式事务方案并说明异常如何处理。',
   '采用 Seata AT 或事务消息最终一致：方案 A（AT）在全局事务内依次调用，任一失败全局回滚，依赖 undo_log 反向补偿。方案 B（消息）订单库事务内写订单并投事务消息，库存/账户消费扣减，消费失败重试+死信补偿。需保证所有扣减接口幂等（带唯一事务ID），避免重复消费导致超扣。',
   '要点：AT 全局回滚 或 事务消息+重试；接口必须幂等防重复。', 8, 7003, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),

  -- —— API 网关（组 7004）——
  (7014, 'API 网关的作用是什么？它与 Nginx 有什么区别？', '请列举网关的核心职责并对比 Nginx 定位。',
   '网关作为微服务统一入口，负责路由、认证鉴权、限流、熔断降级、灰度、日志与协议转换。Nginx 主要做高性能反向代理与负载均衡（传输/边缘层），缺乏业务级治理；网关（Spring Cloud Gateway/APISIX）在应用层，易集成鉴权与限流等逻辑。实际常 Nginx 前置 + 网关后置分层。',
   '要点：网关统一治理（鉴权/限流/灰度），Nginx 偏边缘代理，常分层部署。', 8, 7004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (7015, 'Spring Cloud Gateway 的路由与过滤器机制是怎样的？', '请说明 Route/Predicate/Filter 与执行顺序。',
   'Gateway 由 Route（路由，含 ID、目标 URI、断言、过滤器）组成。Predicate（断言）匹配请求（路径、头、方法等）决定命中哪条路由；Filter 分 GlobalFilter 与 GatewayFilter，在请求前后做改写、鉴权、限流等。请求经断言选路后按顺序执行前置过滤器→转发→后置过滤器。',
   '要点：Route+Predicate 选路、Filter 链前后置、全局与局部过滤器。', 8, 7004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (7016, '如何在网关层做统一鉴权与限流？', '请说明 token 校验位置与限流维度。',
   '鉴权：在全局前置过滤器校验 JWT/网关发放的 token，解析身份与权限，缺失或无效直接拒绝并转发用户信息头给下游。限流：基于令牌桶/计数（如 Sentinel/Redis）按 IP、用户、API 维度限流，超限返回 429。关键是不把鉴权逻辑散落到各服务，由网关集中处理。',
   '要点：全局过滤器校验 token 并下发身份头；限流按 IP/用户/API 维度。', 8, 7004, 'MEDIUM', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (7017, '网关出现性能瓶颈、请求大量超时，如何排查与优化？', '请从线程模型、下游依赖与限流角度给出排查路径。',
   '1) 看网关线程/连接池是否耗尽（event-loop 或 Tomcat 线程阻塞）；2) 排查是否有同步阻塞调用（如网关内查库），应全异步；3) 检查下游服务是否变慢导致网关堆积，加熔断与超时；4) 调整连接池与超时参数，开启限流保护；5) 用链路追踪定位慢调用。避免网关承担重逻辑。',
   '要点：避免网关同步阻塞、下游变慢加熔断、调连接池与超时、用追踪定位。', 8, 7004, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),

  -- —— 熔断与弹性容错（组 7005）——
  (7018, '什么是熔断、降级、限流？三者的区别是什么？', '请分别定义并说明适用时机。',
   '限流：控制单位时间请求量，保护系统不被压垮（入口防御）。熔断：依赖方错误率超阈值时快速失败、切断调用，防止故障扩散（依赖防御）。降级：系统压力过大或依赖不可用时，关闭非核心功能、返回兜底，保核心可用。三者层次不同：限流挡流量、熔断隔故障、降级保核心。',
   '要点：限流挡流量、熔断隔故障、降级保核心，三者互补。', 8, 7005, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (7019, '熔断器的半开状态是什么？Hystrix/Sentinel 熔断状态如何转换？', '请说明 Closed/Open/Half-Open 三态与触发条件。',
   '三态：Closed 正常放行并统计错误率；错误率/慢调用超阈值转 Open，直接快速失败并启动冷却计时；冷却结束后转 Half-Open，放行少量试探请求，成功则回 Closed、失败则回 Open。Hystrix 用滑动窗口统计，Sentinel 支持慢调用比例、异常比例、异常数等多种策略。',
   '要点：Closed→Open（超阈）→Half-Open（冷却后试探）→Closed/Open。', 8, 7005, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (7020, 'Sentinel 的限流算法有哪些？滑动窗口如何实现？', '请说明计数器、滑动窗口、漏桶、令牌桶及 Sentinel 实现。',
   '常见算法：固定窗口计数器（简单但有临界突刺）、滑动窗口（细分区间平滑）、漏桶（恒定速率流出）、令牌桶（恒定速率放令牌，允许突发）。Sentinel 默认用滑动窗口（将时间分为多个小桶统计 qps/异常），配合 LeapArray 实现高效滚动统计，支持按 QPS 或线程数限流。',
   '要点：四种算法差异，Sentinel 用滑动窗口 LeapArray 做滚动统计。', 8, 7005, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (7021, '某服务依赖的下游接口响应变慢，如何防止雪崩？', '请设计包含超时、隔离、熔断、限流的完整防护。',
   '多层防护：1) 设置合理调用超时，避免线程长期挂着；2) 线程池/信号量隔离，限制对该依赖的并发，故障不拖垮全局；3) 开启熔断，错误率升高时快速失败；4) 对入口限流；5) 准备降级兜底返回缓存或默认值。目标是隔离故障、快速失败、保核心链路。',
   '要点：超时+隔离（舱壁）+熔断+限流+降级，五层防雪崩。', 8, 7005, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),

  -- —— 链路追踪与配置中心（组 7006）——
  (7022, '分布式链路追踪的原理是什么？TraceId 与 SpanId 如何传递？', '请说明一次跨服务调用的串联方式。',
   '每个请求分配全局 TraceId，服务内每一步操作为一个 Span（含 SpanId、父 SpanId）。通过 RPC/HTTP 透传 TraceId 与 SpanId（如放入请求头），下游服务继承并新建子 Span，形成树状调用链。最终各 Span 上报到收集器（Zipkin/Jaeger）按 TraceId 聚合展示。',
   '要点：TraceId 全局串联、Span/父Span 树状、透传头部、上报聚合。', 8, 7006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (7023, 'Sleuth + Zipkin 如何串联一次跨服务调用？', '请说明埋点、上下文传递与采样上报。',
   'Sleuth 在 Spring 应用中自动为请求生成 TraceId/SpanId，并在 RestTemplate、Feign、MQ 等组件上织入埋点，将上下文放入消息头跨进程传递。采样后通过 SpanReporter 异步发送到 Zipkin 存储（内存/ES），Zipkin UI 按 TraceId 还原时序瀑布图，可定位慢服务与错误。',
   '要点：Sleuth 自动埋点+上下文传头，Zipkin 存储聚合+瀑布图。', 8, 7006, 'MEDIUM', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (7024, '微服务配置中心（Nacos/Apollo）解决了什么问题？配置热更新如何实现？', '请说明集中配置与动态刷新的机制。',
   '解决配置分散、需重启生效、环境差异难管理的问题。配置中心集中存储配置，客户端长连接（Nacos 用长轮询/推送，Apollo 用监听）监听变更，服务端推送后客户端拉取新值并刷新到内存，配合 @RefreshScope 或事件机制使 Bean 重新绑定，实现不重启热更新。',
   '要点：集中配置+长连接推送+客户端刷新+@RefreshScope 热更新。', 8, 7006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (7025, '多个微服务共用一份配置，如何做到只改一处、全局生效且不必重启？', '请设计基于配置中心的共享配置方案。',
   '将公共配置（如数据源、中间件地址、通用开关）放到配置中心的公共 namespace/应用下，各服务引用该共享配置并监听。修改后配置中心推送变更，各客户端监听到后拉取更新并刷新内存中的配置 Bean，业务代码通过 @Value/@RefreshScope 读取，无需重启即可生效。需注意配置版本与灰度发布。',
   '要点：公共配置共享+监听推送+客户端刷新，免重启且一处改全局生效。', 8, 7006, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1)
ON DUPLICATE KEY UPDATE
  title = VALUES(title), content = VALUES(content), reference_answer = VALUES(reference_answer),
  analysis = VALUES(analysis), category_id = VALUES(category_id), group_id = VALUES(group_id),
  difficulty = VALUES(difficulty), question_type = VALUES(question_type), experience_level = VALUES(experience_level),
  is_high_frequency = VALUES(is_high_frequency), status = VALUES(status);

-- ===== 4. 题目—标签关系（每题 1 个标签，扁平 FROM DUAL WHERE NOT EXISTS 形式） =====
INSERT INTO question_tag_relation (question_id, tag_id)
SELECT 7001, 701 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7001 AND r.tag_id = 701)
UNION ALL SELECT 7002, 701 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7002 AND r.tag_id = 701)
UNION ALL SELECT 7003, 701 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7003 AND r.tag_id = 701)
UNION ALL SELECT 7004, 701 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7004 AND r.tag_id = 701)
UNION ALL SELECT 7005, 702 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7005 AND r.tag_id = 702)
UNION ALL SELECT 7006, 702 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7006 AND r.tag_id = 702)
UNION ALL SELECT 7007, 702 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7007 AND r.tag_id = 702)
UNION ALL SELECT 7008, 702 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7008 AND r.tag_id = 702)
UNION ALL SELECT 7009, 703 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7009 AND r.tag_id = 703)
UNION ALL SELECT 7010, 703 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7010 AND r.tag_id = 703)
UNION ALL SELECT 7011, 703 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7011 AND r.tag_id = 703)
UNION ALL SELECT 7012, 704 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7012 AND r.tag_id = 704)
UNION ALL SELECT 7013, 704 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7013 AND r.tag_id = 704)
UNION ALL SELECT 7014, 705 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7014 AND r.tag_id = 705)
UNION ALL SELECT 7015, 705 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7015 AND r.tag_id = 705)
UNION ALL SELECT 7016, 705 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7016 AND r.tag_id = 705)
UNION ALL SELECT 7017, 705 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7017 AND r.tag_id = 705)
UNION ALL SELECT 7018, 706 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7018 AND r.tag_id = 706)
UNION ALL SELECT 7019, 710 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7019 AND r.tag_id = 710)
UNION ALL SELECT 7020, 707 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7020 AND r.tag_id = 707)
UNION ALL SELECT 7021, 710 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7021 AND r.tag_id = 710)
UNION ALL SELECT 7022, 708 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7022 AND r.tag_id = 708)
UNION ALL SELECT 7023, 708 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7023 AND r.tag_id = 708)
UNION ALL SELECT 7024, 709 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7024 AND r.tag_id = 709)
UNION ALL SELECT 7025, 709 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 7025 AND r.tag_id = 709);
