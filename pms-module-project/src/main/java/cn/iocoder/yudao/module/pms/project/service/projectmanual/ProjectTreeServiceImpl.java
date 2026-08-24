package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectTreeRules;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_WEIGHT_SUM_INVALID;

/**
 * 项目树与进度汇总 Service 实现（F-PM02 / PM-02）
 * <p>
 * 仅保留V1.7进度与权重兼容能力；版本化树查询与移动由projecttree包承接。
 */
@Service("projectMasterTreeService")
@Validated
public class ProjectTreeServiceImpl implements ProjectTreeService {

    @Resource
    private ProjectMasterMapper projectMasterMapper;
    @Resource
    private ProjectTreeVersionMapper projectTreeVersionMapper;
    @Resource
    private ProjectTreeScopeService projectTreeScopeService;

    @Override
    public ProjectProgress getProgress(Long projectId, Long actorId) {
        ProjectMasterDO node = validateProjectExists(projectId);
        assertFullAccess(node, actorId);
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
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void updateChildWeights(Long projectId, Map<Long, BigDecimal> childWeights, Long actorId) {
        ProjectMasterDO project = validateProjectExists(projectId);
        assertFullAccess(project, actorId);
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

    private void assertFullAccess(ProjectMasterDO project, Long actorId) {
        Long rootId = project.getRootId() == null ? project.getId() : project.getRootId();
        ProjectTreeVersionDO activeVersion = projectTreeVersionMapper.selectLatestActive(rootId);
        if (activeVersion == null) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        projectTreeScopeService.assertFullAccess(actorId, project.getId(), activeVersion.getTreeVersion());
    }
}
