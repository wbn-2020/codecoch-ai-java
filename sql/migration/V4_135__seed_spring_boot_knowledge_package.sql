-- V4_135: Phase 1 里程碑 — 知识点包「Spring Boot」。
-- 数据模型：一个 question_group = 一个知识点（main_knowledge_point = 主知识点），
--           其下多道 question 通过 group_id 归属。分类挂到 init.sql 已有的「Spring Boot」(category_id=5)。
-- 幂等：group/tag 用 NOT EXISTS 守卫；question 用 ON DUPLICATE KEY UPDATE；
--       关系用 SELECT qid, tid FROM DUAL WHERE NOT EXISTS(...)。
-- ID 区间：groups 6001-6006，questions 6001-6025，tags 601-610（与并发/集合/Redis 等包无交集）。

-- ===== 1. 标签（10 个，高 id 避免冲突） =====
INSERT INTO question_tag (id, tag_name, status)
SELECT 601, 'IoC and DI', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 601)
UNION ALL
SELECT 602, 'Bean Lifecycle', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 602)
UNION ALL
SELECT 603, 'Auto-configuration', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 603)
UNION ALL
SELECT 604, 'Transaction', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 604)
UNION ALL
SELECT 605, 'AOP', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 605)
UNION ALL
SELECT 606, 'Annotations', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 606)
UNION ALL
SELECT 607, 'Spring MVC', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 607)
UNION ALL
SELECT 608, 'Interceptor', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 608)
UNION ALL
SELECT 609, 'Bean Scope', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 609)
UNION ALL
SELECT 610, 'Starter', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 610);

-- ===== 2. 知识点组（6 个主知识点，category_id=5 Spring Boot） =====
INSERT INTO question_group (id, group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status)
SELECT 6001, 'IoC DI and Beans', 'What are IoC and DI, and how does Spring implement them?',
       'Inversion of Control hands object creation and wiring to the Spring container; Dependency Injection is the mechanism where dependencies are provided rather than created internally. Spring builds a bean definition registry, instantiates beans, and injects collaborators via constructor, setter, or field, resolving the graph from the container.',
       'IoC container and dependency injection', 'MEDIUM', '考察控制反转思想、DI 三种注入方式与容器装配。', 5, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 6001)
UNION ALL
SELECT 6002, 'Lifecycle and Auto-configuration', 'How does Spring Boot bootstrap and auto-configure beans?',
       'Spring Boot starts from SpringApplication.run which creates an ApplicationContext, then @EnableAutoConfiguration imports candidate auto-configurations filtered by @Conditional on classpath and existing beans, registering beans only when conditions are met. Bean lifecycle callbacks and Environment properties drive the wiring.',
       'Spring Boot startup and conditional auto-config', 'HARD', '考察启动流程、@Conditional 自动配置与条件装配。', 5, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 6002)
UNION ALL
SELECT 6003, 'Transaction Management', 'How does Spring manage declarative transactions?',
       'Spring provides @Transactional for declarative transactions via AOP proxies. A transaction advisor begins a transaction (using the platform transaction manager, typically JDBC), binds a connection to the thread via TransactionSynchronizationManager, and commits or rolls back on normal return or RuntimeException/Error. Propagation and isolation control behavior.',
       'Declarative transaction and propagation', 'HARD', '考察声明式事务、传播行为、代理与连接绑定的原理。', 5, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 6003)
UNION ALL
SELECT 6004, 'AOP', 'What are the core concepts and implementations of Spring AOP?',
       'AOP modularizes cross-cutting concerns using pointcut (where), advice (what/when), aspect (combination), and weaving. Spring AOP uses dynamic proxies: JDK proxy for interfaces and CGLIB for classes, applying advice around matched join points at runtime. Order is controlled by @Order or Ordered.',
       'AOP proxying and advice ordering', 'MEDIUM', '考察切点/通知/切面概念、JDK 与 CGLIB 代理及顺序。', 5, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 6004)
UNION ALL
SELECT 6005, 'Common Annotations', 'What do the common Spring Boot annotations do?',
       'Key annotations include @SpringBootApplication (composed of @Configuration, @EnableAutoConfiguration, @ComponentScan), @Component/@Service/@Repository/@Controller for bean registration, @RestController vs @Controller, @Autowired for injection, and @Value/@ConfigurationProperties for externalized config.',
       'Spring Boot annotation catalog', 'EASY', '考察常用注解的语义与组合关系。', 5, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 6005)
UNION ALL
SELECT 6006, 'Spring MVC and Interceptor', 'How does a request flow through Spring MVC and interceptors?',
       'DispatcherServlet receives a request and uses HandlerMapping to find a handler, HandlerAdapter to invoke the controller, then resolves a view or writes the response. Interceptors (HandlerInterceptor) run preHandle/postHandle/afterCompletion around the controller, distinct from servlet Filters which sit earlier in the filter chain.',
       'MVC dispatch flow and interceptor vs filter', 'MEDIUM', '考察 DispatcherServlet 流程与拦截器/过滤器区别。', 5, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 6006);

-- ===== 3. 题目（25 道，category_id=5，group_id 指向知识点组） =====
INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, question_type, experience_level, is_high_frequency, status)
VALUES
  -- —— IoC DI and Beans（组 6001）——
  (6001, 'What are IoC and DI, and what problems do they solve?', 'Explain the concepts and the benefit over manual wiring.',
   'Inversion of Control transfers the responsibility of creating and assembling objects from application code to the Spring container. Dependency Injection is how the container supplies dependencies. Benefits: loose coupling, easier testing via mocks, centralized configuration, and lifecycle management. Without DI, classes hard-code their collaborators and become hard to test and change.',
   'good answer explains the control shift, coupling reduction, and testability, not just definitions.', 5, 6001, 'MEDIUM', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (6002, 'What is the difference between singleton and prototype bean scope, and is singleton thread-safe?', 'Discuss scope semantics and concurrency implications.',
   'Singleton (default) creates one shared instance per container; prototype creates a new instance on each request. Singleton beans are not thread-safe by themselves: shared mutable state causes races. Stateless beans (no fields or only read-only dependencies) are safe; stateful singletons need synchronization or a different scope (request, prototype).',
   'clarify that thread-safety depends on bean state, not the scope name; singletons share one instance.', 5, 6001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (6003, 'How does Spring resolve circular dependencies, and why does constructor injection fail?', 'Explain the three-level cache and the constructor limitation.',
   'Spring resolves singleton setter/field circular dependencies using a three-level cache: singletonObjects (finished), earlySingletonObjects (early reference), and singletonFactories (object factory for early proxy). It exposes a partially-created early reference so the other bean can be injected, then completes both. Constructor injection cannot be resolved this way because an instance does not exist until the constructor returns, so it throws BeanCurrentlyInCreationException.',
   'key is the three-level cache and that constructor needs a fully built instance, so it cannot be proxied early.', 5, 6001, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (6004, 'Constructor injection vs field/setter injection: which is preferred and why?', 'Give the modern recommendation with reasons.',
   'Constructor injection is preferred: it makes dependencies explicit and mandatory, enables immutability (final fields), and is easy to unit-test without a container. Field injection is concise but hides dependencies, complicates testing, and cannot be final. Setter injection suits optional or reconfigurable dependencies.',
   'favor constructor injection for required deps; mention immutability and testability as the reasons.', 5, 6001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),

  -- —— Lifecycle and Auto-configuration（组 6002）——
  (6005, 'What are the bean lifecycle callbacks in Spring?', 'List the initialization and destruction hooks and their order.',
   'Initialization: @PostConstruct (or afterPropertiesSet from InitializingBean, or a custom init-method) runs after properties are set. Destruction: @PreDestroy (or destroyMethod / DisposableBean.destroy) runs on context close. The container calls them in a defined order: aware interfaces, then @PostConstruct/InitializingBean, then custom init, and symmetrically for destroy.',
   'cover @PostConstruct/@PreDestroy plus InitializingBean/DisposableBean and the relative order.', 5, 6002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (6006, 'What is the difference between @Bean and @Component?', 'Explain when to use each.',
   '@Component (and @Service/@Repository/@Controller) is class-level stereotype scanning that auto-detects and registers beans. @Bean is method-level, used inside a @Configuration class, to explicitly instantiate and configure third-party or conditionally built objects whose class you do not own. @Bean gives full control over construction and dependencies.',
   'the crux: @Component is auto-scanned on your class, @Bean explicitly builds objects you do not control.', 5, 6002, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),
  (6007, 'How does Spring Boot auto-configuration actually work?', 'Trace from the annotation to conditional bean registration.',
   '@SpringBootApplication includes @EnableAutoConfiguration, which imports AutoConfigurationImportSelector. It reads META-INF/spring.factories (or META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports in newer versions) to load candidate auto-config classes. Each class is gated by @ConditionalOnClass, @ConditionalOnMissingBean, @ConditionalOnProperty, etc., so it only registers beans when the classpath and context satisfy the conditions, preventing conflicts with user beans.',
   'strong answer names the import selector, the imports file, and the @Conditional gates; no auto-configuration means nothing is forced.', 5, 6002, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (6008, 'What do @Conditional and its derived annotations do?', 'List the common conditional annotations.',
   '@Conditional marks a bean/configuration to register only when a specified condition matches. Common derived annotations: @ConditionalOnClass / @ConditionalOnMissingClass (class on classpath), @ConditionalOnBean / @ConditionalOnMissingBean (bean present/absent), @ConditionalOnProperty (property value), @ConditionalOnWebApplication, and @ConditionalOnExpression (SpEL). They enable auto-config to adapt to the environment.',
   'mention the base @Conditional plus the most-used derived annotations and their triggers.', 5, 6002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (6009, 'Briefly describe the Spring Boot application startup process.', 'Outline the major steps from main to ready.',
   'SpringApplication.run builds an ApplicationContext (AnnotationConfigServletWebServerApplicationContext for web), prepares the Environment (profiles, properties), runs ApplicationContextInitializers and listeners, registers the main configuration class, refreshes the context (bean definition loading, auto-configuration, bean instantiation), starts the embedded web server, and finally calls ApplicationRunner/CommandLineRunner and publishes the started event.',
   'cover context creation, environment prep, refresh (where beans/auto-config happen), server start, and runners.', 5, 6002, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),

  -- —— Transaction Management（组 6003）——
  (6010, 'What are the Spring transaction propagation behaviors?', 'List the main propagation types and their meaning.',
   'REQUIRED (join or create, default), REQUIRES_NEW (always new, suspend outer), SUPPORTS (join if exists else none), NOT_SUPPORTED (no tx, suspend), MANDATORY (must exist or throw), NEVER (must not exist or throw), NESTED (savepoint within outer, DB-dependent). REQUIRES_NEW and NESTED differ in that the former is fully independent while the latter rolls back to a savepoint of the outer.',
   'name the seven types with emphasis on REQUIRED vs REQUIRES_NEW vs NESTED distinctions.', 5, 6003, 'HARD', 'SHORT_ANSWER', 'SENIOR', 1, 1),
  (6011, 'What transaction isolation levels does Spring support and how do they map to the database?', 'Explain the levels and dirty/non-repeatable/phantom reads.',
   'Spring isolates via ISOLATION_DEFAULT (use JDBC/DB default) plus READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE. READ_COMMITTED prevents dirty reads; REPEATABLE_READ additionally prevents non-repeatable reads; SERIALIZABLE prevents phantom reads. The actual guarantees depend on the underlying database engine (e.g. MySQL InnoDB REPEATABLE_READ by default).',
   'link the levels to the anomalies they prevent and note the DB ultimately enforces them.', 5, 6003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (6012, 'Why does @Transactional sometimes fail to roll back?', 'Enumerate the common traps that break rollback.',
   'Common causes: the method is called from within the same class (self-invocation bypasses the proxy, so no AOP applies); the exception is a checked exception (default rollback is only on RuntimeException/Error, unless rollbackFor is set); the propagation is set so a new transaction is not actually started; or the method is private/final so the proxy cannot intercept. Fixes: move to another bean, set rollbackFor, or use AspectJ weaving.',
   'good answer identifies self-invocation, checked-exception default, and proxy limitations as the main traps.', 5, 6003, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (6013, 'What do readOnly and timeout do on @Transactional?', 'Explain their practical effects.',
   'readOnly=true hints the persistence provider/driver to optimize for reads (e.g. skip dirty checking, use read-only connections) and can avoid unnecessary work, though it is a hint not a hard guarantee. timeout sets the maximum duration before the transaction times out and is rolled back, protecting against long-held locks. Both are best-effort depending on the underlying resource.',
   'note both are hints/limits enforced by the transaction manager and underlying resource, not always strict.', 5, 6003, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),
  (6014, 'How is Spring declarative transaction implemented under the hood?', 'Explain the proxy, AOP, and connection binding.',
   '@Transactional is backed by an AOP advisor. A proxy wraps the bean; when the method is called, the advisor asks the PlatformTransactionManager to begin a transaction, obtains a connection, and binds it to the current thread via TransactionSynchronizationManager so the same connection is used by the persistence layer (e.g. JDBC/ORM). On success it commits; on RuntimeException it rolls back; finally it unbinds the connection.',
   'key pieces: proxy interception, transaction manager, thread-bound connection, commit/rollback on the same connection.', 5, 6003, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),

  -- —— AOP（组 6004）——
  (6015, 'What are the core concepts of AOP: pointcut, advice, aspect, weaving?', 'Define each term clearly.',
   'Pointcut selects where advice applies (join points, e.g. method execution). Advice is the action taken (before, after, around, after-returning, after-throwing). Aspect is the module combining pointcuts and advice. Weaving is the process of applying aspects to target objects: compile-time, load-time, or runtime (Spring AOP weaves at runtime via proxies).',
   'define all four and note Spring weaves at runtime through proxies.', 5, 6004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (6016, 'When does Spring AOP use JDK dynamic proxy vs CGLIB?', 'Explain the selection rule and implications.',
   'If the target bean implements at least one interface, Spring uses a JDK dynamic proxy implementing those interfaces; if it has no interfaces (or proxyTargetClass is forced), it uses CGLIB to subclass the class. CGLIB cannot proxy final classes/methods. This is why programming to interfaces and avoiding final on advised methods matters.',
   'the rule is interface present -> JDK proxy, otherwise CGLIB; final blocks CGLIB.', 5, 6004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (6017, 'How is the execution order of multiple aspects determined?', 'Explain ordering of cross-cutting concerns.',
   'Order is controlled by @Order(value) on the aspect or by implementing Ordered. Lower order value runs earlier on the way in (before advice) and later on the way out (after advice), forming a nested chain. If no order is specified, the order is undefined. Around advice wraps the proceed() call, so ordering affects the nesting depth.',
   'emphasize @Order/Ordered with lower = earlier in, later out, and that unordered is non-deterministic.', 5, 6004, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (6018, 'Why does AOP not apply when one method in the same bean calls another advised method?', 'Explain the self-invocation pitfall.',
   'Because the advice runs only when the call passes through the proxy. An internal this.otherMethod() call bypasses the proxy and invokes the target directly, so no aspect is applied. Workarounds: inject a self-reference via ApplicationContext or @Lazy self-proxy, move the method to another bean, or use AspectJ compile/load-time weaving which does not rely on proxies.',
   'the root cause is that internal calls skip the proxy; mention self-injection or AspectJ as fixes.', 5, 6004, 'MEDIUM', 'CASE_ANALYSIS', 'MID', 0, 1),

  -- —— Common Annotations（组 6005）——
  (6019, 'What does @SpringBootApplication compose into?', 'Name the three annotations it bundles.',
   '@SpringBootApplication is a convenience annotation equal to @Configuration (marks a config class), @EnableAutoConfiguration (turns on auto-configuration), and @ComponentScan (scans the package and sub-packages for components). This is why the main class can both define beans and enable auto-config in one line.',
   'state the three composed annotations and their individual roles.', 5, 6005, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (6020, 'What is the difference between @RestController and @Controller?', 'Explain the response handling difference.',
   '@Controller returns a view name resolved by a ViewResolver (server-side rendering). @RestController is @Controller plus @ResponseBody, meaning every handler method serializes the return value (typically JSON) directly to the HTTP response body, which is the norm for REST APIs.',
   'the difference is @ResponseBody: @RestController writes the body, @Controller resolves a view.', 5, 6005, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),
  (6021, 'How do @Value, @ConfigurationProperties, and @PropertySource differ?', 'Compare injecting single values vs bound objects.',
   '@Value injects a single property (supports SpEL) into a field/parameter. @ConfigurationProperties binds a structured prefix of properties to a typed POJO, supporting relaxed binding and validation. @PropertySource registers an additional properties file into the Environment. For groups of related settings, @ConfigurationProperties is cleaner and type-safe.',
   'contrast single-value (@Value) vs bulk typed binding (@ConfigurationProperties), and @PropertySource adds sources.', 5, 6005, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (6022, 'What do @RequestMapping and its variant annotations do?', 'List the HTTP-specific shortcuts and key attributes.',
   '@RequestMapping maps a handler to a path and can specify method, params, headers, consumes, produces. Shortcuts @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping bind to specific HTTP verbs. Attributes like path, consumes (Content-Type), and produces (Accept) narrow matching for content negotiation.',
   'name the verb shortcuts and the key attributes (method/path/consumes/produces) for content negotiation.', 5, 6005, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),

  -- —— Spring MVC and Interceptor（组 6006）——
  (6023, 'Walk through the Spring MVC request processing flow.', 'Name the core components from request to response.',
   'DispatcherServlet receives the request, consults HandlerMapping to select a handler (controller + method), uses HandlerAdapter to invoke it, applies HandlerInterceptor pre/post hooks, the controller returns data or a view name, ViewResolver resolves the view (or the response is written directly for @ResponseBody), and the result is rendered. Exception resolvers and interceptors wrap the whole flow.',
   'list DispatcherServlet -> HandlerMapping -> HandlerAdapter -> Interceptor -> Controller -> ViewResolver/response, in order.', 5, 6006, 'HARD', 'SHORT_ANSWER', 'SENIOR', 1, 1),
  (6024, 'What is the difference between an Interceptor and a Filter, and their execution order?', 'Compare scope and position in the pipeline.',
   'A Filter is a servlet-spec component in the servlet container, running before the DispatcherServlet on every request (including static resources) and can wrap the request/response. An Interceptor is a Spring component applied only to handler methods, with preHandle/postHandle/afterCompletion around the controller. Order: Filter chain first, then Interceptor preHandle, controller, Interceptor postHandle/afterCompletion, then Filter completes.',
   'filters sit in the servlet container before MVC; interceptors are inside MVC around the handler; filters run first.', 5, 6006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (6025, 'How do you implement unified exception handling in Spring Boot?', 'Describe the central exception mechanism.',
   'Use @RestControllerAdvice (or @ControllerAdvice) combined with @ExceptionHandler methods to catch specific exceptions globally and return a consistent response body (e.g. a Result object). Optionally @ResponseStatus sets the HTTP code, and HandlerExceptionResolver orders resolution. This avoids scattering try/catch across controllers and centralizes error formatting.',
   'center on @RestControllerAdvice + @ExceptionHandler for centralized, consistent error responses.', 5, 6006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1)
ON DUPLICATE KEY UPDATE
  title = VALUES(title), content = VALUES(content), reference_answer = VALUES(reference_answer),
  analysis = VALUES(analysis), category_id = VALUES(category_id), group_id = VALUES(group_id),
  difficulty = VALUES(difficulty), question_type = VALUES(question_type), experience_level = VALUES(experience_level),
  is_high_frequency = VALUES(is_high_frequency), status = VALUES(status);

-- ===== 4. 题目—标签关系（每题 1 个标签，扁平 FROM DUAL WHERE NOT EXISTS 形式） =====
INSERT INTO question_tag_relation (question_id, tag_id)
SELECT 6001, 601 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6001 AND r.tag_id = 601)
UNION ALL SELECT 6002, 609 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6002 AND r.tag_id = 609)
UNION ALL SELECT 6003, 601 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6003 AND r.tag_id = 601)
UNION ALL SELECT 6004, 601 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6004 AND r.tag_id = 601)
UNION ALL SELECT 6005, 602 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6005 AND r.tag_id = 602)
UNION ALL SELECT 6006, 606 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6006 AND r.tag_id = 606)
UNION ALL SELECT 6007, 603 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6007 AND r.tag_id = 603)
UNION ALL SELECT 6008, 603 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6008 AND r.tag_id = 603)
UNION ALL SELECT 6009, 603 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6009 AND r.tag_id = 603)
UNION ALL SELECT 6010, 604 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6010 AND r.tag_id = 604)
UNION ALL SELECT 6011, 604 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6011 AND r.tag_id = 604)
UNION ALL SELECT 6012, 604 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6012 AND r.tag_id = 604)
UNION ALL SELECT 6013, 604 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6013 AND r.tag_id = 604)
UNION ALL SELECT 6014, 604 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6014 AND r.tag_id = 604)
UNION ALL SELECT 6015, 605 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6015 AND r.tag_id = 605)
UNION ALL SELECT 6016, 605 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6016 AND r.tag_id = 605)
UNION ALL SELECT 6017, 605 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6017 AND r.tag_id = 605)
UNION ALL SELECT 6018, 605 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6018 AND r.tag_id = 605)
UNION ALL SELECT 6019, 606 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6019 AND r.tag_id = 606)
UNION ALL SELECT 6020, 606 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6020 AND r.tag_id = 606)
UNION ALL SELECT 6021, 606 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6021 AND r.tag_id = 606)
UNION ALL SELECT 6022, 606 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6022 AND r.tag_id = 606)
UNION ALL SELECT 6023, 607 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6023 AND r.tag_id = 607)
UNION ALL SELECT 6024, 608 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6024 AND r.tag_id = 608)
UNION ALL SELECT 6025, 607 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 6025 AND r.tag_id = 607);
