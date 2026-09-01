package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.repository.projectgovernance.ProjectStageSnapshotRepository;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageAdvanceApplicationService;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import cn.iocoder.yudao.module.pms.project.service.stagegate.command.ProjectStageAdvanceCommand;
import cn.iocoder.yudao.module.pms.project.service.stagegate.command.ProjectStageAdvanceResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@Import({ProjectStageSnapshotRepository.class,
        ProjectStageAdvanceMySqlIntegrationTest.StageAdvanceTestConfiguration.class})
class ProjectStageAdvanceMySqlIntegrationTest extends ProjectManualCreationMySqlTestSupport {

    private static final Long ACTOR_ID = 9_900_001L;

    @Resource
    private PlatformCommandExecutionApi commandExecutionApi;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private ProjectMasterMapper projectMasterMapper;
    @Resource
    private ProjectStageInstanceMapper stageMapper;
    @Resource
    private ProjectGateInstanceMapper gateMapper;
    @Resource
    private ProjectGateReferenceInstanceMapper referenceMapper;
    @Resource
    private ProjectMemberAssignmentMapper memberMapper;
    @Resource
    private ProjectStageSnapshotMapper snapshotMapper;
    @Resource
    private ProjectStageSnapshotRepository snapshotRepository;

    private ProjectStageGateProviderRegistry providerRegistry;
    private ProjectStageAdvanceApplicationService stageAdvanceService;

    @BeforeEach
    void setUpStageAdvanceService() {
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        when(scopeApi.resolveCurrent(any())).thenAnswer(invocation -> {
            ProjectCurrentScopeQuery query = invocation.getArgument(0);
            return scope(query.anchorProjectId(), 1L);
        });
        when(scopeApi.lockAndRevalidate(any())).thenAnswer(invocation -> {
            ProjectScopeRevalidationQuery query = invocation.getArgument(0);
            return scope(query.anchorProjectId(), query.expectedScopeVersion());
        });
        providerRegistry = mock(ProjectStageGateProviderRegistry.class);
        satisfyEveryOwnerFact();
        stageAdvanceService = new ProjectStageAdvanceApplicationService(commandExecutionApi, permissionApi, scopeApi,
                mock(ProjectParticipantFactApi.class), mock(ProjectStageGateProcessOwnerApi.class), providerRegistry,
                projectMasterMapper, stageMapper, gateMapper, referenceMapper, memberMapper, snapshotMapper,
                snapshotRepository);
    }

    @AfterEach
    void removeStageAdvanceSnapshotsBeforeProjectCleanup() {
        jdbcTemplate.update("DELETE FROM proj_project_stage_snapshot WHERE project_id IN "
                + "(SELECT id FROM proj_project WHERE project_name LIKE ?)", DATA_PREFIX + "%");
    }

    @Test
    void advancesS0ToS1WithOneAtomicFactSet() {
        var created = applicationService.create(newCommand(), newActor());
        assignActorAsProjectManager(created.id());
        int version = projectVersion(created.id());
        long treeVersion = currentTreeVersion(created.id());
        assertEquals("S0", currentStage(created.id()));

        ProjectStageAdvanceResult result = stageAdvanceService.advance(command(
                        created.id(), version, "S0", treeVersion, "success"),
                actor("success"));

        assertEquals("S1", currentStage(created.id()));
        assertEquals(version + 1, projectVersion(created.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM proj_project_stage WHERE project_id=? "
                + "AND stage_code='S0' AND status='DONE'", created.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM proj_project_stage WHERE project_id=? "
                + "AND stage_code='S1' AND status='ACTIVE'", created.id()));
        assertTrue(count("SELECT COUNT(*) FROM proj_project_gate WHERE project_id=? "
                + "AND stage_code='S0' AND gate_type='EXIT'", created.id()) > 0);
        assertEquals(0L, count("SELECT COUNT(*) FROM proj_project_gate WHERE project_id=? "
                + "AND stage_code='S0' AND gate_type='EXIT' AND status<>'PASSED'", created.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM proj_project_stage_snapshot WHERE project_id=? "
                + "AND operation_type='STAGE_ADVANCE'", created.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM plt_operation_audit WHERE aggregate_type='Project' "
                + "AND aggregate_key=? AND operation_code='PROJECT_STAGE_ADVANCE'", String.valueOf(created.id())));
        assertEquals(1L, count("SELECT COUNT(*) FROM plt_outbox_event WHERE aggregate_type='Project' "
                + "AND aggregate_key=? AND event_type='ProjectStageChanged'", String.valueOf(created.id())));
        String outboxEventId = jdbcTemplate.queryForObject("SELECT event_id FROM plt_outbox_event "
                        + "WHERE aggregate_type='Project' AND aggregate_key=? AND event_type='ProjectStageChanged'",
                String.class, String.valueOf(created.id()));
        String payloadEventId = jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(payload,'$.eventId')) "
                        + "FROM plt_outbox_event WHERE event_id=?", String.class, outboxEventId);
        assertEquals(result.operationId(), outboxEventId);
        assertEquals(outboxEventId, payloadEventId);
    }

    @Test
    void ownerFailureAndVersionDriftWriteNothing() {
        var created = applicationService.create(newCommand(), newActor());
        assignActorAsProjectManager(created.id());
        int version = projectVersion(created.id());
        long treeVersion = currentTreeVersion(created.id());
        Map<String, Long> before = advanceFactCounts(created.id());
        doAnswer(invocation -> {
            String providerKey = invocation.getArgument(0);
            ProjectStageGateFactQuery query = invocation.getArgument(1);
            return new ProjectStageGateFact(providerKey, query.refType(), query.refCode(), "IN_PROGRESS", "1",
                    ProjectStageGateOutcome.UNSATISFIED, "OWNER_NOT_READY");
        }).when(providerRegistry).lockAndRevalidate(any(), any());

        assertThrows(ServiceException.class, () -> stageAdvanceService.advance(
                command(created.id(), version, "S0", treeVersion, "owner-failure"), actor("owner-failure")));
        assertEquals(before, advanceFactCounts(created.id()));

        assertThrows(ServiceException.class, () -> stageAdvanceService.advance(
                command(created.id(), version + 1, "S0", treeVersion, "version-drift"), actor("version-drift")));
        assertEquals(before, advanceFactCounts(created.id()));
    }

    private void satisfyEveryOwnerFact() {
        when(providerRegistry.lockAndRevalidate(any(), any())).thenAnswer(invocation -> {
            String providerKey = invocation.getArgument(0);
            ProjectStageGateFactQuery query = invocation.getArgument(1);
            return new ProjectStageGateFact(providerKey, query.refType(), query.refCode(), "DONE", "1",
                    ProjectStageGateOutcome.SATISFIED, null);
        });
    }

    private ProjectStageAdvanceCommand command(Long projectId, int version, String stage, long treeVersion,
                                                String suffix) {
        return new ProjectStageAdvanceCommand(projectId, version, stage, treeVersion,
                KEY_PREFIX + "stage-advance-" + suffix + "-" + UUID.randomUUID(), sha256(UUID.randomUUID().toString()));
    }

    private ProjectStageAdvanceApplicationService.Actor actor(String suffix) {
        return new ProjectStageAdvanceApplicationService.Actor(0L, ACTOR_ID,
                KEY_PREFIX + "stage-correlation-" + suffix + "-" + UUID.randomUUID());
    }

    private ProjectScopeResult scope(Long projectId, Long treeVersion) {
        return new ProjectScopeResult(projectId, treeVersion, Set.of(projectId), Set.of());
    }

    private int projectVersion(Long projectId) {
        return jdbcTemplate.queryForObject("SELECT version FROM proj_project WHERE id=?", Integer.class, projectId);
    }

    private void assignActorAsProjectManager(Long projectId) {
        assertEquals(1, jdbcTemplate.update("UPDATE proj_project SET manager_id=? WHERE id=?", ACTOR_ID, projectId));
    }

    private String currentStage(Long projectId) {
        return jdbcTemplate.queryForObject("SELECT current_stage FROM proj_project WHERE id=?", String.class, projectId);
    }

    private long currentTreeVersion(Long projectId) {
        return jdbcTemplate.queryForObject("SELECT tree_version FROM proj_project_tree_version "
                + "WHERE root_project_id=? AND status='ACTIVE'", Long.class, projectId);
    }

    private Map<String, Long> advanceFactCounts(Long projectId) {
        return Map.of(
                "projectVersion", (long) projectVersion(projectId),
                "s0Active", count("SELECT COUNT(*) FROM proj_project_stage WHERE project_id=? "
                        + "AND stage_code='S0' AND status='ACTIVE'", projectId),
                "s1Pending", count("SELECT COUNT(*) FROM proj_project_stage WHERE project_id=? "
                        + "AND stage_code='S1' AND status='PENDING'", projectId),
                "passedGates", count("SELECT COUNT(*) FROM proj_project_gate WHERE project_id=? "
                        + "AND stage_code='S0' AND gate_type='EXIT' AND status='PASSED'", projectId),
                "snapshots", count("SELECT COUNT(*) FROM proj_project_stage_snapshot WHERE project_id=? "
                        + "AND operation_type='STAGE_ADVANCE'", projectId),
                "audits", count("SELECT COUNT(*) FROM plt_operation_audit WHERE aggregate_type='Project' "
                        + "AND aggregate_key=? AND operation_code='PROJECT_STAGE_ADVANCE'", String.valueOf(projectId)),
                "events", count("SELECT COUNT(*) FROM plt_outbox_event WHERE aggregate_type='Project' "
                        + "AND aggregate_key=? AND event_type='ProjectStageChanged'", String.valueOf(projectId)));
    }

    private long count(String sql, Object argument) {
        return jdbcTemplate.queryForObject(sql, Long.class, argument);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StageAdvanceTestConfiguration {

        @Bean
        ConfigApi configApi() {
            return mock(ConfigApi.class);
        }

        @Bean
        DictDataApi dictDataApi() {
            return mock(DictDataApi.class);
        }

        @Bean
        DynamicFormBusinessInstanceApi dynamicFormBusinessInstanceApi() {
            return mock(DynamicFormBusinessInstanceApi.class);
        }

        @Bean
        ProjectStageGateProviderRegistry projectStageGateProviderRegistry() {
            return mock(ProjectStageGateProviderRegistry.class);
        }

        @Bean
        ProjectStageGateProcessOwnerApi projectStageGateProcessOwnerApi() {
            return mock(ProjectStageGateProcessOwnerApi.class);
        }
    }
}
