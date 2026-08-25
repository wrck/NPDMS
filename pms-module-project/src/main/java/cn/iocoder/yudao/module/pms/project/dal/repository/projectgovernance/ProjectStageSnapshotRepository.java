package cn.iocoder.yudao.module.pms.project.dal.repository.projectgovernance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectExceptionCloseSnapshotQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectgovernance.ProjectStageSnapshotRules;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/** 共享阶段快照的唯一追加入口。 */
@Repository
public class ProjectStageSnapshotRepository {

    @Resource
    private ProjectStageSnapshotMapper mapper;
    @Resource
    private ProjectMasterMapper projectMasterMapper;

    public int append(ProjectStageSnapshotDO snapshot) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (snapshot.getTenantId() != null && !Objects.equals(snapshot.getTenantId(), tenantId)) {
            throw new IllegalArgumentException("snapshot tenant must match trusted tenant context");
        }
        snapshot.setTenantId(tenantId);
        ProjectStageSnapshotRules.validateGovernanceAction(snapshot);
        return mapper.insertAppendOnly(snapshot);
    }

    /** 先锁定稳定的项目行，再判定尚未被重开消费的异常关闭快照，统一并发锁序。 */
    public ProjectStageSnapshotDO selectLatestReusableExceptionCloseForUpdate(
            ProjectExceptionCloseSnapshotQuery query) {
        if (!Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("query tenant must match trusted tenant context");
        }
        if (projectMasterMapper.selectByIdForUpdate(query.projectId()) == null) {
            return null;
        }
        return mapper.selectLatestReusableExceptionClose(query);
    }
}
