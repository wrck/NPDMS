package cn.iocoder.yudao.module.pms.project.api.commerce;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectAcceptanceStageFact;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectAcceptanceStageFactQuery;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectStageEntrySnapshotQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules.STATUS_S5;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;

@Service
@RequiredArgsConstructor
public class ProjectAcceptanceStageFactApiImpl implements ProjectAcceptanceStageFactApi {

    private static final String ACTIVE = "ACTIVE";
    private static final String STAGE_ACTIVE = "ACTIVE";

    private final ProjectMasterMapper projectMapper;
    private final ProjectStageInstanceMapper stageMapper;
    private final ProjectStageSnapshotMapper snapshotMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public ProjectAcceptanceStageFact lockAndRead(ProjectAcceptanceStageFactQuery query) {
        validate(query);
        ProjectMasterDO project = projectMapper.selectByIdForUpdate(query.projectId());
        if (project == null) {
            return outcome(ProjectFactOutcome.NOT_FOUND, query.projectId(), null, null, null);
        }
        if (!Objects.equals(project.getTenantId(), query.tenantId())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        if (!Objects.equals(project.getVersion(), query.expectedProjectVersion())) {
            return outcome(ProjectFactOutcome.VERSION_CONFLICT, project.getId(), project.getVersion(),
                    project.getCurrentStage(), null);
        }
        if (!ACTIVE.equals(project.getLifecycleStatus())) {
            return outcome(ProjectFactOutcome.INACTIVE, project.getId(), project.getVersion(),
                    project.getCurrentStage(), null);
        }
        ProjectStageInstanceDO acceptanceStage = stageMapper.selectByProjectIdAndStageCode(project.getId(), STATUS_S5);
        if (!validStage(acceptanceStage, query.tenantId(), project.getId())) {
            return outcome(ProjectFactOutcome.NOT_FOUND, project.getId(), project.getVersion(),
                    project.getCurrentStage(), null);
        }
        Long snapshotId = null;
        if (Objects.equals(project.getCurrentStage(), acceptanceStage.getStageCode())) {
            if (!STAGE_ACTIVE.equals(acceptanceStage.getStatus())) {
                return outcome(ProjectFactOutcome.NOT_FOUND, project.getId(), project.getVersion(),
                        project.getCurrentStage(), acceptanceStage.getStageCode());
            }
            ProjectStageSnapshotDO snapshot = snapshotMapper.selectLatestStageEntry(
                    new ProjectStageEntrySnapshotQuery(query.tenantId(), project.getId(), acceptanceStage.getStageCode()));
            if (!validSnapshot(snapshot, query.tenantId(), project.getId(), acceptanceStage.getStageCode())) {
                return outcome(ProjectFactOutcome.NOT_FOUND, project.getId(), project.getVersion(),
                        project.getCurrentStage(), acceptanceStage.getStageCode());
            }
            snapshotId = snapshot.getId();
        }
        return new ProjectAcceptanceStageFact(ProjectFactOutcome.FOUND, project.getId(), project.getVersion(),
                project.getCurrentStage(), acceptanceStage.getStageCode(), snapshotId);
    }

    private void validate(ProjectAcceptanceStageFactQuery query) {
        Long trustedTenantId = TenantContextHolder.getTenantId();
        if (query == null || trustedTenantId == null || trustedTenantId < 0
                || !Objects.equals(query.tenantId(), trustedTenantId)
                || query.projectId() == null || query.projectId() <= 0
                || query.expectedProjectVersion() == null || query.expectedProjectVersion() < 0
                || query.operationId() == null || query.operationId().isBlank()) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private boolean validStage(ProjectStageInstanceDO stage, Long tenantId, Long projectId) {
        return stage != null && Objects.equals(stage.getTenantId(), tenantId)
                && Objects.equals(stage.getProjectId(), projectId)
                && STATUS_S5.equals(stage.getStageCode());
    }

    private boolean validSnapshot(ProjectStageSnapshotDO snapshot, Long tenantId, Long projectId, String stageCode) {
        return snapshot != null && snapshot.getId() != null
                && Objects.equals(snapshot.getTenantId(), tenantId)
                && Objects.equals(snapshot.getProjectId(), projectId)
                && Objects.equals(snapshot.getStageCode(), stageCode)
                && "STAGE_ENTRY".equals(snapshot.getOperationType())
                && Objects.equals(snapshot.getAfterStage(), stageCode);
    }

    private ProjectAcceptanceStageFact outcome(ProjectFactOutcome outcome, Long projectId, Integer version,
                                                 String currentStage, String acceptanceStage) {
        return new ProjectAcceptanceStageFact(outcome, projectId, version, currentStage, acceptanceStage, null);
    }
}
