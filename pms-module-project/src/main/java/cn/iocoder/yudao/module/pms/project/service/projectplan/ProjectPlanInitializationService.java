package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectPlanInitializationService {
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;
    private final ProjectTemplateService templates;
    private final cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleTimerScheduler timers;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void initialize(ProjectMasterDO project, TemplateExecutionSnapshot snapshot,
                           List<ProjectStageExecutionContractDO> stageContracts,
                           List<ProjectTaskExecutionContractDO> taskContracts) {
        if (project.getActivePlanVersionId() != null) throw new IllegalArgumentException("PROJECT_PLAN_ALREADY_INITIALIZED");
        var source = templates.getRevisionById(project.getLifecycleTemplateRevisionId());
        if (source == null || !"PUBLISHED".equals(source.getStatus()) || source.getDesignerDocument() == null
                || !Objects.equals(source.getTenantId(), project.getTenantId())
                || !Objects.equals(source.getId(), project.getLifecycleTemplateRevisionId())
                || !Objects.equals(source.getTemplateId(), project.getLifecycleTemplateId())
                || !Objects.equals(source.getRevisionNo(), project.getLifecycleTemplateRevisionNo()))
            throw new IllegalArgumentException("PUBLISHED_DESIGNER_REQUIRED");
        if (stageContracts.size() != snapshot.getStages().size() || taskContracts.size() != snapshot.getTasks().size())
            throw new IllegalArgumentException("FROZEN_CONTRACT_SET_INCOMPLETE");
        var plan = new ProjectPlanVersionDO();
        plan.setTenantId(project.getTenantId()); plan.setProjectId(project.getId()); plan.setRevisionNo(1);
        plan.setStatus("EFFECTIVE"); plan.setSourceTemplateRevisionId(source.getId());
        plan.setDesignerDocument(source.getDesignerDocument()); plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        plan.setEffectiveAt(LocalDateTime.now()); plan.setVersion(0);
        if (plans.insert(plan) != 1) throw new IllegalStateException("PROJECT_PLAN_INITIALIZATION_FAILED");
        for (var contract : stageContracts)
            insertRound(project, plan, "STAGE", contract.getStageId(), contract.getSourceNodeKey(), contract.getId());
        for (var contract : taskContracts)
            insertRound(project, plan, "TASK", contract.getProjectTaskId(), contract.getSourceNodeKey(), contract.getId());
        if (plans.attachInitialPlan(new ProjectPlanVersionMapper.InitialPlanBinding(project.getTenantId(), project.getId(), plan.getId())) != 1)
            throw new IllegalStateException("PROJECT_PLAN_BINDING_CONFLICT");
        project.setActivePlanVersionId(plan.getId());
        timers.schedule(project.getId(), plan.getId(), snapshot, null);
    }

    private void insertRound(ProjectMasterDO project, ProjectPlanVersionDO plan, String kind, Long instanceId, String nodeKey, Long contractId) {
        if (nodeKey == null || nodeKey.isBlank() || contractId == null || instanceId == null)
            throw new IllegalArgumentException("FROZEN_NODE_IDENTITY_REQUIRED");
        var round = new ProjectNodeExecutionDO();
        round.setTenantId(project.getTenantId()); round.setProjectId(project.getId()); round.setPlanVersionId(plan.getId());
        round.setNodeKind(kind); round.setNodeKey(nodeKey); round.setNodeInstanceId(instanceId); round.setContractId(contractId);
        round.setRoundNo(1); round.setCurrentMarker(1); round.setStatus("PENDING"); round.setVersion(0);
        if (executions.insert(round) != 1) throw new IllegalStateException("NODE_ROUND_INITIALIZATION_FAILED");
    }
}
