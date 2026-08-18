package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 项目树与进度汇总领域规则（F-PM02 / PM-02）
 * <p>
 * 纯函数规则，覆盖 BR-1（无环）、BR-3（权重归一化+进度汇总）、BR-4（结构/业务层级分离）
 * 的树真值维护口径。树缓存（root_id/tree_path/tree_depth）由 Service 层同事务读写本规则
 * 计算值；根项目 tree_path=''（无祖先）、tree_depth=0；子项目 tree_path=父path+父id+'/'，
 * 与 F-PM01 已落库的根项目 tree_path='' 语义一致。
 */
public final class ProjectTreeRules {

    /** 根项目祖先路径（无祖先，空串） */
    public static final String ROOT_PATH = "";
    /** 权重来源：默认等权 */
    public static final String WEIGHT_SOURCE_DEFAULT_EQUAL = "DEFAULT_EQUAL";
    /** 权重来源：人工设置 */
    public static final String WEIGHT_SOURCE_MANUAL = "MANUAL";
    /** 权重百分比 100 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** 归一化权重保留精度（0-1 小数） */
    private static final int WEIGHT_SCALE = 6;

    private ProjectTreeRules() {
    }

    /**
     * 构建子项目祖先路径：父 path + 父 id + '/'（根父 path 空则从空起）。
     */
    public static String buildChildPath(String parentPath, Long parentId) {
        String parent = parentPath == null ? ROOT_PATH : parentPath;
        return parent + parentId + "/";
    }

    /**
     * 子项目结构层级深度 = 父深度 + 1（根=0）。
     */
    public static int buildChildDepth(int parentDepth) {
        return parentDepth + 1;
    }

    /**
     * 解析祖先路径（"1/2/3/"）为祖先项目 ID 列表（[1,2,3]，根→父顺序）；空路径返回空列表。
     */
    public static List<Long> parseAncestorIds(String treePath) {
        if (treePath == null || treePath.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : treePath.split("/")) {
            if (part.isBlank()) {
                continue;
            }
            ids.add(Long.parseLong(part));
        }
        return ids;
    }

    /**
     * 后代前缀：node 的 tree_path + node.id + '/'，用于全部后代 LIKE 前缀匹配（不含自身）。
     */
    public static String descendantPrefix(String nodePath, Long nodeId) {
        String path = nodePath == null ? ROOT_PATH : nodePath;
        return path + nodeId + "/";
    }

    /**
     * 无环校验（BR-1）：把 nodeId 挂到 newParentId 下是否会形成循环。
     * <p>
     * 自身挂接、或 newParentId 的祖先路径含 nodeId（即 newParent 是 node 的后代）均视为循环。
     */
    public static boolean wouldCreateCycle(Long nodeId, String newParentPath, Long newParentId) {
        if (nodeId == null || newParentId == null) {
            return false;
        }
        if (nodeId.equals(newParentId)) {
            return true;
        }
        return parseAncestorIds(newParentPath).contains(nodeId);
    }

    /**
     * 归一化直接子项目权重（BR-3）：返回 0-1 小数的权重列表，合计=1。
     * <p>
     * 全部 NULL → 等权（每个 1/n）；全部非 NULL → 校验合计=100% 后折算为 0-1；
     * 部分 NULL 部分非 NULL → 非法（权重语义不可混合）。
     *
     * @throws IllegalArgumentException 混合配置或合计≠100%
     */
    public static List<BigDecimal> normalizedWeights(List<BigDecimal> configuredWeights) {
        int n = configuredWeights.size();
        if (n == 0) {
            return List.of();
        }
        boolean allNull = configuredWeights.stream().allMatch(Objects::isNull);
        boolean allSet = configuredWeights.stream().allMatch(Objects::nonNull);
        if (allNull) {
            BigDecimal each = BigDecimal.ONE.divide(BigDecimal.valueOf(n), WEIGHT_SCALE, RoundingMode.HALF_UP);
            return Collections.nCopies(n, each);
        }
        if (!allSet) {
            throw new IllegalArgumentException("权重配置必须全等权或全手动，不得混合");
        }
        BigDecimal sum = configuredWeights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(HUNDRED) != 0) {
            throw new IllegalArgumentException("直接子项目权重合计必须为100%，当前=" + sum);
        }
        return configuredWeights.stream()
                .map(weight -> weight.divide(HUNDRED, WEIGHT_SCALE, RoundingMode.HALF_UP))
                .toList();
    }

    /**
     * 进度汇总（BR-3）：父进度 = Σ(直接子项目进度 × 归一化权重)，结果保留 2 位。
     * 子项目进度 NULL 视为 0（progress 列 NOT NULL DEFAULT 0，NULL 仅防御）。
     *
     * @throws IllegalArgumentException 进度与权重数量不一致
     */
    public static BigDecimal aggregateProgress(List<BigDecimal> progresses, List<BigDecimal> weights) {
        if (progresses.size() != weights.size()) {
            throw new IllegalArgumentException("进度与权重数量不一致");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < progresses.size(); i++) {
            BigDecimal progress = progresses.get(i) == null ? BigDecimal.ZERO : progresses.get(i);
            sum = sum.add(progress.multiply(weights.get(i)));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }
}
