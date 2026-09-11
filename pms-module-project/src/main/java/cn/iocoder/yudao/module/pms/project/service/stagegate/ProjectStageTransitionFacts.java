package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 当前完成及准出已核实后，向目标准入提供本次推进事实；不提前持久化阶段状态。 */
final class ProjectStageTransitionFacts {
    private ProjectStageTransitionFacts() { }

    static List<ProjectGateInstanceDO> exitBeforeEntry(List<ProjectGateInstanceDO> gates) {
        return gates.stream().sorted(Comparator.comparingInt(gate -> "EXIT".equals(gate.getGateType()) ? 0 : 1)).toList();
    }

    static ProjectStageGateFact completionForEntry(ProjectStageInstanceDO current, ProjectStageInstanceDO target,
                                                   ProjectGateInstanceDO gate, ProjectGateReferenceInstanceDO reference,
                                                   boolean currentReadyToComplete) {
        if (!currentReadyToComplete || target == null || !"ACTIVE".equals(current.getStatus())
                || !"PENDING".equals(target.getStatus()) || !"ENTRY".equals(gate.getGateType())
                || !Objects.equals(target.getStageCode(), gate.getStageCode())
                || !"STATE".equals(reference.getRefType())
                || !Objects.equals(current.getStageCode(), ProjectLocalStageGateFactProvider.completedStageCode(reference.getRefCode()))) {
            return null;
        }
        // 标明这是求值事实而非已持久化DONE；沿用实际阶段身份和当前版本进入审计。
        return new ProjectStageGateFact(ProjectStageGateFactProviderApi.PROVIDER_PROJ_STATE, reference.getRefType(),
                String.valueOf(current.getId()), "COMPLETION_VERIFIED", String.valueOf(current.getVersion()),
                ProjectStageGateOutcome.SATISFIED, null);
    }
}
