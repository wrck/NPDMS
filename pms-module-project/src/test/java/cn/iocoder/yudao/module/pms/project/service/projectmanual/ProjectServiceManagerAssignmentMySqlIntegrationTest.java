package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class ProjectServiceManagerAssignmentMySqlIntegrationTest
        extends ProjectManualCreationMySqlTestSupport {

    @Resource
    private ProjectManualCreationService projectService;
    @Resource
    private PlatformOutboxDeliveryApi outboxDeliveryApi;

    @Test
    void sameVersionHasSingleWinnerAndPrimaryIntervalsMeetAtOneBoundary() throws Exception {
        var project = applicationService.create(newCommand(), newActor());
        insertActiveProjectManager(project.id());
        int initialVersion = currentProjectVersion(project.id());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AssignServiceManagerResult>> futures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (long managerId : List.of(8_100_001L, 8_100_002L)) {
                futures.add(executor.submit(() -> {
                    TenantContextHolder.setTenantId(0L);
                    try {
                        ready.countDown();
                        start.await();
                        return projectService.assignServiceManager(command(
                                project.id(), initialVersion, managerId));
                    } finally {
                        TenantContextHolder.clear();
                    }
                }));
            }
            ready.await();
            start.countDown();
        }

        List<AssignServiceManagerResult> successes = new ArrayList<>();
        for (Future<AssignServiceManagerResult> future : futures) {
            try {
                successes.add(future.get());
            } catch (java.util.concurrent.ExecutionException expectedConflict) {
                assertTrue(ProjectManualCreationMySqlTestSupport.hasCauseMessage(
                        expectedConflict, "Project版本冲突"));
            }
        }
        assertEquals(1, successes.size());
        assertEquals("ASSIGNED", successes.getFirst().assignmentStatus());
        assertEquals(initialVersion + 1, jdbcTemplate.queryForObject(
                "SELECT version FROM proj_project WHERE id=?", Integer.class, project.id()));

        AssignServiceManagerResult replacement = projectService.assignServiceManager(
                command(project.id(), initialVersion + 1, 8_100_003L));
        assertEquals("ASSIGNED", replacement.assignmentStatus());
        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_member_assignment WHERE project_id=? "
                        + "AND member_role='SERVICE_MANAGER_L1' AND assignment_type='PRIMARY'",
                Long.class, project.id()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_member_assignment old_row "
                        + "JOIN proj_project_member_assignment new_row "
                        + "ON old_row.project_id=new_row.project_id "
                        + "AND old_row.effective_to=new_row.effective_from "
                        + "WHERE old_row.project_id=? AND old_row.effective_to IS NOT NULL "
                        + "AND new_row.effective_to IS NULL",
                Long.class, project.id()));
    }

    @Test
    void assignmentStatusRequiresBothProjectManagerAndPrimaryServiceManager() {
        var project = applicationService.create(newCommand(), newActor());
        int initialVersion = currentProjectVersion(project.id());

        AssignServiceManagerResult serviceManagerOnly = projectService.assignServiceManager(
                command(project.id(), initialVersion, 8_200_001L));

        assertEquals("UNASSIGNED", serviceManagerOnly.assignmentStatus());
        assertEquals("UNASSIGNED", jdbcTemplate.queryForObject(
                "SELECT assignment_status FROM proj_project WHERE id=?", String.class, project.id()));
    }

    @Test
    void outboxRetryIsDueOnlyAfterCasScheduledTimeAndThenCompletes() {
        var project = applicationService.create(newCommand(), newActor());
        String eventId = "it-fproj005-" + UUID.randomUUID();
        LocalDateTime occurredAt = LocalDateTime.now().minusMinutes(1);
        jdbcTemplate.update("INSERT INTO plt_outbox_event "
                        + "(event_id,event_type,aggregate_type,aggregate_key,payload,status,"
                        + "occurred_at,retry_count,tenant_id) VALUES (?,?,?,?,?,'PENDING',?,0,0)",
                eventId, "ProjectServiceManagerAssigned", "Project", String.valueOf(project.id()),
                "{}", occurredAt);

        assertEquals(1, outboxDeliveryApi.claimDue(
                new PlatformOutboxClaimQuery(LocalDateTime.now(), 10,
                        java.util.Set.of("ProjectServiceManagerAssigned"))).stream()
                .filter(message -> eventId.equals(message.eventId())).count());
        LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(5).withNano(0);
        outboxDeliveryApi.scheduleRetry(eventId, 0, nextRetryTime);
        assertEquals(0, outboxDeliveryApi.claimDue(
                new PlatformOutboxClaimQuery(nextRetryTime.minusSeconds(1), 10,
                        java.util.Set.of("ProjectServiceManagerAssigned"))).stream()
                .filter(message -> eventId.equals(message.eventId())).count());
        assertEquals(1, outboxDeliveryApi.claimDue(
                new PlatformOutboxClaimQuery(nextRetryTime, 10,
                        java.util.Set.of("ProjectServiceManagerAssigned"))).stream()
                .filter(message -> eventId.equals(message.eventId())).count());

        outboxDeliveryApi.markDelivered(eventId, 1);

        assertEquals("DELIVERED", jdbcTemplate.queryForObject(
                "SELECT status FROM plt_outbox_event WHERE event_id=?", String.class, eventId));
        assertEquals(0, outboxDeliveryApi.claimDue(
                new PlatformOutboxClaimQuery(nextRetryTime.plusMinutes(1), 10,
                        java.util.Set.of("ProjectServiceManagerAssigned"))).stream()
                .filter(message -> eventId.equals(message.eventId())).count());
    }

    private AssignServiceManagerCommand command(Long projectId, int expectedVersion, Long managerId) {
        return new AssignServiceManagerCommand(projectId, expectedVersion, "L1", managerId,
                null, "PRIMARY", 1L, "IT-DEPT", "真实MySQL服务经理指派验证", null, null);
    }

    private int currentProjectVersion(Long projectId) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM proj_project WHERE id=?", Integer.class, projectId);
    }

    private void insertActiveProjectManager(Long projectId) {
        jdbcTemplate.update("INSERT INTO proj_project_member_assignment "
                        + "(project_id,user_id,member_role,responsibility,effective_from,status,tenant_id) "
                        + "VALUES (?,?,'PROJECT_MANAGER','项目经理',?,'ACTIVE',0)",
                projectId, 8_000_001L, LocalDateTime.now().minusMinutes(1));
    }
}
