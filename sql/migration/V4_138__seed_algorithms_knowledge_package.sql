-- V4_138: 知识点包 — Algorithms & Data Structures (category_id = 11)。
-- 数据模型：一个 question_group = 一个知识点（main_knowledge_point = 主知识点），
--           其下多道 question 通过 group_id 归属。分类 id=11 为本迁移新建。
-- 幂等：category/group/tag 用 NOT EXISTS 守卫；question 用 ON DUPLICATE KEY UPDATE；
--       relation 用 SELECT <qid>, <tid> FROM DUAL WHERE NOT EXISTS(...) 形式。

-- ===== 0. 新分类 =====
INSERT INTO question_category (id, parent_id, category_name, sort, sort_order, status)
SELECT 11, 1, 'Algorithms & Data Structures', 11, 11, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_category WHERE id = 11);

-- ===== 1. 标签（901–910） =====
INSERT INTO question_tag (id, tag_name, status)
SELECT 901, 'Two Pointers', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 901)
UNION ALL
SELECT 902, 'Linked List', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 902)
UNION ALL
SELECT 903, 'Tree & BST', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 903)
UNION ALL
SELECT 904, 'Sorting', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 904)
UNION ALL
SELECT 905, 'Binary Search', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 905)
UNION ALL
SELECT 906, 'Dynamic Programming', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 906)
UNION ALL
SELECT 907, 'Backtracking', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 907)
UNION ALL
SELECT 908, 'Hash Table', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 908)
UNION ALL
SELECT 909, 'Heap & Priority Queue', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 909)
UNION ALL
SELECT 910, 'Recursion', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag WHERE id = 910);

-- ===== 2. 知识点组（6 个，category_id=11） =====
INSERT INTO question_group (id, group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status)
SELECT 9001, 'Arrays & Two Pointers', 'How do you solve the Two Sum problem efficiently?',
       'Use a hash map to store value -> index while scanning once: for each element, check if (target - current) already exists in the map; if so return the two indices, otherwise put current value and index. This reduces time from O(n^2) to O(n) at O(n) space. The two-pointer technique on sorted arrays is the alternative for problems like container-with-most-water or 3Sum.',
       'Two pointers and hash-based lookup on arrays', 'MEDIUM', 'Arrays, two pointers, hash map, sliding window trade-offs.', 11, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 9001)
UNION ALL
SELECT 9002, 'Linked Lists', 'How do you reverse a singly linked list?',
       'Iteratively keep a prev pointer: while current != null, save next, set current.next = prev, move prev and current forward; return prev as new head. Recursively reverse the rest then link. Detecting a cycle uses Floyd''s slow/fast pointers where they meet inside the cycle, then advance a third pointer from head to find the entry. Middle node is found by fast moving two steps while slow moves one.',
       'Linked list traversal, reversal, cycle detection', 'MEDIUM', 'Reversal, cycle detection, fast/slow pointers, merge.', 11, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 9002)
UNION ALL
SELECT 9003, 'Trees & BST', 'How do you validate a Binary Search Tree?',
       'Recursively verify each node lies within an allowed (min, max) range that tightens as you descend: left subtree must be < node and > min, right subtree must be > node and < max. Inorder traversal of a BST yields ascending order, so an iterative inorder with a previous-value check is another correct approach. LCA in a BST is found by comparing both nodes against the current root.',
       'BST properties, traversal, validation, LCA', 'MEDIUM', 'Inorder, BFS/level order, BST validation, LCA.', 11, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 9003)
UNION ALL
SELECT 9004, 'Sorting & Searching', 'When would you use quicksort vs mergesort?',
       'Quicksort is in-place (O(log n) stack space), average O(n log n) but worst O(n^2) without randomization, and is not stable. Mergesort is stable, guaranteed O(n log n), but needs O(n) extra space. For mostly sorted data use insertion sort; for external/linked-data use mergesort; for arrays use randomized quicksort. Binary search requires a sorted range and runs in O(log n) by halving the search space each step.',
       'Sorting trade-offs and binary search', 'MEDIUM', 'Quicksort/mergesort, binary search boundaries.', 11, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 9004)
UNION ALL
SELECT 9005, 'Recursion, Backtracking & DP', 'How do you compute Fibonacci with memoization and DP?',
       'Naive recursion is exponential O(2^n). Top-down memoization caches subresults in a map, giving O(n). Bottom-up DP uses an array where dp[i] = dp[i-1] + dp[i-2], reducing space to O(1) by keeping only two variables. Backtracking builds candidates incrementally and undoes the choice (pruning) to enumerate permutations/subsets. 0/1 knapsack and LIS are classic DP problems.',
       'Recursion, memoization, DP, backtracking', 'HARD', 'Fibonacci, knapsack, permutations, LIS.', 11, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 9005)
UNION ALL
SELECT 9006, 'Hash & Heap', 'How do you find the top K frequent elements?',
       'Count frequencies with a hash map (O(n)), then use a min-heap of size K: push each (freq, value), evict the smallest when size exceeds K, so the heap ends with the K largest by frequency in O(n log K). A data stream''s median is maintained with two heaps (max-heap for lower half, min-heap for upper half). Hash tables offer O(1) average insert/lookup but need collision handling.',
       'Hash tables and priority queues', 'MEDIUM', 'Hash collisions, heap, top-K, median stream.', 11, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_group WHERE id = 9006);

-- ===== 3. 题目（25 道，category_id=11，group_id 9001–9006） =====
INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, question_type, experience_level, is_high_frequency, status)
VALUES
  -- —— Arrays & Two Pointers（组 9001）——
  (9001, 'Given an array of integers and a target, return indices of the two numbers that sum to target.', 'Design an O(n) solution. Explain the trade-off versus the brute-force O(n^2) approach.',
   'Use a hash map from value to index. Iterate once: for each element x, if (target - x) is already in the map return [map.get(target - x), currentIndex]; otherwise store x with its index. Time O(n), space O(n). Brute force checks every pair in O(n^2).',
   'Key insight: a hash map turns the O(n) lookup of the complement into O(1), trading space for time. Watch for duplicate values and the same-element-twice edge case.', 11, 9001, 'EASY', 'CODING', 'JUNIOR', 1, 1),
  (9002, 'Given an array of daily stock prices, what is the maximum profit from one buy and one sell?', 'You must buy before you sell. Provide the single-pass idea and its complexity.',
   'Track the minimum price seen so far and the maximum profit = max(profit, price - minPrice) while scanning left to right. Time O(n), space O(1). This is the "best time to buy and sell stock" classic.',
   'This reduces to "max of (price[i] - min of prices before i)". The two-pointer / single-pass scan is the standard optimization over O(n^2) pairs.', 11, 9001, 'EASY', 'CASE_ANALYSIS', 'JUNIOR', 1, 1),
  (9003, 'Move all zeros in an array to the end while keeping the relative order of non-zeros, in place.', 'Implement with O(n) time and O(1) extra space.',
   'Use a slow pointer j = 0; iterate i from 0..n-1, and whenever nums[i] != 0 swap nums[i] with nums[j++] (or simply assign and zero the source). This is the partition / two-pointer compaction pattern.',
   'The two-pointer "write index" pattern generalizes to partition problems (e.g., even/odd, colors). In-place swap keeps it O(1) space.', 11, 9001, 'EASY', 'CODING', 'JUNIOR', 0, 1),
  (9004, 'Given n vertical lines, find two that together with the x-axis form a container holding the most water.', 'Explain the two-pointer shrinking strategy and why it is correct.',
   'Use left=0, right=n-1. Area = min(height[l], height[r]) * (r-l). Move the shorter side inward because moving the taller side can never increase the height but always decreases width. Stop when pointers meet. Time O(n).',
   'The greedy two-pointer move is provably optimal: the limiting height only improves by moving the shorter line, so no optimal pair is skipped.', 11, 9001, 'MEDIUM', 'CODING', 'MID', 0, 1),
  (9005, 'Given an array, return all unique triplets that sum to zero.', 'How do you avoid duplicate results efficiently?',
   'Sort first. Fix the first element i, then use two pointers l=i+1, r=n-1 to find pairs summing to -nums[i]; skip duplicate i, l, and r to avoid repeated triplets. Time O(n^2), space O(1) excluding output.',
   'This is 3Sum built on the two-pointer sum pattern. Sorting enables both the pointer scan and duplicate skipping in O(1).', 11, 9001, 'MEDIUM', 'CASE_ANALYSIS', 'MID', 0, 1),

  -- —— Linked Lists（组 9002）——
  (9006, 'Reverse a singly linked list (iterative and recursive).', 'Give both implementations and their complexity.',
   'Iterative: prev=null; while cur, next=cur.next; cur.next=prev; prev=cur; cur=next; return prev. Recursive: reverse(rest), then rest.next.next=head; head.next=null. Both O(n) time, O(1) / O(n) stack space.',
   'Reversal is the backbone of many linked-list problems. The recursive version makes the "reverse the rest then relink" structure explicit.', 11, 9002, 'MEDIUM', 'CODING', 'JUNIOR', 1, 1),
  (9007, 'Detect whether a linked list has a cycle, and find the node where it begins.', 'Explain Floyd''s cycle detection and the math behind finding the entry.',
   'Use slow (1 step) and fast (2 steps) pointers; if they meet there is a cycle. To find the entry, reset one pointer to head and move both one step at a time; they meet at the cycle entry. Proof: distance from head to entry equals distance from meeting point to entry around the cycle.',
   'Floyd''s algorithm is O(n) time and O(1) space. The "reset and step together" trick is a classic application of the cycle-length math.', 11, 9002, 'MEDIUM', 'CODING', 'MID', 0, 1),
  (9008, 'Find the middle node of a linked list. If two middles exist, return the second.', 'Use the fast/slow pointer technique.',
   'Slow moves one step, fast moves two. When fast reaches the end (fast==null or fast.next==null), slow is at the middle. For the second middle, stop when fast!=null && fast.next!=null fails.',
   'Fast/slow pointers solve middle, cycle, and intersection problems with a single O(n) pass and O(1) space.', 11, 9002, 'EASY', 'CODING', 'JUNIOR', 0, 1),
  (9009, 'Merge two sorted linked lists into one sorted list.', 'Implement iteratively and recursively; discuss which is safer for long lists.',
   'Iteratively compare heads and append the smaller, advancing that list; attach the remainder at the end. Recursively return the smaller node and set its next to the merge of the rest. Iterative is preferred to avoid stack overflow on very long lists. Both O(n+m).',
   'This is the building block of merge sort on linked lists. Prefer the iterative form in production to bound stack depth.', 11, 9002, 'EASY', 'CASE_ANALYSIS', 'JUNIOR', 0, 1),

  -- —— Trees & BST（组 9003）——
  (9010, 'Implement inorder traversal of a binary tree iteratively.', 'Use an explicit stack; explain why it mirrors the recursive version.',
   'Push the left spine onto the stack, pop the top (visit it), then move to its right subtree and repeat. This reproduces the LNR order without recursion. Time O(n), space O(h).',
   'Iterative inorder is the canonical "convert recursion to stack" exercise and underpins BST validation via a previous-value check.', 11, 9003, 'MEDIUM', 'CODING', 'MID', 0, 1),
  (9011, 'Validate that a binary tree is a Binary Search Tree.', 'Define the precise invariant and implement it.',
   'Each node must be > all nodes in its left subtree and < all nodes in its right. Implement recursively passing down (low, high) bounds: check low < node.val < high, then recurse left with high=node.val and right with low=node.val. Time O(n).',
   'Using only "left.val < node.val < right.val" per node is WRONG; the global range bound is required. Inorder-ascending is an equivalent check.', 11, 9003, 'MEDIUM', 'CODING', 'MID', 1, 1),
  (9012, 'Find the lowest common ancestor of two nodes in a BST.', 'Use the BST ordering property.',
   'If both target values are < current.val, go left; if both > current.val, go right; otherwise the current node is the LCA (one target is in each subtree, or one equals the current node). Time O(h).',
   'BST ordering lets you decide direction in O(1) per level, so no ancestor-path storage is needed unlike the generic binary-tree LCA.', 11, 9003, 'MEDIUM', 'CASE_ANALYSIS', 'MID', 0, 1),
  (9013, 'Perform level-order (BFS) traversal of a binary tree.', 'Return nodes grouped by level.',
   'Use a queue. At each step, pop the current level''s nodes, record their values, and enqueue their children; track level boundaries via the queue size snapshot. Time O(n), space O(w) where w is max width.',
   'BFS with a queue-size snapshot is the standard pattern for "grouped by level" tree problems (e.g., zigzag, right-side view).', 11, 9003, 'EASY', 'CODING', 'JUNIOR', 0, 1),
  (9014, 'Compare binary trees, BSTs, and balanced trees (AVL / Red-Black).', 'When does a BST degenerate, and how do self-balancing trees help?',
   'A plain BST degrades to O(n) height if insertions are sorted. AVL trees keep height O(log n) via rotations after each insert/delete (strict balance, good for lookups). Red-Black trees relax the invariant (loose balance, fewer rotations, good for frequent writes). Both guarantee O(log n) operations.',
   'The degradation risk of a naive BST motivates balanced variants; AVL favors read-heavy, Red-Black favors write-heavy workloads.', 11, 9003, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— Sorting & Searching（组 9004）——
  (9015, 'Compare quicksort and mergesort: time, space, stability.', 'When would you pick one over the other?',
   'Quicksort: average O(n log n), worst O(n^2) without randomization, in-place O(log n) stack, NOT stable. Mergesort: guaranteed O(n log n), O(n) extra space, stable. Use quicksort for in-memory arrays, mergesort for stable sort or linked/external data, insertion sort for nearly sorted small ranges.',
   'Stability and worst-case guarantees are the deciding factors; many real libraries use hybrid (e.g., introsort) to get the best of both.', 11, 9004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (9016, 'Implement binary search on a sorted array.', 'Handle the mid calculation and loop invariants carefully.',
   'Set lo=0, hi=n-1. While lo<=hi: mid=lo+(hi-lo)/2 (avoid overflow); if arr[mid]==target return mid; if arr[mid]<target lo=mid+1 else hi=mid-1. Time O(log n).',
   'Use lo+(hi-lo)/2 to avoid overflow and keep the invariant "answer is within [lo,hi]". Off-by-one errors come from wrong loop bounds.', 11, 9004, 'EASY', 'CODING', 'JUNIOR', 1, 1),
  (9017, 'Find the first and last index of a target in a sorted array (range search).', 'Adapt binary search to locate boundaries.',
   'Write a helper that finds the leftmost index where nums[mid] >= target (lower bound) and one where nums[mid] > target then minus one (upper bound). Each is O(log n); combine for the range.',
   'This is the "lower_bound / upper_bound" pattern. Searching boundaries instead of exact equality is the key generalization of binary search.', 11, 9004, 'MEDIUM', 'CASE_ANALYSIS', 'MID', 0, 1),
  (9018, 'What are the preconditions and complexity of binary search, and how do you apply it to answer-based problems?', 'Give examples beyond arrays.',
   'Binary search needs a monotone "predicate" over a sorted or orderable domain: sorted arrays, answer ranges (e.g., minimal capacity, sqrt), or "first false/true". Each step halves the candidate space, O(log n). The domain need not be an array.',
   'Reframing a problem as "find the boundary where a predicate flips" lets binary search solve many optimization problems, not just lookups.', 11, 9004, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),

  -- —— Recursion, Backtracking & DP（组 9005）——
  (9019, 'Compute the nth Fibonacci number efficiently.', 'Show naive recursion, memoization, and bottom-up DP with space optimization.',
   'Naive recursion is O(2^n). Top-down memoization caches fib(n)=fib(n-1)+fib(n-2) in a map => O(n). Bottom-up dp[i]=dp[i-1]+dp[i-2] needs only two rolling variables, so O(n) time and O(1) space. Matrix exponentiation reaches O(log n).',
   'This is the canonical intro to overlapping subproblems and optimal substructure; rolling variables show DP space optimization.', 11, 9005, 'EASY', 'CODING', 'JUNIOR', 0, 1),
  (9020, 'Solve the 0/1 knapsack problem with dynamic programming.', 'Define the state and transition; discuss space optimization.',
   'State dp[i][w] = max value using first i items within weight w. Transition: dp[i][w] = max(dp[i-1][w], dp[i-1][w-weight[i]] + value[i]). Time O(nW). Optimize to a 1D array iterating w backwards to avoid reusing the same item.',
   'Knapsack teaches the "include/exclude" DP choice and the backward 1D roll-up that prevents using an item twice.', 11, 9005, 'HARD', 'CODING', 'SENIOR', 0, 1),
  (9021, 'Generate all permutations of distinct numbers using backtracking.', 'Show the choose-explore-unchoose structure.',
   'At each position swap in every unused candidate, recurse to the next position, then swap back (unchoose). When position reaches length, record the permutation. Time O(n * n!), space O(n) for the recursion/visited state.',
   'Backtracking = DFS with state save/restore. The swap-and-restore trick enumerates permutations without an extra visited array.', 11, 9005, 'MEDIUM', 'CODING', 'MID', 0, 1),
  (9022, 'Find the length of the longest increasing subsequence.', 'Explain the O(n^2) DP and the O(n log n) patience-sorting approach.',
   'DP: dp[i] = 1 + max(dp[j] for j<i and arr[j]<arr[i]), answer = max(dp). O(n^2). Faster: maintain a tails array where tails[k] is the smallest ending of an increasing subsequence of length k+1; binary-search each element to replace/extend. O(n log n).',
   'LIS is a classic DP; the patience-sorting variant shows how binary search can replace an inner DP loop to cut a log factor.', 11, 9005, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1),

  -- —— Hash & Heap（组 9006）——
  (9023, 'How does a hash table handle collisions, and what are its average complexities?', 'Compare chaining and open addressing.',
   'Chaining stores a linked list/structure per bucket; open addressing probes (linear/quadratic/double hashing) for the next free slot. Average insert/lookup/delete is O(1); worst case degrades to O(n) under many collisions. A good hash and load factor (e.g., 0.75) keep it near O(1).',
   'Collisions are inevitable; load factor and probing strategy determine when resizing is needed and how clustering behaves.', 11, 9006, 'MEDIUM', 'SHORT_ANSWER', 'MID', 0, 1),
  (9024, 'Given an integer array, return the k most frequent elements.', 'Use a hash map plus a heap.',
   'Count frequencies in a hash map O(n). Then use a min-heap of size k: push (freq, value), evict the smallest frequency when size exceeds k. The remaining k entries are the top-k by frequency. Time O(n log k).',
   'The bounded min-heap is the standard top-K pattern; with k small it beats full sorting O(n log n).', 11, 9006, 'MEDIUM', 'CODING', 'MID', 1, 1),
  (9025, 'Design a data structure to find the median of a stream of numbers at any time.', 'Use two heaps.',
   'Maintain a max-heap for the lower half and a min-heap for the upper half, keeping sizes equal (or max-heap one larger). Insert into the appropriate heap then rebalance so the max-heap holds the smaller half. Median is the top of the max-heap (or average of both tops). Add/query O(log n).',
   'The two-heap invariant splits the stream at the median; this pattern also powers sliding-window and percentile problems.', 11, 9006, 'HARD', 'CASE_ANALYSIS', 'SENIOR', 0, 1)
ON DUPLICATE KEY UPDATE
  title = VALUES(title), content = VALUES(content), reference_answer = VALUES(reference_answer),
  analysis = VALUES(analysis), category_id = VALUES(category_id), group_id = VALUES(group_id),
  difficulty = VALUES(difficulty), question_type = VALUES(question_type), experience_level = VALUES(experience_level),
  is_high_frequency = VALUES(is_high_frequency), status = VALUES(status);

-- ===== 4. 题目—标签关系（每题 1 个标签，FROM DUAL WHERE NOT EXISTS 形式） =====
INSERT INTO question_tag_relation (question_id, tag_id)
SELECT 9001, 908 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9001 AND r.tag_id = 908)
UNION ALL SELECT 9002, 901 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9002 AND r.tag_id = 901)
UNION ALL SELECT 9003, 901 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9003 AND r.tag_id = 901)
UNION ALL SELECT 9004, 901 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9004 AND r.tag_id = 901)
UNION ALL SELECT 9005, 907 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9005 AND r.tag_id = 907)
UNION ALL SELECT 9006, 902 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9006 AND r.tag_id = 902)
UNION ALL SELECT 9007, 902 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9007 AND r.tag_id = 902)
UNION ALL SELECT 9008, 902 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9008 AND r.tag_id = 902)
UNION ALL SELECT 9009, 902 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9009 AND r.tag_id = 902)
UNION ALL SELECT 9010, 903 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9010 AND r.tag_id = 903)
UNION ALL SELECT 9011, 903 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9011 AND r.tag_id = 903)
UNION ALL SELECT 9012, 903 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9012 AND r.tag_id = 903)
UNION ALL SELECT 9013, 903 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9013 AND r.tag_id = 903)
UNION ALL SELECT 9014, 903 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9014 AND r.tag_id = 903)
UNION ALL SELECT 9015, 904 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9015 AND r.tag_id = 904)
UNION ALL SELECT 9016, 905 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9016 AND r.tag_id = 905)
UNION ALL SELECT 9017, 905 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9017 AND r.tag_id = 905)
UNION ALL SELECT 9018, 905 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9018 AND r.tag_id = 905)
UNION ALL SELECT 9019, 910 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9019 AND r.tag_id = 910)
UNION ALL SELECT 9020, 906 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9020 AND r.tag_id = 906)
UNION ALL SELECT 9021, 907 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9021 AND r.tag_id = 907)
UNION ALL SELECT 9022, 906 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9022 AND r.tag_id = 906)
UNION ALL SELECT 9023, 908 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9023 AND r.tag_id = 908)
UNION ALL SELECT 9024, 909 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9024 AND r.tag_id = 909)
UNION ALL SELECT 9025, 909 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM question_tag_relation r WHERE r.question_id = 9025 AND r.tag_id = 909);
