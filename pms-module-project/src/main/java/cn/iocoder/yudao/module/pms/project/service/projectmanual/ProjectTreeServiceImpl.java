package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectTreeRules;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_MOVE_CYCLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_WEIGHT_SUM_INVALID;

/**
 * 项目树与进度汇总 Service 实现（F-PM02 / PM-02）
 * <p>
 * 四类层级查询基于 tree_path 前缀 + root_id 过滤；子树移动在单事务内校验无环并重建
 * 子树 root_id/tree_path/tree_depth；进度汇总走等权兜底 + 手动权重合计 100% 校验。
 */
@Service("projectMasterTreeService")
@Validated
public class ProjectTreeServiceImpl implements ProjectTreeService {

    @Resource
    private ProjectMasterMapper projectMasterMapper;

    @Override
    public List<ProjectMasterDO> getChildren(Long projectId) {
        validateProjectExists(projectId);
        return projectMasterMapper.selectChildren(projectId);
    }

    @Override
    public List<ProjectMasterDO> getDescendants(Long projectId) {
        ProjectMasterDO node = validateProjectExists(projectId);
        String prefix = ProjectTreeRules.descendantPrefix(node.getTreePath(), node.getId());
        return projectMasterMapper.selectDescendants(node.getRootId(), prefix);
    }

    @Override
    public List<ProjectMasterDO> getAncestors(Long projectId) {
        ProjectMasterDO node = validateProjectExists(projectId);
        List<Long> ancestorIds = ProjectTreeRules.parseAncestorIds(node.getTreePath());
        if (ancestorIds.isEmpty()) {
            return List.of();
        }
        List<ProjectMasterDO> ancestors = projectMasterMapper.selectBatchIds(ancestorIds);
        Map<Long, ProjectMasterDO> byId = ancestors.stream()
                .collect(Collectors.toMap(ProjectMasterDO::getId, Function.identity()));
        return ancestorIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    @Override
    public List<ProjectMasterDO> getByBusinessLevel(String businessLevelCode) {
        return projectMasterMapper.selectByBusinessLevel(businessLevelCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveSubtree(Long projectId, Long newParentId) {
        ProjectMasterDO node = validateProjectExists(projectId);
        ProjectMasterDO newParent = validateProjectExists(newParentId);
        // BR-1 无环校验：目标父为自身或自身后代时拒绝
        if (ProjectTreeRules.wouldCreateCycle(node.getId(), newParent.getTreePath(), newParent.getId())) {
            throw exception(PROJECT_MOVE_CYCLE);
        }
        String newTreePath = ProjectTreeRules.buildChildPath(newParent.getTreePath(), newParent.getId());
        int newDepth = ProjectTreeRules.buildChildDepth(newParent.getTreeDepth());
        int depthDelta = newDepth - node.getTreeDepth();
        String oldPrefix = ProjectTreeRules.descendantPrefix(node.getTreePath(), node.getId());
        String newPrefix = ProjectTreeRules.descendantPrefix(newTreePath, node.getId());
        List<ProjectMasterDO> descendants = projectMasterMapper.selectDescendants(node.getRootId(), oldPrefix);
        // 更新被移动节点自身
        ProjectMasterDO updateNode = new ProjectMasterDO();
        updateNode.setId(node.getId());
        updateNode.setParentId(newParentId);
        updateNode.setRootId(newParent.getRootId());
        updateNode.setTreePath(newTreePath);
        updateNode.setTreeDepth(newDepth);
        projectMasterMapper.updateById(updateNode);
        // 更新后代（tree_path 前缀替换 + 深度增量 + root_id 对齐新根）
        for (ProjectMasterDO descendant : descendants) {
            String relative = descendant.getTreePath().substring(oldPrefix.length());
            ProjectMasterDO updateDescendant = new ProjectMasterDO();
            updateDescendant.setId(descendant.getId());
            updateDescendant.setRootId(newParent.getRootId());
            updateDescendant.setTreePath(newPrefix + relative);
            updateDescendant.setTreeDepth(descendant.getTreeDepth() + depthDelta);
            projectMasterMapper.updateById(updateDescendant);
        }
    }

    @Override
    public ProjectProgress getProgress(Long projectId) {
        ProjectMasterDO node = validateProjectExists(projectId);
        List<ProjectMasterDO> children = projectMasterMapper.selectChildren(projectId);
        if (children.isEmpty()) {
            return new ProjectProgress(BigDecimal.ZERO, List.of());
        }
        List<BigDecimal> configuredWeights = children.stream()
                .map(ProjectMasterDO::getAggregationWeight).toList();
        List<BigDecimal> weights;
        try {
            weights = ProjectTreeRules.normalizedWeights(configuredWeights);
        } catch (IllegalArgumentException ex) {
            throw exception(PROJECT_WEIGHT_SUM_INVALID, ex.getMessage());
        }
        List<BigDecimal> progresses = children.stream().map(ProjectMasterDO::getProgress).toList();
        BigDecimal aggregate = ProjectTreeRules.aggregateProgress(progresses, weights);
        List<ChildProgress> childProgresses = new ArrayList<>(children.size());
        for (int i = 0; i < children.size(); i++) {
            ProjectMasterDO child = children.get(i);
            childProgresses.add(new ChildProgress(
                    child.getId(), child.getProjectCode(), child.getProjectName(),
                    child.getProgress() == null ? BigDecimal.ZERO : child.getProgress(),
                    weights.get(i), child.getWeightSource()));
        }
        return new ProjectProgress(aggregate, childProgresses);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChildWeights(Long projectId, Map<Long, BigDecimal> childWeights) {
        validateProjectExists(projectId);
        List<ProjectMasterDO> children = projectMasterMapper.selectChildren(projectId);
        List<Long> childIds = children.stream().map(ProjectMasterDO::getId).toList();
        if (children.isEmpty() || childWeights.size() != children.size()
                || !childWeights.keySet().containsAll(childIds)) {
            throw exception(PROJECT_WEIGHT_SUM_INVALID, "必须完整覆盖当前全部直接子项目");
        }
        List<BigDecimal> weights = childIds.stream().map(childWeights::get).toList();
        try {
            ProjectTreeRules.normalizedWeights(weights);
        } catch (IllegalArgumentException ex) {
            throw exception(PROJECT_WEIGHT_SUM_INVALID, ex.getMessage());
        }
        List<ProjectMasterDO> updates = childIds.stream().map(childId -> {
            ProjectMasterDO update = new ProjectMasterDO();
            update.setId(childId);
            update.setAggregationWeight(childWeights.get(childId));
            update.setWeightSource(ProjectTreeRules.WEIGHT_SOURCE_MANUAL);
            return update;
        }).toList();
        projectMasterMapper.updateById(updates);
    }

    private ProjectMasterDO validateProjectExists(Long id) {
        ProjectMasterDO project = projectMasterMapper.selectById(id);
        if (project == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        return project;
    }
}
