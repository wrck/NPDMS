package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 项目树与进度汇总 Service（F-PM02 / PM-02）
 * <p>
 * 四类层级查询（直接下级/全部后代/完整上级链/指定业务层级）、无环子树移动、进度汇总口径
 * （等权兜底 + 手动权重合计 100% 校验 + Σ 子进度×归一化权重）。树缓存重建在单事务内。
 */
public interface ProjectTreeService {

    /**
     * 直接下级（按 tree_sort、id 升序；按需加载）。
     */
    List<ProjectMasterDO> getChildren(Long projectId);

    /**
     * 全部后代（tree_path 前缀匹配，不含自身）。
     */
    List<ProjectMasterDO> getDescendants(Long projectId);

    /**
     * 完整上级链（根→父顺序，不含自身）。
     */
    List<ProjectMasterDO> getAncestors(Long projectId);

    /**
     * 指定业务层级（business_level_code 精确，按结构深度排序）。
     */
    List<ProjectMasterDO> getByBusinessLevel(String businessLevelCode);

    /**
     * 子树移动：校验无环（非自身/非后代）后重建子树 root_id/tree_path/tree_depth 缓存。
     */
    void moveSubtree(Long projectId, Long newParentId);

    /**
     * 整组设置直接子项目人工权重；请求必须完整覆盖当前直接子项目且合计为 100%。
     */
    void updateChildWeights(Long projectId, Map<Long, BigDecimal> childWeights);

    /**
     * 进度汇总：直接子项目进度列表 + 归一化权重 + 汇总进度。
     */
    ProjectProgress getProgress(Long projectId);

    /**
     * 直接子项目进度项。
     */
    record ChildProgress(Long projectId, String projectCode, String projectName,
                         BigDecimal progress, BigDecimal normalizedWeight, String weightSource) {
    }

    /**
     * 项目进度汇总结果。
     */
    record ProjectProgress(BigDecimal aggregate, List<ChildProgress> children) {
    }
}
