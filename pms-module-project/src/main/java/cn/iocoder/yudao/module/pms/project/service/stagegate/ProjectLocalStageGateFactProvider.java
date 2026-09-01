package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMilestoneInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.stagegate.ProjectLocalStageGateFactMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.stagegate.query.ProjectLocalGateFactQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProjectLocalStageGateFactProvider implements ProjectStageGateFactProviderApi {

    private static final Map<String, String> STATE_STAGE_CODES = Map.of(
            "S0_COMPLETED", "S0", "S1_COMPLETED", "S1", "S2_COMPLETED", "S2",
            "S3_COMPLETED", "S3", "S4_COMPLETED", "S4", "S5_COMPLETED", "S5",
            "S6_COMPLETED", "S6");

    private final ProjectLocalStageGateFactMapper mapper;

    @Override
    public Set<String> providerKeys() {
        return Set.of(PROVIDER_PROJ_TASK, PROVIDER_PROJ_MILESTONE, PROVIDER_PROJ_STATE);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectStageGateFact lockAndRevalidate(ProjectStageGateFactQuery query) {
        validate(query);
        return switch (query.refType()) {
            case "TASK" -> task(query);
            case "MILESTONE" -> milestone(query);
            case "STATE" -> state(query);
            default -> throw new IllegalArgumentException("unsupported PROJ gate refType: " + query.refType());
        };
    }

    private ProjectStageGateFact task(ProjectStageGateFactQuery query) {
        ProjectTaskInstanceDO row = mapper.selectTaskForUpdate(localQuery(query, query.refCode()));
        if (row == null) return unavailable(PROVIDER_PROJ_TASK, query, "TASK_NOT_FOUND");
        return fact(PROVIDER_PROJ_TASK, query, row.getId(), row.getStatus(), row.getVersion(),
                "DONE".equals(row.getStatus()), "TASK_NOT_DONE");
    }

    private ProjectStageGateFact milestone(ProjectStageGateFactQuery query) {
        ProjectMilestoneInstanceDO row = mapper.selectMilestoneForUpdate(localQuery(query, query.refCode()));
        if (row == null) return unavailable(PROVIDER_PROJ_MILESTONE, query, "MILESTONE_NOT_FOUND");
        return fact(PROVIDER_PROJ_MILESTONE, query, row.getId(), row.getStatus(), row.getVersion(),
                "ACHIEVED".equals(row.getStatus()), "MILESTONE_NOT_ACHIEVED");
    }

    private ProjectStageGateFact state(ProjectStageGateFactQuery query) {
        String stageCode = STATE_STAGE_CODES.get(query.refCode());
        if (stageCode == null) return unavailable(PROVIDER_PROJ_STATE, query, "STATE_CODE_UNKNOWN");
        ProjectStageInstanceDO row = mapper.selectStageForUpdate(localQuery(query, stageCode));
        if (row == null) return unavailable(PROVIDER_PROJ_STATE, query, "STATE_NOT_FOUND");
        return fact(PROVIDER_PROJ_STATE, query, row.getId(), row.getStatus(), row.getVersion(),
                "DONE".equals(row.getStatus()), "STATE_NOT_DONE");
    }

    private static ProjectLocalGateFactQuery localQuery(ProjectStageGateFactQuery query, String ownerCode) {
        return new ProjectLocalGateFactQuery(query.tenantId(), query.projectId(), ownerCode);
    }

    private static ProjectStageGateFact fact(String providerKey, ProjectStageGateFactQuery query,
                                             Long id, String status, Integer version,
                                             boolean satisfied, String unmetCode) {
        return new ProjectStageGateFact(providerKey, query.refType(), String.valueOf(id),
                value(status), value(version), satisfied ? ProjectStageGateOutcome.SATISFIED
                : ProjectStageGateOutcome.UNSATISFIED, satisfied ? null : unmetCode);
    }

    private static ProjectStageGateFact unavailable(String providerKey, ProjectStageGateFactQuery query,
                                                    String unmetCode) {
        return new ProjectStageGateFact(providerKey, query.refType(), query.refCode(),
                "UNKNOWN", "UNKNOWN", ProjectStageGateOutcome.DEPENDENCY_UNAVAILABLE, unmetCode);
    }

    private static void validate(ProjectStageGateFactQuery query) {
        Long trustedTenantId = TenantContextHolder.getRequiredTenantId();
        if (query == null || query.tenantId() == null || !Objects.equals(query.tenantId(), trustedTenantId)
                || query.projectId() == null || query.projectId() <= 0
                || blank(query.refType()) || blank(query.refCode())) {
            throw new IllegalArgumentException("invalid project local stage gate query");
        }
    }

    private static String value(Object value) {
        return value == null ? "UNKNOWN" : String.valueOf(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
