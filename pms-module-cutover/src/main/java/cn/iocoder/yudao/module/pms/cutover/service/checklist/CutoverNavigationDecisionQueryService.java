package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.NavigationDecision;

import java.util.Objects;
import java.util.Set;

public class CutoverNavigationDecisionQueryService {

    private static final Set<String> READABLE_STATUSES = Set.of("PUBLISHED", "DISABLED");

    private final CutoverTaskMapper taskMapper;
    private final CutoverConfigurationRevisionMapper revisionMapper;
    private final CutoverNavigationDecisionPolicy policy;

    public CutoverNavigationDecisionQueryService(CutoverTaskMapper taskMapper,
                                                 CutoverConfigurationRevisionMapper revisionMapper,
                                                 CutoverNavigationDecisionPolicy policy) {
        this.taskMapper = taskMapper;
        this.revisionMapper = revisionMapper;
        this.policy = policy;
    }

    public NavigationDecision decide(Long tenantId, Long taskId) {
        CutoverTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(tenantId, task.getTenantId())
                || task.getConfigurationRevisionId() == null || task.getConfigurationRevisionId() <= 0) {
            throw new IllegalStateException("割接任务冻结配置不存在");
        }
        CutoverConfigurationRevisionDO revision = revisionMapper.selectById(task.getConfigurationRevisionId());
        if (revision == null || !Objects.equals(tenantId, revision.getTenantId())
                || !Objects.equals(task.getConfigurationCode(), revision.getConfigurationCode())
                || !Objects.equals(task.getConfigurationRevisionNo(), revision.getRevisionNo())
                || !READABLE_STATUSES.contains(revision.getStatusCode())) {
            throw new IllegalStateException("割接任务冻结配置身份不匹配");
        }
        return policy.decide(revision.getId(), revision.getNavigationRuleSnapshot());
    }
}
