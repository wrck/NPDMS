package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphFreezer;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphResolver;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateServiceImpl;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplateDefinitionReferenceAssembler;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationCommands;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@Import({ProjectStageSnapshotRepository.class, ProjectRuntimeGraphFreezer.class,
        ProjectRuntimeGraphResolver.class, ProjectRuntimeRuleEvaluator.class,
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
    @MockitoSpyBean
    private ProjectStageSnapshotRepository snapshotRepository;

    @Resource
    private ProjectStageGateProviderRegistry providerRegistry;
    @Resource
    private ProjectRuntimeGraphResolver runtimeGraphResolver;
    @MockitoSpyBean
    private ProjectTemplateServiceImpl templateService;
    // Only the published template input and external Owners are substituted. Graph persistence,
    // freezing, rule evaluation and target resolution use the production implementations.
    @MockitoBean
    private TemplateDefinitionReferenceAssembler definitionReferenceAssembler;
    @MockitoBean
    private DeliveryConfigurationCommands configurationCommands;
    @MockitoBean
    private cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi normalClosureApi;
    @MockitoBean
    private cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi explicitPermissionApi;
    @MockitoBean
    private cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.AcceptanceActivityInitializationApi acceptanceActivityInitializationApi;
    @MockitoBean
    private cn.iocoder.yudao.module.pms.project.api.satisfaction.SatisfactionQuestionnaireTemplateApi satisfactionQuestionnaireTemplateApi;
    private ProjectStageAdvanceApplicationService stageAdvanceService;

    @BeforeEach
    void setUpStageAdvanceService() {
        var login = new cn.iocoder.yudao.framework.security.core.LoginUser();
        login.setId(ACTOR_ID); login.setTenantId(0L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(login, null, List.of()));
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        when(scopeApi.resolveCurrent(any())).thenAnswer(invocation -> {
            ProjectCurrentScopeQuery query = invocation.getArgument(0);
            return scope(query.anchorProjectId(), 1L);
        });
        when(scopeApi.lockAndRevalidate(any())).thenAnswer(invocation -> {
            ProjectScopeRevalidationQuery query = invocation.getArgument(0);
            return scope(query.anchorProjectId(), query.expectedScopeVersion());
        });
        reset(providerRegistry);
        satisfyEveryOwnerFact();
        doReturn(graphTemplate(false)).when(templateService).getRevisionContent(any(), any());
        stageAdvanceService = new ProjectStageAdvanceApplicationService(commandExecutionApi, permissionApi, scopeApi,
                mock(ProjectParticipantFactApi.class), mock(ProjectStageGateProcessOwnerApi.class), providerRegistry,
                projectMasterMapper, stageMapper, gateMapper, referenceMapper, memberMapper, snapshotMapper,
                snapshotRepository, runtimeGraphResolver);
    }

    @org.junit.jupiter.api.AfterEach
    void clearLoginAndCheckUnusedDependencies() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        org.mockito.Mockito.verifyNoInteractions(configurationCommands, definitionReferenceAssembler,
                acceptanceActivityInitializationApi, satisfactionQuestionnaireTemplateApi);
    }

    @Override
    void cleanOwnedFacts() {
        for (String table : List.of("proj_project_stage_snapshot", "proj_project_stage_transition",
                "proj_project_stage_execution_contract")) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE project_id IN "
                    + "(SELECT id FROM proj_project WHERE project_name LIKE ?)", DATA_PREFIX + "%");
        }
        super.cleanOwnedFacts();
    }

    @Test
    void advancesS0ToS4ByFrozenEdgeWithOneAtomicFactSet() {
        var created = applicationService.create(newCommand(), newActor());
        assignPrimaryManagers(created.id());
        int version = projectVersion(created.id());
        long treeVersion = currentTreeVersion(created.id());
        assertEquals("S0", currentStage(created.id()));

        ProjectStageAdvanceResult result = stageAdvanceService.advance(command(
                        created.id(), version, "S0", treeVersion, "success"),
                actor("success"));

        assertEquals("S4", currentStage(created.id()));
        assertEquals("S4", result.afterStage());
        assertEquals(3L, count("SELECT COUNT(*) FROM proj_project_stage_execution_contract WHERE project_id=?", created.id()));
        assertEquals(2L, count("SELECT COUNT(*) FROM proj_project_stage_transition WHERE project_id=?", created.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM proj_project_stage WHERE project_id=? "
                + "AND stage_code='S6' AND sort_order=1 AND status='PENDING'", created.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM proj_project_gate WHERE project_id=? "
                + "AND stage_code='S4' AND gate_type='ENTRY' AND status='PASSED'", created.id()));
        assertEquals(version + 1, projectVersion(created.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM proj_project_stage WHERE project_id=? "
                + "AND stage_code='S0' AND status='DONE'", created.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM proj_project_stage WHERE project_id=? "
                + "AND stage_code='S4' AND status='ACTIVE'", created.id()));
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
    void s0WithoutTasksCannotAdvanceUntilBothRealPrimaryResponsibilitiesExist() {
        var created = applicationService.create(newCommand(), newActor());
        assignActorAsProjectManager(created.id());
        assertEquals(0L, count("SELECT COUNT(*) FROM proj_project_task WHERE project_id=? AND stage_code='S0'", created.id()));
        // Deliberately stale projection: ASSIGNED alone must never replace the missing SM fact.
        jdbcTemplate.update("UPDATE proj_project SET assignment_status='ASSIGNED' WHERE id=?", created.id());
        var before = advanceFactCounts(created.id());
        var failure = assertThrows(ServiceException.class, () -> stageAdvanceService.advance(
                command(created.id(), projectVersion(created.id()), "S0", currentTreeVersion(created.id()), "missing-sm"), actor("missing-sm")));
        assertTrue(failure.getMessage().contains("S0_PRIMARY_SERVICE_MANAGER_REQUIRED"));
        assertEquals(before, advanceFactCounts(created.id()));
        insertResponsibility(created.id(), ACTOR_ID + 1, "SERVICE_MANAGER_L1");
        var result = stageAdvanceService.advance(command(created.id(), projectVersion(created.id()), "S0",
                currentTreeVersion(created.id()), "both-managers"), actor("both-managers"));
        assertEquals("S4", result.afterStage());
        assertEquals("S4", currentStage(created.id()));
        assertEquals(0L, count("SELECT COUNT(*) FROM proj_project_task WHERE project_id=? AND stage_code='S0'", created.id()));
    }

    @Test
    void ownerFailureAndVersionDriftWriteNothing() {
        var created = applicationService.create(newCommand(), newActor());
        assignPrimaryManagers(created.id());
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

    @Test
    void snapshotFailureRollsBackStageGateAndProjectUpdates() {
        currentCompletionEntryTemplate();
        var created = applicationService.create(newCommand(), newActor());
        assignPrimaryManagers(created.id());
        int version = projectVersion(created.id());
        var before = advanceFactCounts(created.id());
        org.mockito.Mockito.doThrow(new IllegalStateException("snapshot unavailable"))
                .when(snapshotRepository).append(any());

        assertThrows(IllegalStateException.class, () -> stageAdvanceService.advance(
                command(created.id(), version, "S0", currentTreeVersion(created.id()), "snapshot-failure"),
                actor("snapshot-failure")));

        assertEquals("S0", currentStage(created.id()));
        assertEquals(version, projectVersion(created.id()));
        assertEquals(before, advanceFactCounts(created.id()));
        assertEquals(0L, count("SELECT COUNT(*) FROM proj_project_gate WHERE project_id=? AND status='PASSED'", created.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM proj_project_stage WHERE project_id=? AND stage_code='S0' AND status='ACTIVE'", created.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM proj_project_stage WHERE project_id=? AND stage_code='S4' AND status='PENDING'", created.id()));
    }

    @Test
    void currentStageCompletionEntryAdvancesAtomicallyAndRetainsEvaluationEvidence() {
        currentCompletionEntryTemplate();
        var created = applicationService.create(newCommand(), newActor());
        assignPrimaryManagers(created.id());
        int version = projectVersion(created.id());
        assertEquals("S0", currentStage(created.id()));
        var result = stageAdvanceService.advance(command(created.id(), version, "S0", currentTreeVersion(created.id()), "current-completion"),
                actor("current-completion"));
        assertEquals("S4", result.afterStage());
        assertEquals(version + 1, projectVersion(created.id()));
        assertTrue(result.gateEvaluationSummary().contains("COMPLETION_VERIFIED"));
        assertEquals(1L, count("SELECT COUNT(*) FROM proj_project_stage WHERE project_id=? AND stage_code='S0' AND status='DONE'", created.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM proj_project_stage_snapshot WHERE project_id=? AND operation_type='STAGE_ADVANCE'", created.id()));
    }

    private void currentCompletionEntryTemplate() {
        var template = graphTemplate(false);
        template.getGates().get(1).getReferences().getFirst().setRefCode("S0_COMPLETED");
        doReturn(template).when(templateService).getRevisionContent(any(), any());
        // Persisted S0 is still ACTIVE. Only the transition-scoped completion fact may satisfy this entry.
        doReturn(new ProjectStageGateFact("PROJ_STATE", "STATE", "S0_COMPLETED", "ACTIVE", "0",
                ProjectStageGateOutcome.UNSATISFIED, "STATE_NOT_DONE")).when(providerRegistry)
                .lockAndRevalidate(any(), argThat(query -> query != null && "S0_COMPLETED".equals(query.refCode())));
    }

    @Test
    void terminalDoesNotCloseProjectOrPublishAnotherAdvance() {
        var created = applicationService.create(newCommand(), newActor());
        assignPrimaryManagers(created.id());
        long treeVersion = currentTreeVersion(created.id());
        stageAdvanceService.advance(command(created.id(), projectVersion(created.id()), "S0", treeVersion, "to-s4"), actor("to-s4"));
        stageAdvanceService.advance(command(created.id(), projectVersion(created.id()), "S4", treeVersion, "to-s6"), actor("to-s6"));
        Map<String, Long> before = advanceFactCounts(created.id());

        ServiceException failure = assertThrows(ServiceException.class, () -> stageAdvanceService.advance(
                command(created.id(), projectVersion(created.id()), "S6", treeVersion, "terminal"), actor("terminal")));

        assertTrue(failure.getMessage().contains("TERMINAL"));
        assertEquals(before, advanceFactCounts(created.id()));
        assertEquals("S6", currentStage(created.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM proj_project WHERE id=? AND lifecycle_status='ACTIVE' "
                + "AND project_close_time IS NULL", created.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM proj_project_stage WHERE project_id=? "
                + "AND stage_code='S6' AND status='ACTIVE'", created.id()));
    }

    @Test
    void unknownConditionBlocksDefaultWithoutWritingFacts() {
        doReturn(graphTemplate(true)).when(templateService).getRevisionContent(any(), any());
        var created = applicationService.create(newCommand(), newActor());
        assignPrimaryManagers(created.id());
        Map<String, Long> before = advanceFactCounts(created.id());

        ServiceException failure = assertThrows(ServiceException.class, () -> stageAdvanceService.advance(
                command(created.id(), projectVersion(created.id()), "S0", currentTreeVersion(created.id()), "unknown"), actor("unknown")));

        assertTrue(failure.getMessage().contains("GRAPH_UNAVAILABLE"));
        assertEquals(before, advanceFactCounts(created.id()));
        assertEquals("S0", currentStage(created.id()));
    }

    @Test
    void entryOwnerBlocksAdvanceUsingTargetStageIdentity() {
        var created = applicationService.create(newCommand(), newActor());
        assignPrimaryManagers(created.id());
        Map<String, Long> before = advanceFactCounts(created.id());
        doReturn(new ProjectStageGateFact("PROJ_STATE", "STATE", "ENTRY-S4", "IN_PROGRESS", "1",
                ProjectStageGateOutcome.UNSATISFIED, "ENTRY_NOT_READY")).when(providerRegistry)
                .lockAndRevalidate(any(), argThat(query -> query != null && "ENTRY-S4".equals(query.refCode())));

        ServiceException failure = assertThrows(ServiceException.class, () -> stageAdvanceService.advance(
                command(created.id(), projectVersion(created.id()), "S0", currentTreeVersion(created.id()), "entry"), actor("entry")));

        assertTrue(failure.getMessage().contains("ENTRY_NOT_READY"));
        assertEquals(before, advanceFactCounts(created.id()));
        verify(providerRegistry).lockAndRevalidate(any(), argThat(query -> "ENTRY-S4".equals(query.refCode())
                && "S4".equals(query.currentStageCode())));
    }

    @Test
    void historicalProjectWithoutFrozenGraphIsRejected() {
        var created = applicationService.create(newCommand(), newActor());
        assignPrimaryManagers(created.id());
        // This test owns these rows: represent a pre-graph project, never backfill a sort-based edge.
        jdbcTemplate.update("DELETE FROM proj_project_stage_transition WHERE project_id=?", created.id());
        jdbcTemplate.update("DELETE FROM proj_project_stage_execution_contract WHERE project_id=?", created.id());
        jdbcTemplate.update("UPDATE proj_project_stage SET graph_version=NULL WHERE project_id=?", created.id());
        Map<String, Long> before = advanceFactCounts(created.id());

        ServiceException failure = assertThrows(ServiceException.class, () -> stageAdvanceService.advance(
                command(created.id(), projectVersion(created.id()), "S0", currentTreeVersion(created.id()), "history"), actor("history")));

        assertTrue(failure.getMessage().contains("GRAPH_NOT_FROZEN"));
        assertEquals(before, advanceFactCounts(created.id()));
    }

    private TemplateDefinitionContent graphTemplate(boolean unknownCondition) {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        List<Map<String, Object>> definitions = new ArrayList<>();
        definitions.add(definition(100L, "COMPLETION_RULE", Map.of("predicate", "STAGE_NATIVE_STATUS",
                "parameters", Map.of("requiredStatus", "DONE"))));
        definitions.add(definition(101L, "COMPLETION_RULE", Map.of("predicate", "PROCESS",
                "parameters", Map.of("refCode", "UNKNOWN-PROCESS"))));
        definitions.add(definition(102L, "WORK_BINDING", Map.of("bindingType", "STAGE_NATIVE")));
        definitions.add(definition(103L, "PERMISSION_POLICY", Map.of()));
        String[] codes = {"S0", "S4", "S6"};
        int[] sorts = {0, 99, 1}; // A sort-based implementation would incorrectly choose S6.
        for (int i = 0; i < codes.length; i++) {
            var stage = new TemplateDefinitionContent.StageDef();
            stage.setStageCode(codes[i]); stage.setName(codes[i]); stage.setSortOrder(sorts[i]);
            stage.setStart(i == 0); stage.setTerminal(i == 2); stage.setDefinitionRevisionId(200L + i);
            stage.setWorkBindingRevisionId(102L); stage.setPermissionPolicyRevisionId(103L); stage.setCompletionRuleRevisionId(100L);
            content.getStages().add(stage);
            definitions.add(definition(200L + i, "STAGE", Map.of("stageCode", codes[i])));
        }
        content.getTransitions().add(transition(301L, "S0", "S4", unknownCondition ? 101L : null, false));
        content.getTransitions().add(transition(302L, "S4", "S6", null, false));
        if (unknownCondition) content.getTransitions().add(transition(303L, "S0", "S6", null, true));
        content.getGates().add(gate("S0", "EXIT"));
        content.getGates().add(gate("S4", "ENTRY"));
        content.setDefinitionSnapshot(JsonUtils.parseObject(JsonUtils.toJsonString(definitions), JsonNode.class));
        return content;
    }

    private static Map<String, Object> definition(Long id, String kind, Map<String, ?> payload) {
        return Map.of("definition", Map.of("id", id, "definitionKind", kind, "schemaVersion", 1, "payload", payload));
    }

    private static TemplateDefinitionContent.TransitionDef transition(Long id, String from, String to, Long rule, boolean fallback) {
        var edge = new TemplateDefinitionContent.TransitionDef();
        edge.setId(id); edge.setTransitionCode("E-" + from + "-" + to); edge.setRevisionNo(1L);
        edge.setFromStageCode(from); edge.setToStageCode(to); edge.setConditionRuleRevisionId(rule);
        edge.setPriority(1); edge.setDefaultBranch(fallback);
        return edge;
    }

    private static TemplateDefinitionContent.GateDef gate(String stage, String type) {
        var gate = new TemplateDefinitionContent.GateDef();
        gate.setGateCode(type + "-" + stage); gate.setName(gate.getGateCode()); gate.setStageCode(stage); gate.setGateType(type);
        var ref = new TemplateDefinitionContent.GateRef(); ref.setRefType("STATE"); ref.setRefCode(gate.getGateCode());
        gate.setReferences(List.of(ref));
        return gate;
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

    private void assignPrimaryManagers(Long projectId) {
        assignActorAsProjectManager(projectId);
        insertResponsibility(projectId, ACTOR_ID + 1, "SERVICE_MANAGER_L1");
        assertEquals(1, jdbcTemplate.update("UPDATE proj_project SET assignment_status='ASSIGNED' WHERE id=?", projectId));
    }

    private void assignActorAsProjectManager(Long projectId) {
        assertEquals(1, jdbcTemplate.update("UPDATE proj_project SET manager_id=? WHERE id=?", ACTOR_ID, projectId));
        insertResponsibility(projectId, ACTOR_ID, "PROJECT_MANAGER");
    }

    private void insertResponsibility(Long projectId, Long userId, String role) {
        ProjectMemberAssignmentDO member = new ProjectMemberAssignmentDO();
        member.setTenantId(0L); member.setProjectId(projectId); member.setUserId(userId);
        member.setMemberRole(role); member.setAssignmentType("PRIMARY");
        member.setStatus("ACTIVE"); member.setEffectiveFrom(LocalDateTime.now().minusMinutes(1));
        member.setVersion(0);
        assertEquals(1, memberMapper.insert(member));
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
                "s4Pending", count("SELECT COUNT(*) FROM proj_project_stage WHERE project_id=? "
                        + "AND stage_code='S4' AND status='PENDING'", projectId),
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
