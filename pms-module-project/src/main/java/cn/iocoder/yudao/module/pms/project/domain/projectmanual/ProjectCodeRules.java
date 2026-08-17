package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

/**
 * 项目编码规则（F-PM01 / ADR-0020，code_rule_version='V1' 冻结）
 * <p>
 * 根项目编码：`PJT` + 年份4位 + 流水6位零填充（如 PJT2026000001，序号空间 1~999999）；
 * 根项目 project_sequence=0、code_root_id=id（自建命名空间）；子项目序号&gt;0（PM-02 预留）；
 * BR-8：编码创建后不可变，软删除/关闭/归档不释放；序号不回收复用。
 */
public final class ProjectCodeRules {

    /** 编码前缀 */
    public static final String CODE_PREFIX = "PJT";
    /** 编码规则版本（创建时冻结） */
    public static final String CODE_RULE_VERSION = "V1";
    /** 根项目固定命名空间序号（子项目>0 属 PM-02） */
    public static final int ROOT_PROJECT_SEQUENCE = 0;
    /** 流水上限（6 位序号空间） */
    public static final long MAX_SEQUENCE = 999_999L;

    private ProjectCodeRules() {
    }

    /**
     * 生成根项目编码：PJT + 年份4位 + 流水6位零填充（如 PJT2026000001）。
     *
     * @throws IllegalArgumentException 流水超出 [1, 999999] 或年份非 4 位
     */
    public static String buildRootCode(int year, long sequence) {
        if (year < 1000 || year > 9999) {
            throw new IllegalArgumentException("年份必须为4位：" + year);
        }
        requireSequenceAvailable(sequence);
        return CODE_PREFIX + String.format("%04d", year) + String.format("%06d", sequence);
    }

    /**
     * 流水是否已耗尽（超出 6 位可表达范围或非法）。
     */
    public static boolean isSequenceExhausted(long nextValue) {
        return nextValue < 1 || nextValue > MAX_SEQUENCE;
    }

    /**
     * 根项目序号语义：sequence=0 自建命名空间；>0 为子项目（PM-02）。
     */
    public static boolean isRootSequence(int projectSequence) {
        return projectSequence == ROOT_PROJECT_SEQUENCE;
    }

    /**
     * BR-8：项目编码创建后不可变；软删除/关闭/归档均不释放
     * （与状态、删除标记无关，任何情况下编码不回收）。
     */
    public static boolean isCodeReleasable(String status, boolean deleted) {
        return false;
    }

    /**
     * BR-8：编码命名空间内序号不回收复用（子项目流水永久递增）。
     */
    public static boolean isSequenceRecyclable() {
        return false;
    }

    private static void requireSequenceAvailable(long sequence) {
        if (isSequenceExhausted(sequence)) {
            throw new IllegalArgumentException("编码流水必须在 1~" + MAX_SEQUENCE + " 范围内：" + sequence);
        }
    }
}
