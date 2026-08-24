package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectattribute.query.ProjectTemplateMatchHistoryPageQuery;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeClassificationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeSourceCorrectionService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectTemplateMatchHistoryQueryService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ManualProjectAttributeAdjustmentCommand;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ProjectAttributeAdjustmentResult;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ProjectAttributeSourceCorrectionCommand;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class ProjectAttributeCorrectionMySqlIntegrationTest extends ProjectManualCreationMySqlTestSupport {

    @Resource
    private ProjectAttributeClassificationApplicationService classificationService;
    @Resource
    private ProjectAttributeSourceCorrectionService sourceCorrectionService;
    @Resource
    private ProjectTemplateMatchHistoryQueryService historyQueryService;
    @Resource
    private ProjectTreeScopeService scopeService;

    @Test
    void adjustmentAppendsHistoryWithoutChangingFrozenOrInstantiatedFacts() {
        var created = applicationService.create(newCommand(), newActor());
        activateTreeProjection(created.id());
        allowManage(created.id());
        var before = projectFacts(created.id());
        long beforeHistory = countHistory(created.id());
        long beforeOutbox = countOutbox(created.id());

        var result = classificationService.adjust(command(created.id(), before.version()), actor());

        var after = projectFacts(created.id());
        assertEquals(before.version() + 1, result.version());
        assertEquals(before.signingMethod() + "_ADJUSTED", after.signingMethod());
        assertEquals(before.templateId(), after.templateId());
        assertEquals(before.revisionNo(), after.revisionNo());
        assertEquals(before.stageCount(), after.stageCount());
        assertEquals(before.taskCount(), after.taskCount());
        assertEquals(before.milestoneCount(), after.milestoneCount());
        assertEquals(before.deliverableCount(), after.deliverableCount());
        assertEquals(before.gateCount(), after.gateCount());
        assertEquals(before.gateReferenceCount(), after.gateReferenceCount());
        assertEquals(beforeHistory + 1, countHistory(created.id()));
        assertEquals(beforeOutbox, countOutbox(created.id()));
        var historyPage = historyQueryService.page(new ProjectTemplateMatchHistoryPageQuery(
                        0L, created.id(), new PageParam(), null, null, null,
                        null, null, "occurredAt", false),
                new ProjectTemplateMatchHistoryQueryService.Actor(0L, 9_900_001L));
        assertEquals(beforeHistory + 1, historyPage.getTotal());
        assertEquals(beforeHistory + 1, historyPage.getList().size());
    }

    @Test
    void trustedSourceCorrectionChangesOnlyCrmOwnerFieldsWithoutReinstantiation() {
        var created = applicationService.create(newCommand(), newActor());
        var before = projectFacts(created.id());
        long beforeHistory = countHistory(created.id());
        long beforeOutbox = countOutbox(created.id());

        var result = sourceCorrectionService.correct(sourceCommand(created.id(), before.version()),
                0L, KEY_PREFIX + "source-trace-" + UUID.randomUUID());

        var after = projectFacts(created.id());
        assertEquals(before.version() + 1, result.version());
        assertEquals(before.signingMethod() + "_CRM", after.signingMethod());
        assertEquals(before.implementationMode() + "_CRM", after.implementationMode());
        assertEquals("A", after.majorProjectLevel());
        assertEquals(before.projectCategory(), after.projectCategory());
        assertEquals(before.templateId(), after.templateId());
        assertEquals(before.revisionNo(), after.revisionNo());
        assertEquals(before.stageCount(), after.stageCount());
        assertEquals(before.taskCount(), after.taskCount());
        assertEquals(before.milestoneCount(), after.milestoneCount());
        assertEquals(before.deliverableCount(), after.deliverableCount());
        assertEquals(before.gateCount(), after.gateCount());
        assertEquals(before.gateReferenceCount(), after.gateReferenceCount());
        assertEquals(beforeHistory + 1, countHistory(created.id()));
        assertEquals(beforeOutbox, countOutbox(created.id()));
    }

    @Test
    void concurrentAdjustmentsWithSameVersionCommitExactlyOneHistory() throws Exception {
        var created = applicationService.create(newCommand(), newActor());
        activateTreeProjection(created.id());
        allowManage(created.id());
        var before = projectFacts(created.id());
        long beforeHistory = countHistory(created.id());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        Callable<Object> adjustment = () -> {
            ready.countDown();
            start.await();
            try {
                return classificationService.adjust(command(created.id(), before.version()), actor());
            } catch (RuntimeException ex) {
                return ex;
            }
        };
        try {
            var first = executor.submit(adjustment);
            var second = executor.submit(adjustment);
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            var outcomes = java.util.List.of(first.get(), second.get());

            assertEquals(1, outcomes.stream()
                    .filter(ProjectAttributeAdjustmentResult.class::isInstance).count());
            assertEquals(1, outcomes.stream()
                    .filter(cn.iocoder.yudao.framework.common.exception.ServiceException.class::isInstance).count());
            assertEquals(before.version() + 1, projectFacts(created.id()).version());
            assertEquals(beforeHistory + 1, countHistory(created.id()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void historyInsertFailureRollsBackAttributeVersion() {
        var created = applicationService.create(newCommand(), newActor());
        allowManage(created.id());
        var before = projectFacts(created.id());
        long beforeHistory = countHistory(created.id());
        installFailureTrigger(FailurePoint.MATCH_HISTORY);

        assertThrows(RuntimeException.class,
                () -> classificationService.adjust(command(created.id(), before.version()), actor()));

        dropFailureTrigger();
        assertEquals(before, projectFacts(created.id()));
        assertEquals(beforeHistory, countHistory(created.id()));
    }

    private void allowManage(Long projectId) {
        when(scopeService.resolve(any(ProjectScopeQuery.class))).thenReturn(
                new ProjectTreeScopeService.ProjectTreeScope(
                        projectId, 1L, Set.of(projectId), Set.of(), Set.of()));
    }

    private void activateTreeProjection(Long projectId) {
        jdbcTemplate.update("""
                INSERT INTO proj_project_tree_version
                    (id, root_project_id, tree_version, status, change_batch_id,
                     node_count, path_count, activated_at, tenant_id)
                VALUES (?, ?, 1, 'ACTIVE', ?, 1, 1, NOW(), 0)
                """, projectId, projectId, KEY_PREFIX + "tree-" + projectId);
    }

    private ManualProjectAttributeAdjustmentCommand command(Long projectId, Integer version) {
        var values = jdbcTemplate.queryForMap(
                "SELECT signing_method, project_category, implementation_mode FROM proj_project WHERE id = ?",
                projectId);
        return new ManualProjectAttributeAdjustmentCommand(projectId, version,
                values.get("signing_method") + "_ADJUSTED", String.valueOf(values.get("project_category")),
                String.valueOf(values.get("implementation_mode")), "MySQL属性影响验证",
                KEY_PREFIX + UUID.randomUUID(), sha256(UUID.randomUUID().toString()));
    }

    private ProjectAttributeSourceCorrectionCommand sourceCommand(Long projectId, Integer version) {
        var values = jdbcTemplate.queryForMap(
                "SELECT signing_method, implementation_mode FROM proj_project WHERE id = ?", projectId);
        String eventId = KEY_PREFIX + "crm-event-" + UUID.randomUUID();
        return new ProjectAttributeSourceCorrectionCommand(projectId, version,
                values.get("signing_method") + "_CRM", values.get("implementation_mode") + "_CRM", "A",
                "CRM", "CRM", "CRM-PROJECT-" + projectId, eventId, "v2", LocalDateTime.now(),
                sha256(eventId), "crm-map-v2", "CRM来源修正", KEY_PREFIX + UUID.randomUUID(),
                sha256(UUID.randomUUID().toString()), "int-crm-sync");
    }

    private ProjectAttributeClassificationApplicationService.Actor actor() {
        return new ProjectAttributeClassificationApplicationService.Actor(
                0L, 9_900_001L, KEY_PREFIX + "classify-" + UUID.randomUUID());
    }

    private ProjectFacts projectFacts(Long projectId) {
        return jdbcTemplate.queryForObject("""
                SELECT signing_method, project_category, implementation_mode, major_project_level,
                       lifecycle_template_id, lifecycle_template_revision_no, version,
                       (SELECT COUNT(*) FROM proj_project_stage WHERE project_id = p.id) stage_count,
                       (SELECT COUNT(*) FROM proj_project_task WHERE project_id = p.id) task_count,
                       (SELECT COUNT(*) FROM proj_project_milestone WHERE project_id = p.id) milestone_count,
                       (SELECT COUNT(*) FROM acc_project_deliverable WHERE project_id = p.id) deliverable_count,
                       (SELECT COUNT(*) FROM proj_project_gate WHERE project_id = p.id) gate_count,
                       (SELECT COUNT(*) FROM proj_project_gate_reference r
                         JOIN proj_project_gate g ON g.id = r.gate_id WHERE g.project_id = p.id) gate_reference_count
                FROM proj_project p WHERE id = ?
                """, (rs, rowNum) -> new ProjectFacts(
                rs.getString("signing_method"), rs.getString("project_category"),
                rs.getString("implementation_mode"), rs.getString("major_project_level"),
                rs.getLong("lifecycle_template_id"), rs.getInt("lifecycle_template_revision_no"),
                rs.getInt("version"), rs.getLong("stage_count"), rs.getLong("task_count"),
                rs.getLong("milestone_count"), rs.getLong("deliverable_count"),
                rs.getLong("gate_count"), rs.getLong("gate_reference_count")), projectId);
    }

    private long countHistory(Long projectId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_template_match_history WHERE project_id = ?",
                Long.class, projectId);
    }

    private long countOutbox(Long projectId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_outbox_event WHERE aggregate_type = 'Project' AND aggregate_key = ?",
                Long.class, String.valueOf(projectId));
    }

    private record ProjectFacts(String signingMethod, String projectCategory, String implementationMode,
                                String majorProjectLevel, Long templateId, Integer revisionNo, Integer version,
                                Long stageCount, Long taskCount, Long milestoneCount, Long deliverableCount,
                                Long gateCount, Long gateReferenceCount) {
    }
}
