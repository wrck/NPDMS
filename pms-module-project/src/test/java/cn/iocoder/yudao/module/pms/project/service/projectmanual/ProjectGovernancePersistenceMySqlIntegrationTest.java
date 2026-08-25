package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectExceptionCloseSnapshotQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectGovernanceHistoryPageQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGovernanceStateUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectServiceManagerIntervalClose;
import cn.iocoder.yudao.module.pms.project.dal.repository.projectgovernance.ProjectStageSnapshotRepository;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@Import(ProjectStageSnapshotRepository.class)
class ProjectGovernancePersistenceMySqlIntegrationTest
        extends ProjectManualCreationMySqlTestSupport {

    @Resource
    private ProjectMasterMapper projectMasterMapper;
    @Resource
    private ProjectMemberAssignmentMapper memberAssignmentMapper;
    @Resource
    private ProjectStageSnapshotMapper snapshotMapper;
    @Resource
    private ProjectStageSnapshotRepository snapshotRepository;
    @Resource
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanGovernanceSnapshotsBefore() {
        cleanGovernanceSnapshots();
    }

    @AfterEach
    void cleanGovernanceSnapshotsAfter() {
        cleanGovernanceSnapshots();
    }

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

        ProjectStageSnapshotDO close = exceptionCloseSnapshot(project.id(), 1, closedAt);
        snapshotRepository.append(close);
        assertEquals(close.getId(), snapshotRepository.selectLatestReusableExceptionCloseForUpdate(
                new ProjectExceptionCloseSnapshotQuery(0L, project.id())).getId());
        assertEquals(1, snapshotMapper.selectGovernanceHistoryPage(
                new ProjectGovernanceHistoryPageQuery(0L, project.id(), page(1, 10))).getList().size());

        snapshotRepository.append(reopenSnapshot(project.id(), 2, close.getId(), closedAt.plusSeconds(1)));
        assertNull(snapshotRepository.selectLatestReusableExceptionCloseForUpdate(
                new ProjectExceptionCloseSnapshotQuery(0L, project.id())));
        assertEquals(2, snapshotMapper.selectGovernanceHistoryPage(
                new ProjectGovernanceHistoryPageQuery(0L, project.id(), page(1, 10))).getList().size());
    }

    @Test
    void concurrentReopenConsumersHaveOneWinner() throws Exception {
        var project = applicationService.create(newCommand(), newActor());
        LocalDateTime operatedAt = LocalDateTime.now().withNano(0);
        ProjectStageSnapshotDO close = exceptionCloseSnapshot(project.id(), 1, operatedAt);
        snapshotRepository.append(close);

        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondReadViewEstablished = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> inTenantTransaction(() -> {
                ProjectStageSnapshotDO selected = snapshotRepository.selectLatestReusableExceptionCloseForUpdate(
                        new ProjectExceptionCloseSnapshotQuery(0L, project.id()));
                firstLocked.countDown();
                await(releaseFirst);
                snapshotRepository.append(reopenSnapshot(
                        project.id(), 2, selected.getId(), operatedAt.plusSeconds(1)));
                return selected.getId();
            }));
            firstLocked.await();

            var second = executor.submit(() -> inTenantTransaction(() -> {
                assertEquals("REPEATABLE-READ", jdbcTemplate.queryForObject(
                        "SELECT @@transaction_isolation", String.class));
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM proj_project_stage_snapshot WHERE tenant_id=0 AND project_id=?",
                        Long.class, project.id());
                secondReadViewEstablished.countDown();
                return snapshotRepository.selectLatestReusableExceptionCloseForUpdate(
                        new ProjectExceptionCloseSnapshotQuery(0L, project.id()));
            }));
            await(secondReadViewEstablished);
            try {
                assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
                assertFalse(second.isDone());
            } finally {
                releaseFirst.countDown();
            }

            assertEquals(close.getId(), first.get());
            assertNull(second.get());
        }
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_stage_snapshot WHERE tenant_id=0 AND project_id=? "
                        + "AND operation_type='REOPEN' AND related_snapshot_id=?",
                Long.class, project.id(), close.getId()));
    }

    @Test
    void appendRejectsSnapshotFromAnotherTenantWithoutSideEffects() {
        var project = applicationService.create(newCommand(), newActor());
        ProjectStageSnapshotDO close = exceptionCloseSnapshot(
                project.id(), 1, LocalDateTime.now().withNano(0));
        close.setTenantId(1L);
        long before = snapshotCount(project.id());

        assertThrows(IllegalArgumentException.class, () -> snapshotRepository.append(close));

        assertEquals(before, snapshotCount(project.id()));
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

    private ProjectStageSnapshotDO exceptionCloseSnapshot(Long projectId, int snapshotNo,
                                                           LocalDateTime operatedAt) {
        ProjectStageSnapshotDO snapshot = commonSnapshot(projectId, snapshotNo,
                "EXCEPTION_CLOSE", operatedAt);
        snapshot.setBusinessBasis("客户书面确认终止");
        snapshot.setLegacyItemsJson("[]");
        snapshot.setGuardSnapshotJson("{}");
        snapshot.setTreeVersion(1L);
        snapshot.setProviderFactsJson("{}");
        return snapshot;
    }

    private ProjectStageSnapshotDO reopenSnapshot(Long projectId, int snapshotNo,
                                                   Long relatedSnapshotId, LocalDateTime operatedAt) {
        ProjectStageSnapshotDO snapshot = commonSnapshot(projectId, snapshotNo, "REOPEN", operatedAt);
        snapshot.setRelatedSnapshotId(relatedSnapshotId);
        return snapshot;
    }

    private ProjectStageSnapshotDO commonSnapshot(Long projectId, int snapshotNo, String operationType,
                                                   LocalDateTime operatedAt) {
        ProjectStageSnapshotDO snapshot = new ProjectStageSnapshotDO();
        snapshot.setTenantId(0L);
        snapshot.setProjectId(projectId);
        snapshot.setStageCode("S0");
        snapshot.setSnapshotNo(snapshotNo);
        snapshot.setOperationType(operationType);
        snapshot.setBeforeStage("S3");
        snapshot.setAfterStage("S0");
        snapshot.setBeforeLifecycleStatus("ACTIVE");
        snapshot.setAfterLifecycleStatus("EXCEPTION_CLOSED");
        snapshot.setBeforeAssignmentStatus("ASSIGNED");
        snapshot.setAfterAssignmentStatus("UNASSIGNED");
        snapshot.setReasonCode("CONFIGURED_REASON");
        snapshot.setReasonDetail("真实MySQL治理验证");
        snapshot.setOperationId("fproj006-it-" + projectId + "-" + snapshotNo);
        snapshot.setOperatorUserId(9_900_001L);
        snapshot.setOperatedAt(operatedAt);
        return snapshot;
    }

    private PageParam page(int pageNo, int pageSize) {
        PageParam page = new PageParam();
        page.setPageNo(pageNo);
        page.setPageSize(pageSize);
        return page;
    }

    private <T> T inTenantTransaction(java.util.concurrent.Callable<T> action) {
        TenantContextHolder.setTenantId(0L);
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                try {
                    return action.call();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void cleanGovernanceSnapshots() {
        jdbcTemplate.update("DELETE FROM proj_project_stage_snapshot WHERE operation_id LIKE 'fproj006-it-%'");
    }

    private long snapshotCount(Long projectId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_stage_snapshot WHERE project_id=?", Long.class, projectId);
    }
}
