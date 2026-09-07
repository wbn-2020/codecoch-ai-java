-- V4_139: 知识点包 — Networking & OS (category_id = 12)。
-- 数据模型：一个 question_group = 一个知识点（main_knowledge_point = 主知识点），
--           其下多道 question 通过 group_id 归属。分类 id=12 为本迁移新建。
-- 幂等：category/group/tag 用 NOT EXISTS 守卫；question 用 ON DUPLICATE KEY UPDATE；
--       relation 用 SELECT <qid>, <tid> FROM DUAL WHERE NOT EXISTS(...) 形式。

-- ===== 0. 新分类 =====
INSERT INTO question_category (id, parent_id, category_name, sort, sort_order, status)
SELECT 12, 1, 'Networking & OS', 12, 12, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_category WHERE id = 12);

-- ===== 1. 标签（1001–1010） =====
INSERT INTO question_tag (id, tag_name, status)
SELECT 1001, 'TCP', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 1001)
UNION ALL
SELECT 1002, 'HTTP', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 1002)
UNION ALL
SELECT 1003, 'HTTPS & TLS', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 1003)
UNION ALL
SELECT 1004, 'Process & Thread', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 1004)
UNION ALL
SELECT 1005, 'Virtual Memory', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 1005)
UNION ALL
SELECT 1006, 'Paging', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 1006)
UNION ALL
SELECT 1007, 'IO Multiplexing', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 1007)
UNION ALL
SELECT 1008, 'epoll', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 1008)
UNION ALL
SELECT 1009, 'Linux Command', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 1009)
UNION ALL
SELECT 1010, 'Scheduling', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 1010);

-- ===== 2. 知识点组（6 个，category_id=12） =====
INSERT INTO question_group (id, group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status)
SELECT 10001, 'TCP/IP & Handshake', 'Walk through the TCP three-way handshake and four-way wave.',
       'Three-way handshake: SYN (client->server, seq=x), SYN-ACK (server->client, seq=y, ack=x+1), ACK (client->server, ack=y+1) establishes the connection with both sides synchronized. Four-way wave: FIN/ACK each direction because closing is independent; the side receiving FIN enters CLOSE_WAIT and sends its own FIN later, while TIME_WAIT holds 2MSL to ensure the final ACK arrives and old segments expire.',
       'TCP connection setup/teardown and reliability', 'MEDIUM', 'Handshake, wave, TIME_WAIT, reliability mechanisms.', 12, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 10001)
UNION ALL
SELECT 10002, 'HTTP/HTTPS', 'What is the difference between HTTP and HTTPS, and how does TLS work?',
       'HTTP is plaintext; HTTPS wraps HTTP in TLS/SSL, adding encryption, integrity, and authentication via certificates. TLS uses asymmetric crypto (RSA/ECDHE) only to exchange a symmetric session key, then bulk-encrypts the stream with a fast symmetric cipher (AES). HTTPS also enables HTTP/2 and protects against man-in-the-middle tampering.',
       'HTTP semantics and TLS encryption', 'MEDIUM', 'Methods, status codes, TLS handshake, HTTP versions.', 12, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 10002)
UNION ALL
SELECT 10003, 'Process & Thread', 'What is the difference between a process and a thread?',
       'A process is an independent unit of resource ownership (own address space, file descriptors); a thread is the unit of CPU scheduling that shares the process address space. Threads are cheaper to create/switch and communicate via shared memory, but need synchronization; processes are isolated and communicate via IPC (pipe, shared memory, socket, message queue).',
       'Process/thread model and IPC', 'MEDIUM', 'States, context switch, IPC, synchronization primitives.', 12, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 10003)
UNION ALL
SELECT 10004, 'Memory Management & Virtual Memory', 'Explain virtual memory and how paging works.',
       'Virtual memory gives each process a contiguous logical address space backed by physical RAM plus disk (swap). The MMU translates logical->physical via page tables in fixed-size pages; a TLB caches translations. A page fault occurs when a page is not resident, triggering load from disk. Thrashing happens when the working set exceeds RAM and the system spends more time paging than working.',
       'Virtual memory, paging, page faults', 'HARD', 'Virtual memory, paging vs segmentation, TLB, thrashing.', 12, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 10004)
UNION ALL
SELECT 10005, 'IO Models & epoll', 'Compare select/poll/epoll and the five IO models.',
       'Blocking IO waits in kernel; non-blocking polls; IO multiplexing (select/poll/epoll) lets one thread watch many fds. epoll uses an in-kernel red-black tree + ready list so it is O(1) per event vs select/poll O(n) scan, and supports edge (EPOLLET) and level trigger. Asynchronous IO (io_uring/AIO) notifies completion. Reactor is event-driven sync IO; Proactor is async IO.',
       'IO multiplexing and event-driven patterns', 'HARD', 'Blocking/non-blocking, select/poll/epoll, zero-copy, Reactor/Proactor.', 12, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 10005)
UNION ALL
SELECT 10006, 'Linux Commands & Troubleshooting', 'Which Linux commands help diagnose CPU, memory, and network issues?',
       'CPU: top/htop, mpstat, pidstat; memory: free, vmstat, pmap; IO: iostat, iotop; network: netstat/ss, lsof, ping, traceroute, tcpdump, curl. For a port conflict use ss -lntp | grep :PORT or lsof -i :PORT then kill -9 PID. These are the first line of production troubleshooting.',
       'Linux observability and troubleshooting', 'MEDIUM', 'Common commands for CPU/mem/IO/network diagnosis.', 12, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 10006);

-- ===== 3. 题目（25 道，category_id=12，group_id 10001–10006） =====
INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, question_type, experience_level, is_high_frequency, status)
VALUES
  -- —— TCP/IP & Handshake（组 10001）——
  (10001, 'Walk through the TCP three-way handshake.', 'List each packet, its flags, and the sequence/ack numbers.',
   '1) Client sends SYN with seq=x. 2) Server replies SYN-ACK with seq=y, ack=x+1 (acknowledging client''s SYN, consuming one sequence number). 3) Client sends ACK with ack=y+1. Now both sides know the other can send and receive, and the connection is established.',
   'The handshake synchronizes initial sequence numbers in both directions; each SYN consumes one sequence number, which is why the ACK numbers are x+1 / y+1.', 12, 10001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (10002, 'Why does TCP need a four-way wave to close, and what is TIME_WAIT?', 'Explain CLOSE_WAIT and TIME_WAIT states.',
   'Each direction is closed independently, so FIN/ACK are exchanged per side (four segments). After the active closer sends the final ACK it enters TIME_WAIT for 2MSL to (a) let the last ACK retransmit if lost and (b) let old duplicate segments expire. The passive side enters CLOSE_WAIT as soon as it receives FIN, until its application calls close.',
   'TIME_WAIT is intentional, not a bug. Too many TIME_WAIT sockets usually signals frequent short-lived connections; tune with reuse/recyclable options, not by disabling it blindly.', 12, 10001, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (10003, 'Compare TCP and UDP.', 'Cover connection, reliability, ordering, speed, and typical use cases.',
   'TCP is connection-oriented, reliable (seq/ack, retransmission, flow & congestion control), ordered, and slower; used for HTTP, DB, file transfer. UDP is connectionless, unreliable, unordered, low-latency, and supports broadcast/multicast; used for DNS, video/voice, games, and QUIC.',
   'The trade-off is reliability vs latency. QUIC builds reliability/congestion control on top of UDP to get both.', 12, 10001, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (10004, 'How does TCP guarantee reliable transmission?', 'Cover sequence numbers, acknowledgement, sliding window, retransmission, and congestion control.',
   'Each byte is sequenced; the receiver ACKs the next expected byte; a sliding window controls flow to avoid overwhelming the receiver; timeouts and fast retransmit recover lost segments; congestion control (slow start, congestion avoidance, fast recovery) probes and backs off network capacity. This separates reliability from ordering and throughput.',
   'Reliability is layered: checksum catches corruption, ARQ handles loss, sliding window handles flow, congestion control handles the network.', 12, 10001, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (10005, 'Why is the TIME_WAIT duration twice the MSL, and what problems can it cause?', 'Relate it to the network layer.',
   'MSL is the maximum lifetime of a packet on the network. 2MSL guarantees that any in-flight duplicate of the old connection dies before a new connection reuses the same (IP, port) pair, preventing old data from corrupting a new session. High connection churn can exhaust ephemeral ports with TIME_WAIT sockets.',
   '2MSL exists purely for safety against stale segments; connection pooling (reuse) is the usual mitigation for port exhaustion.', 12, 10001, 'MEDIUM', 'SHORT_ANSWER', 'SENIOR', 0, 1),

  -- —— HTTP/HTTPS（组 10002）——
  (10006, 'Summarize common HTTP methods and status code classes.', 'Give examples of 2xx/3xx/4xx/5xx.',
   'Methods: GET (read), POST (create), PUT (replace), PATCH (partial update), DELETE, HEAD, OPTIONS. Codes: 2xx success (200, 201), 3xx redirect (301 permanent, 302 found, 304 not modified), 4xx client error (400 bad request, 401 unauthorized, 403 forbidden, 404 not found, 429 too many requests), 5xx server error (500, 502, 503, 504).',
   'Knowing the semantics lets you map symptoms to layers: 4xx = client/request issue, 5xx = server/upstream issue.', 12, 10002, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (10007, 'Explain the TLS handshake and how HTTPS is encrypted.', 'Why use both asymmetric and symmetric crypto?',
   'Client and server negotiate a cipher suite, the server presents a certificate (proving identity via CA chain), and they do a key exchange (RSA or ECDHE) to derive a shared symmetric session key. All application data is then encrypted with a fast symmetric cipher (e.g., AES-GCM). Asymmetric crypto is only used to safely agree on the key, because it is far slower than symmetric.',
   'The hybrid design gets the security of public-key key exchange with the performance of symmetric bulk encryption. ECDHE additionally provides forward secrecy.', 12, 10002, 'MEDIUM', 'CASE_ANALYSIS', 'MID', 1, 1),
  (10008, 'What changed from HTTP/1.0 to 1.1 to 2.0?', 'Cover keep-alive, pipelining, and multiplexing.',
   'HTTP/1.0 opens a connection per request; 1.1 adds persistent connections (keep-alive), pipelining, and chunked transfer; 2.0 adds binary framing, multiplexing many streams over one connection, header compression (HPACK), and server push. This cuts latency by removing head-of-line blocking at the HTTP layer.',
   'The evolution targets latency: 1.1 fixed connection overhead, 2.0 fixed HOL blocking via multiplexing. 3.0 (QUIC) moves it onto UDP.', 12, 10002, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (10009, 'Compare GET and POST.', 'Cover semantics, idempotency, caching, and body usage.',
   'GET is for retrieval, parameters in the URL, safe and idempotent, cacheable, limited size; POST submits data in the body, not idempotent, not cached by default, no practical size limit. Semantically GET should not change state; POST is for creating/submitting. PUT/PATCH/DELETE carry the idempotent mutations.',
   'Choosing GET vs POST is about semantics and safety, not just "data in URL vs body". Idempotency drives retry behavior.', 12, 10002, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1),
  (10010, 'How does HTTPS prevent man-in-the-middle attacks?', 'Explain certificates and the role of CAs.',
   'The server proves its identity with a certificate signed by a trusted Certificate Authority; the client verifies the chain and that the domain matches. The asymmetric key exchange is bound to this verified identity, so an attacker cannot substitute keys without an invalid/unknown certificate that the browser rejects.',
   'Trust flows from the OS/browser CA store. HSTS and certificate pinning further reduce downgrade and impersonation risk.', 12, 10002, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),

  -- —— Process & Thread（组 10003）——
  (10011, 'What is the difference between a process and a thread?', 'Cover address space, switching cost, and communication.',
   'A process has its own virtual address space and resources; a thread shares the process address space and is the scheduling unit. Thread creation/context-switch is cheaper than process switch (no address-space change). Threads communicate via shared memory needing locks; processes use IPC. A crash in one process usually does not kill others, while a bad thread can corrupt the whole process.',
   'The trade-off: threads are lightweight and fast to share data but harder to isolate; processes are isolated but costlier to communicate.', 12, 10003, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (10012, 'What are the typical process states, and what does a context switch cost?', 'How is scheduling related?',
   'Typical states: new, ready, running, blocked/waiting, terminated. A context switch saves the current CPU registers, program counter, and (for process switches) page-table base, then restores the next task''s; it is pure overhead with no useful work done. Scheduling policy (CFS, round-robin, priority) decides which ready task runs next.',
   'Frequent context switches hurt throughput; the cost is register/TLB/page-table reload, worse for process than thread switches.', 12, 10003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (10013, 'What inter-process communication (IPC) mechanisms exist?', 'Compare speed, capacity, and use cases.',
   'Pipe (parent-child, unidirectional), named pipe/FIFO (unrelated processes), message queue (kernel-buffered, structured), shared memory (fastest, no kernel copy, needs sync), semaphore/mutex (synchronization), socket (cross-machine). Shared memory is fastest but requires explicit locking; sockets are most general.',
   'Pick IPC by need: shared memory for speed, sockets for distribution, pipes/queues for simple streaming.', 12, 10003, 'MEDIUM', 'CASE_ANALYSIS', 'MID', 0, 1),
  (10014, 'What are mutex, semaphore, and condition variable used for?', 'Distinguish binary vs counting and signaling.',
   'A mutex provides mutual exclusion (one owner at a time, with ownership). A semaphore is a counter for N permits (binary semaphore ~ mutex, counting for resource pools like connection limits). A condition variable lets a thread wait for a predicate and be signaled by another, always used with a mutex. They solve locking, counting, and wait/notify respectively.',
   'Mutex = exclusive lock; semaphore = counted permits; condvar = wait-for-condition signaling. Mixing them up is a common bug source.', 12, 10003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— Memory & Virtual Memory（组 10004）——
  (10015, 'What is virtual memory and why do we need it?', 'Cover isolation, overcommit, and fragmentation benefits.',
   'Virtual memory maps each process''s logical addresses to physical frames via page tables, so processes see a flat contiguous space independent of physical layout. Benefits: isolation (one process cannot touch another''s memory), ability to run programs larger than RAM using disk swap, and no external fragmentation since allocation is page-grained.',
   'Virtual memory decouples logical from physical layout, enabling isolation and overcommit at the cost of translation overhead (TLB).', 12, 10004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (10016, 'Compare paging and segmentation.', 'Cover what is fixed vs variable and how they combine.',
   'Paging divides memory into fixed-size frames/pages, eliminating external fragmentation but causing internal fragmentation; it is not visible to the programmer. Segmentation divides by logical units (code, heap, stack) of variable size, matching program structure but suffering external fragmentation. x86 uses a segmented model on top of paging.',
   'Paging = fixed physical chunks (simple, some waste); segmentation = variable logical units (intuitive, fragmenting). Modern OSes mostly use paging, with segmentation mostly historical/x86-specific.', 12, 10004, 'MEDIUM', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (10017, 'What happens during a page fault, and what is thrashing?', 'Explain the handling steps and the failure mode.',
   'On accessing a non-resident page the MMU raises a page fault; the OS checks validity, allocates a frame (or swaps the page in from disk), updates the page table, and resumes the instruction. Thrashing occurs when the working set exceeds available RAM, so the system spends most time paging in/out instead of executing, collapsing throughput.',
   'Page faults are normal for demand paging, but a storm of them (thrashing) means the working set is too large; fix by adding memory or reducing concurrency.', 12, 10004, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),
  (10018, 'What is the difference between logical and physical addresses, and what is the TLB?', 'Connect to address translation.',
   'A logical (virtual) address is what the program uses; the MMU translates it to a physical address via page tables. The TLB is a small hardware cache of recent translations, so most accesses avoid walking multi-level page tables. A TLB miss triggers a page-table walk (and possibly a page fault).',
   'Translation is the heart of virtual memory; the TLB makes it cheap. TLB shootdowns are needed when page tables change across CPUs.', 12, 10004, 'MEDIUM', 'SHORT_ANSWER', 'SENIOR', 0, 1),

  -- —— IO Models & epoll（组 10005）——
  (10019, 'Describe the five IO models.', 'Blocking, non-blocking, IO multiplexing, signal-driven, asynchronous.',
   'Blocking: the thread waits until data is ready and copied. Non-blocking: returns immediately, you poll until ready. IO multiplexing: select/poll/epoll wait on many fds. Signal-driven: SIGIO notifies when readable. Asynchronous (AIO/io_uring): the kernel does everything and notifies on completion. Only the last two are truly async; the first four block somewhere in the process.',
   'The key distinction is WHO waits and WHEN you are notified: sync models block on data-copy or poll; async notifies after the whole operation completes.', 12, 10005, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (10020, 'Compare select/poll and epoll, including edge vs level trigger.', 'Why is epoll more scalable?',
   'select/poll scan all registered fds every call (O(n)) and have fd-count limits; epoll keeps fds in a kernel red-black tree and a ready list, so dispatching events is O(1) per event (O(n) total). Level-triggered (LT) re-notifies until handled (safer, simpler); edge-triggered (ET) notifies only on state change, requiring non-blocking reads until EAGAIN for max performance.',
   'epoll scales because registration is separate from waiting and readiness is tracked in-kernel; ET + non-blocking IO yields the highest throughput but is harder to code correctly.', 12, 10005, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 1, 1),
  (10021, 'What is zero-copy and how does it work?', 'Mention mmap and sendfile.',
   'Zero-copy avoids copying data between kernel and user buffers. sendfile() moves bytes from a file to a socket directly in kernel space; mmap maps the file into the process address space so read/write touches the page cache without an extra copy. This reduces CPU and context switches for file serving (e.g., static web content).',
   'Zero-copy removes redundant copies on the hot path; it matters most for high-throughput file/network serving.', 12, 10005, 'HARD', 'SHORT_ANSWER', 'SENIOR', 0, 1),
  (10022, 'Compare the Reactor and Proactor patterns.', 'Connect them to sync vs async IO.',
   'Reactor (e.g., Netty, Node) uses synchronous, non-blocking IO with an event loop demultiplexing readiness events (read is still done by the app after being told "readable"). Proactor (e.g., IOCP, io_uring) issues async operations and is notified after completion, so the app just consumes the result. Reactor is simpler to reason about; Proactor offloads more to the kernel.',
   'Reactor reacts to "ready", Proactor reacts to "done". Modern frameworks often blur the line (io_uring gives Proactor-like completion on Linux).', 12, 10005, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),

  -- —— Linux Commands & Troubleshooting（组 10006）——
  (10023, 'Which commands diagnose CPU, memory, and IO bottlenecks?', 'Give one or two per subsystem and what to look for.',
   'CPU: top/htop (load average, %us/%sy), mpstat -P ALL, pidstat. Memory: free -h, vmstat (si/so paging), pmap. IO: iostat -x (await, %util), iotop. Watch for load > core count, high %iowait, and si/so > 0 indicating swapping.',
   'Start broad (top/vmstat/iostat) to localize the subsystem, then drill into the offending process with pidstat/pmap.', 12, 10006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (10024, 'How do you find which process holds a port and free it?', 'Use ss/lsof and kill.',
   'Find the listener with ss -lntp | grep :<port> or lsof -i :<port> to get the PID, then kill -9 <PID> (or systemctl stop the service). ss is preferred over the deprecated netstat for speed and detail. Confirm with ss -lntp again after killing.',
   'ss -lntp / lsof -i :PORT is the standard port-ownership lookup; always confirm the PID before killing in production.', 12, 10006, 'MEDIUM', 'CASE_ANALYSIS', 'MID', 0, 1),
  (10025, 'Which commands help troubleshoot network connectivity?', 'Cover reachability, path, packet capture, and HTTP checks.',
   'ping tests reachability (ICMP); traceroute/mtr shows the path and where latency/loss appears; tcpdump -i any port <p> captures packets for deep inspection; ss/netstat shows sockets and states; curl -v exercises an HTTP endpoint end-to-end. Combine them to isolate DNS, routing, or application-layer faults.',
   'Layering the tools maps to the stack: ping (L3), traceroute (path), tcpdump (packets), curl (L7).', 12, 10006, 'EASY', 'SHORT_ANSWER', 'JUNIOR', 0, 1)
ON DUPLICATE KEY UPDATE
  title = VALUES(title), content = VALUES(content), reference_answer = VALUES(reference_answer),
  analysis = VALUES(analysis), category_id = VALUES(category_id), group_id = VALUES(group_id),
  difficulty = VALUES(difficulty), question_type = VALUES(question_type), experience_level = VALUES(experience_level),
  is_high_frequency = VALUES(is_high_frequency), status = VALUES(status);

-- ===== 4. 题目—标签关系（每题 1 个标签，FROM DUAL WHERE NOT EXISTS 形式） =====
INSERT INTO question_tag_relation (question_id, tag_id)
SELECT 10001, 1001 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10001 AND r.tag_id = 1001)
UNION ALL SELECT 10002, 1001 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10002 AND r.tag_id = 1001)
UNION ALL SELECT 10003, 1001 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10003 AND r.tag_id = 1001)
UNION ALL SELECT 10004, 1001 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10004 AND r.tag_id = 1001)
UNION ALL SELECT 10005, 1001 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10005 AND r.tag_id = 1001)
UNION ALL SELECT 10006, 1002 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10006 AND r.tag_id = 1002)
UNION ALL SELECT 10007, 1003 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10007 AND r.tag_id = 1003)
UNION ALL SELECT 10008, 1002 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10008 AND r.tag_id = 1002)
UNION ALL SELECT 10009, 1002 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10009 AND r.tag_id = 1002)
UNION ALL SELECT 10010, 1003 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10010 AND r.tag_id = 1003)
UNION ALL SELECT 10011, 1004 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10011 AND r.tag_id = 1004)
UNION ALL SELECT 10012, 1010 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10012 AND r.tag_id = 1010)
UNION ALL SELECT 10013, 1004 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10013 AND r.tag_id = 1004)
UNION ALL SELECT 10014, 1004 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10014 AND r.tag_id = 1004)
UNION ALL SELECT 10015, 1005 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10015 AND r.tag_id = 1005)
UNION ALL SELECT 10016, 1006 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10016 AND r.tag_id = 1006)
UNION ALL SELECT 10017, 1006 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10017 AND r.tag_id = 1006)
UNION ALL SELECT 10018, 1005 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10018 AND r.tag_id = 1005)
UNION ALL SELECT 10019, 1007 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10019 AND r.tag_id = 1007)
UNION ALL SELECT 10020, 1008 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10020 AND r.tag_id = 1008)
UNION ALL SELECT 10021, 1007 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10021 AND r.tag_id = 1007)
UNION ALL SELECT 10022, 1007 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10022 AND r.tag_id = 1007)
UNION ALL SELECT 10023, 1009 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10023 AND r.tag_id = 1009)
UNION ALL SELECT 10024, 1009 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10024 AND r.tag_id = 1009)
UNION ALL SELECT 10025, 1009 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 10025 AND r.tag_id = 1009);
