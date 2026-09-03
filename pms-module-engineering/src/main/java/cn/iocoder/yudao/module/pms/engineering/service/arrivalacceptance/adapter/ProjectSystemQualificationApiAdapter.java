package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.adapter;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectSystemQualificationPort;
import cn.iocoder.yudao.module.pms.project.api.systemqualification.ProjectSystemQualificationFactApi;
import cn.iocoder.yudao.module.pms.project.api.systemqualification.dto.ProjectSystemQualificationFact;
import cn.iocoder.yudao.module.pms.project.api.systemqualification.dto.ProjectSystemQualificationLockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 豁免到期内部命令到PROJ当前系统资格事实的生产适配。 */
@Component
@RequiredArgsConstructor
public class ProjectSystemQualificationApiAdapter implements ProjectSystemQualificationPort {

    private static final String ACTIVE = "ACTIVE";
    private static final String ARRIVAL_STAGE = "S4";

    private final ProjectSystemQualificationFactApi factApi;

    @Override
    public CurrentProjectQualification lockCurrent(Long tenantId, Long projectId) {
        if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0
                || !Objects.equals(tenantId, TenantContextHolder.getTenantId())) {
            throw new IllegalStateException("trusted tenant or project identity is invalid");
        }
        ProjectSystemQualificationFact fact = factApi.lockCurrentForSystem(
                new ProjectSystemQualificationLockQuery(projectId, ACTIVE, ARRIVAL_STAGE));
        if (fact == null || !Objects.equals(projectId, fact.projectId())
                || !ACTIVE.equals(fact.lifecycleStatus()) || !ARRIVAL_STAGE.equals(fact.currentStage())) {
            throw new IllegalStateException("current project system qualification is unavailable or mismatched");
        }
        return new CurrentProjectQualification(
                fact.projectId(), fact.currentManagerUserId(), fact.currentProjectVersion(),
                fact.currentParticipantFactVersion(), fact.currentTreeVersion());
    }
}
