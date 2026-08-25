package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectExceptionCloseSnapshotQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectGovernanceHistoryPageQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGovernanceStateUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectServiceManagerIntervalClose;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class ProjectGovernancePersistenceMySqlIntegrationTest
        extends ProjectManualCreationMySqlTestSupport {

    @Resource
    private ProjectMasterMapper projectMasterMapper;
    @Resource
    private ProjectMemberAssignmentMapper memberAssignmentMapper;
    @Resource
    private ProjectStageSnapshotMapper snapshotMapper;

    @Test
    @Transactional
    void casIntervalsHistoryAndReopenConsumptionUseRealMySql() {
        var project = applicationService.create(newCommand(), newActor());
        int version = currentVersion(project.id());
        assertEquals(1, projectMasterMapper.updateGovernanceStateIfMatch(
                new ProjectGovernanceStateUpdate(0L, project.id(), version, "ACTIVE",
                        "S0", "EXCEPTION_CLOSED", "UNASSIGNED", "fproj006-it")));
        assertEquals(0, projectMasterMapper.updateGovernanceStateIfMatch(
                new ProjectGovernanceStateUpdate(0L, project.id(), version, "ACTIVE",
                        "S0", "EXCEPTION_CLOSED", "UNASSIGNED", "fproj006-it")));

        LocalDateTime closedAt = LocalDateTime.now().withNano(0);
        insertMember(project.id(), 9_920_061L, "SERVICE_MANAGER_L1", "PRIMARY", closedAt);
        insertMember(project.id(), 9_920_062L, "SERVICE_MANAGER_L2", "COLLABORATOR", closedAt);
        insertMember(project.id(), 9_920_063L, "PROJECT_MANAGER", null, closedAt);
        assertEquals(2, memberAssignmentMapper.closeEffectiveServiceManagerAssignments(
                new ProjectServiceManagerIntervalClose(0L, project.id(), closedAt, "fproj006-it")));
        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_member_assignment "
                        + "WHERE project_id=? AND effective_to=?", Long.class, project.id(), closedAt));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_member_assignment "
                        + "WHERE project_id=? AND member_role='PROJECT_MANAGER' AND effective_to IS NULL",
                Long.class, project.id()));

        ProjectStageSnapshotDO close = snapshot(project.id(), 1, "EXCEPTION_CLOSE", null, closedAt);
        snapshotMapper.insert(close);
        assertEquals(close.getId(), snapshotMapper.selectLatestReusableExceptionCloseForUpdate(
                new ProjectExceptionCloseSnapshotQuery(0L, project.id())).getId());
        assertEquals(1, snapshotMapper.selectGovernanceHistoryPage(
                new ProjectGovernanceHistoryPageQuery(0L, project.id(), 0, 10)).getList().size());

        snapshotMapper.insert(snapshot(project.id(), 2, "REOPEN", close.getId(), closedAt.plusSeconds(1)));
        assertNull(snapshotMapper.selectLatestReusableExceptionCloseForUpdate(
                new ProjectExceptionCloseSnapshotQuery(0L, project.id())));
        assertEquals(2, snapshotMapper.selectGovernanceHistoryPage(
                new ProjectGovernanceHistoryPageQuery(0L, project.id(), 0, 10)).getList().size());
    }

    private int currentVersion(Long projectId) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM proj_project WHERE id=?", Integer.class, projectId);
    }

    private void insertMember(Long projectId, Long userId, String role, String assignmentType,
                              LocalDateTime closedAt) {
        jdbcTemplate.update("INSERT INTO proj_project_member_assignment "
                        + "(project_id,user_id,member_role,assignment_type,responsibility,effective_from,"
                        + "status,version,tenant_id) VALUES (?,?,?,?,?,?, 'ACTIVE',1,0)",
                projectId, userId, role, assignmentType, role, closedAt.minusMinutes(1));
    }

    private ProjectStageSnapshotDO snapshot(Long projectId, int snapshotNo, String operationType,
                                             Long relatedSnapshotId, LocalDateTime operatedAt) {
        ProjectStageSnapshotDO snapshot = new ProjectStageSnapshotDO();
        snapshot.setTenantId(0L);
        snapshot.setProjectId(projectId);
        snapshot.setStageCode("S0");
        snapshot.setSnapshotNo(snapshotNo);
        snapshot.setOperationType(operationType);
        snapshot.setRelatedSnapshotId(relatedSnapshotId);
        snapshot.setOperationId("fproj006-it-" + projectId + "-" + snapshotNo);
        snapshot.setOperatedAt(operatedAt);
        return snapshot;
    }
}
