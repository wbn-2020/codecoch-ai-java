package com.codecoachai.question.support;

/**
 * FSRS-lite 间隔复习调度器：把错题复习从固定档位（1/3/7/15 天）升级为
 * 连续记忆强度模型。纯函数、纯确定性、无 ML 依赖，参数全部集中在此处，
 * 便于单测覆盖与后续调参（产品差异化能力的算法核心）。
 *
 * <p>模型（FSRS 的确定性简化版）：
 * <ul>
 *   <li>stability S（记忆稳定性，单位=天）：可保留率的衰减特征时间。采用 FSRS 的幂律衰减约定：
 *       R(t) = exp(ln(0.9) * t / S)，即经过一个 S 时长后保留率恰为 90%。</li>
 *   <li>difficulty D（记忆难度，1-10）：用户对该题的相对难度，答错推高、连续成功缓慢回落。</li>
 *   <li>reps / lapses：累计成功复习次数与遗忘（答错）次数。</li>
 * </ul>
 *
 * <p>间隔推导：到期时间取"保留率衰减到目标值 TARGET_RETENTION"的时刻，
 * 解 R(T) = target 得 T = S * ln(target) / ln(0.9)。target=0.9 时系数恰为 1，
 * 即 interval ≈ stability（天）；若目标保留率调低（如 0.85），系数 &gt; 1，间隔相应拉长。
 * 最终 interval = clamp(round(S * factor), 1, 60)。
 *
 * <p>冷启动兼容：memory_stability 为 NULL 的存量记录视为"模型未初始化"，由
 * {@link com.codecoachai.question.util.QuestionReviewSchedule} 走旧档位 fallback；
 * 首次答错（无模型数据）初始化 S=1.0、D=5.0 → 1 天后到期，与旧行为完全一致。
 */
public final class ReviewScheduler {

    /** FSRS 旧档位序列：新模型 interval 映射回旧列时取最近档（平局取更保守的低档）。 */
    public static final int[] LEGACY_INTERVALS = {1, 3, 7, 15};

    /** 目标保留率：到期时用户仍应有该概率成功回忆（FSRS 标准实践取 0.9）。 */
    public static final double TARGET_RETENTION = 0.9;

    /** FSRS 约定：t = S 时保留率为 0.9，衰减公式 R(t)=exp(ln(0.9)*t/S) 的锚点。 */
    private static final double RETENTION_AT_STABILITY = 0.9;

    /** interval = stability * ln(target)/ln(0.9)；target=0.9 时系数为 1。 */
    public static final double INTERVAL_FACTOR =
            Math.log(TARGET_RETENTION) / Math.log(RETENTION_AT_STABILITY);

    /** 调度间隔边界（天）：不短于 1 天，不长于 60 天（覆盖并超出旧 15 天上限）。 */
    public static final int MIN_INTERVAL_DAYS = 1;
    public static final int MAX_INTERVAL_DAYS = 60;

    /** 冷启动：首次答错（无模型数据）初始化的稳定性/难度 → interval=1 天，与旧行为一致。 */
    public static final double INITIAL_STABILITY = 1.0;
    public static final double INITIAL_DIFFICULTY = 5.0;

    /** 难度取值区间（FSRS 原生为 1-10）。 */
    public static final double MIN_DIFFICULTY = 1.0;
    public static final double MAX_DIFFICULTY = 10.0;

    /**
     * 成功后的稳定性增长率：growth = GROWTH_BASE * (1 + GROWTH_INTERVAL_GAIN * ln(1+interval))
     * * difficultyFactor(D)。ln(1+interval) 体现"间隔越大、巩固增益越大"（spaced repetition 的
     * expansion 效应）；difficultyFactor 随难度线性下降，体现"难题巩固更慢"。
     * GROWTH_BASE=0.7：中性题（D=5）短间隔成功一次约 +70% 稳定性，冷启动链约 1→2→4→9→24→60 天。
     */
    public static final double GROWTH_BASE = 0.7;
    public static final double GROWTH_INTERVAL_GAIN = 0.5;
    /** difficultyFactor = clamp(INTERCEPT - SLOPE*D, MIN, MAX)：D=5 时恰为 1.0（中性）。 */
    public static final double GROWTH_DIFFICULTY_INTERCEPT = 1.6;
    public static final double GROWTH_DIFFICULTY_SLOPE = 0.12;
    public static final double GROWTH_DIFFICULTY_FACTOR_MIN = 0.4;
    public static final double GROWTH_DIFFICULTY_FACTOR_MAX = 1.2;

    /** 每次成功复习后难度向低端缓慢回归（连续做对说明该题对该用户不再那么难）。 */
    public static final double DIFFICULTY_RECOVERY_ON_SUCCESS = 0.1;

    /**
     * 遗忘（答错）衰减：S' = clamp(S * 0.3, 1.0, LAPSE_STABILITY_CAP)。下限 1.0 保证不会低于
     * 冷启动值；上限 2.0 兑现"答错回到 1 天档"的产品语义（长间隔高稳定题遗忘后也必须回到
     * 1~2 天重新巩固，0.3 衰减不足以把 S=60 拉回 1 天，故显式封顶）。
     */
    public static final double LAPSE_STABILITY_DECAY = 0.3;
    public static final double LAPSE_STABILITY_CAP = 2.0;
    /** 答错难度惩罚：D' = min(10, D + 1)。 */
    public static final double LAPSE_DIFFICULTY_PENALTY = 1.0;

    /**
     * 毕业（退出错题本）条件：成功复习次数达标且 interval 已到 60 天上限，即
     * reps >= 4（对齐旧模型走完 1/3/7/15 四档）且 interval >= MAX_INTERVAL_DAYS。
     */
    public static final int MIN_REPS_TO_COMPLETE = 4;

    private ReviewScheduler() {
    }

    /**
     * 记忆状态快照（可为"未初始化"：stability/difficulty 为 NULL）。
     *
     * @param stability  记忆稳定性（天）；null=存量记录未初始化模型
     * @param difficulty 记忆难度 1-10；null=未初始化
     * @param reps       累计成功复习次数
     * @param lapses     累计遗忘（答错）次数
     */
    public record MemoryState(Double stability, Double difficulty, int reps, int lapses) {

        /** 模型是否已初始化（两参数齐全才算）。 */
        public boolean initialized() {
            return stability != null && difficulty != null;
        }
    }

    /** 冷启动首次答错：无模型数据时初始化 S=1.0、D=5.0、lapses=1 → 1 天后到期。 */
    public static MemoryState coldStartAfterLapse() {
        return new MemoryState(INITIAL_STABILITY, INITIAL_DIFFICULTY, 0, 1);
    }

    /**
     * 复习成功（重答 score>=80 或等级 GOOD/EXCELLENT，上游已映射为 MASTERED）：
     * stability *= (1 + growth)，growth 随当前 interval 增大、随 difficulty 减小；
     * difficulty 缓慢回落；reps+1。未初始化状态防御性返回冷启动+1 次成功。
     */
    public static MemoryState onSuccess(MemoryState prev) {
        if (prev == null || !prev.initialized()) {
            return new MemoryState(INITIAL_STABILITY, INITIAL_DIFFICULTY, 1, prev == null ? 0 : prev.lapses());
        }
        int interval = intervalDays(prev);
        double growth = GROWTH_BASE
                * (1.0 + GROWTH_INTERVAL_GAIN * Math.log1p(interval))
                * difficultyGrowthFactor(prev.difficulty());
        double stability = prev.stability() * (1.0 + growth);
        double difficulty = clamp(prev.difficulty() - DIFFICULTY_RECOVERY_ON_SUCCESS,
                MIN_DIFFICULTY, MAX_DIFFICULTY);
        return new MemoryState(round2(stability), round2(difficulty), prev.reps() + 1, prev.lapses());
    }

    /**
     * 复习失败（答错/未掌握）：stability 大幅衰减（见 {@link #LAPSE_STABILITY_CAP}），
     * difficulty+1（封顶 10），lapses+1；interval 回到 1~2 天（产品语义上的"回到 1 天档"）。
     * 未初始化（存量记录队列内答错、或全新题首次答错）走冷启动，语义一致。
     */
    public static MemoryState onFailure(MemoryState prev) {
        if (prev == null || !prev.initialized()) {
            return coldStartAfterLapse();
        }
        double stability = clamp(prev.stability() * LAPSE_STABILITY_DECAY,
                INITIAL_STABILITY, LAPSE_STABILITY_CAP);
        double difficulty = Math.min(MAX_DIFFICULTY, prev.difficulty() + LAPSE_DIFFICULTY_PENALTY);
        return new MemoryState(round2(stability), round2(difficulty), prev.reps(), prev.lapses() + 1);
    }

    /**
     * 手动重新进入错题本（updateMastery NOT_MASTERED）：稳定性打回冷启动值，已估出的难度与
     * 计数（reps/lapses）作为历史保留，不额外计 lapses（人为操作不是遗忘事件）。
     */
    public static MemoryState onManualRequeue(MemoryState prev) {
        if (prev == null || !prev.initialized()) {
            return new MemoryState(INITIAL_STABILITY, INITIAL_DIFFICULTY, 0, 0);
        }
        return new MemoryState(INITIAL_STABILITY, prev.difficulty(), prev.reps(), prev.lapses());
    }

    /** 由稳定性推导调度间隔（天）：clamp(round(S * INTERVAL_FACTOR), 1, 60)；未初始化返回 1。 */
    public static int intervalDays(MemoryState state) {
        if (state == null || !state.initialized()) {
            return MIN_INTERVAL_DAYS;
        }
        long raw = Math.round(state.stability() * INTERVAL_FACTOR);
        return (int) clamp(raw, MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS);
    }

    /** 新 interval 映射到旧档位下标（0-3）：取最近档，等距取低档（保守展示，不虚高记忆强度）。 */
    public static int legacyStageIndex(int intervalDays) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < LEGACY_INTERVALS.length; i++) {
            int distance = Math.abs(intervalDays - LEGACY_INTERVALS[i]);
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** 旧档位下标对应的天数（1/3/7/15），越界按 1 处理。 */
    public static int legacyIntervalDays(int stage) {
        if (stage < 0 || stage >= LEGACY_INTERVALS.length) {
            return LEGACY_INTERVALS[0];
        }
        return LEGACY_INTERVALS[stage];
    }

    /** 毕业判定：reps 达标且 interval 触及 60 天上限（记忆已充分巩固，可退出错题本）。 */
    public static boolean isComplete(MemoryState state, int intervalDays) {
        return state != null && state.reps() >= MIN_REPS_TO_COMPLETE
                && intervalDays >= MAX_INTERVAL_DAYS;
    }

    /** 难度对增长率的影响系数：D=5 → 1.0；D 越高增长越慢（下限 0.4），越低越快（上限 1.2）。 */
    private static double difficultyGrowthFactor(double difficulty) {
        return clamp(GROWTH_DIFFICULTY_INTERCEPT - GROWTH_DIFFICULTY_SLOPE * difficulty,
                GROWTH_DIFFICULTY_FACTOR_MIN, GROWTH_DIFFICULTY_FACTOR_MAX);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 记忆参数保留两位小数：够用即可，且保证落库值与单测断言确定性一致。 */
    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
