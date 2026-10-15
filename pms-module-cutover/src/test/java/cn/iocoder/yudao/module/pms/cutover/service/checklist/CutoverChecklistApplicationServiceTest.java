package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskStageHistoryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemResultMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.AddCustomItemCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.GenerateChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RematchChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RemoveCustomItemCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RequestCollectionCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SelectManualResultCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SubmitChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverCollectionPort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistItemCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistView;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CutoverChecklistApplicationServiceTest {

    @Test
    void completesP3WithDirectCustomAndControlledManualEvidence() {
        Fixture fixture = fixture();

        ChecklistCommandResult generated = fixture.service.generate(GenerateChecklistCommand.builder()
                .tenantId(1L)
                .actorId(8L)
                .taskId(1000L)
                .expectedTaskVersion(2L)
                .expectedAssessmentVersion(1L)
                .expectedProjectScopeVersion(7L)
                .matchingInput(new cn.iocoder.yudao.module.pms.cutover.service.checklist.command.CutoverChecklistMatchInput("配置变更", "普通双机", List.of("ROUTER")))
                .selectedConflictDefinitions(Map.of())
                .idempotencyKey("generate-1")
                .correlationId("corr-generate-1").build());
        ChecklistItemCommandResult transientCustom = fixture.service.addCustomItem(new AddCustomItemCommand(1L,
                8L,
                1000L,
                2L,
                generated.checklistId(),
                0L,
                7L,
                "TEXT",
                "临时核查项",
                null,
                "TEXT",
                "{\"type\":\"string\"}",
                false,
                null,
                cn.iocoder.yudao.module.pms.cutover.support.CutoverTestCommands.intent()));
        ChecklistItemCommandResult removed = fixture.service.removeCustomItem(new RemoveCustomItemCommand(1L,
                8L,
                1000L,
                2L,
                generated.checklistId(),
                transientCustom.checklistVersion(),
                7L,
                transientCustom.stableItemKey(),
                cn.iocoder.yudao.module.pms.cutover.support.CutoverTestCommands.intent()));
        ChecklistCommandResult saved = fixture.service.save(new SaveChecklistCommand(1L,
                8L,
                1000L,
                2L,
                generated.checklistId(),
                removed.checklistVersion(),
                7L,
                List.of(new SaveChecklistCommand.DirectAnswer("SYS-IP", "{\"value\":\"10.0.0.1\"}")),
                cn.iocoder.yudao.module.pms.cutover.support.CutoverTestCommands.intent()));
        ChecklistItemCommandResult custom = fixture.service.addCustomItem(new AddCustomItemCommand(1L,
                8L,
                1000L,
                2L,
                generated.checklistId(),
                saved.checklistFactVersion(),
                7L,
                "TEXT",
                "现场补充项",
                null,
                "TEXT",
                "{\"type\":\"string\"}",
                true,
                "{\"value\":\"已核对\"}",
                cn.iocoder.yudao.module.pms.cutover.support.CutoverTestCommands.intent()));
        CutoverChecklistFilePort.FileFactVersion fileVersion =
                new CutoverChecklistFilePort.FileFactVersion(3, 4, 5);
        ChecklistItemCommandResult manual = fixture.service.selectManual(new SelectManualResultCommand(1L,
                8L,
                1000L,
                2L,
                generated.checklistId(),
                custom.checklistVersion(),
                7L,
                "SYS-MATERIAL",
                CutoverChecklistFilePort.FileHandle.builder()
                        .artifactId(90L)
                        .versionNo(2)
                        .referenceKey("ref-90")
                        .fileFactVersion(fileVersion)
                        .scopeVersion(7L)
                        .build(),
                "现场截图",
                cn.iocoder.yudao.module.pms.cutover.support.CutoverTestCommands.intent()));
        CutoverChecklistView beforeRematch = fixture.service.getView(new cn.iocoder.yudao.module.pms.cutover.service.checklist.query.CutoverChecklistReadQuery(new cn.iocoder.yudao.module.pms.cutover.service.checklist.context.CutoverChecklistContext(1L, 8L, "corr-read"), 1000L, null));
        ChecklistCommandResult rematched = fixture.service.rematch(RematchChecklistCommand.builder()
                .tenantId(1L)
                .actorId(8L)
                .taskId(1000L)
                .expectedTaskVersion(2L)
                .expectedAssessmentVersion(1L)
                .checklistId(generated.checklistId())
                .expectedChecklistVersion(manual.checklistVersion())
                .expectedInputSnapshotHash(beforeRematch.inputSnapshotHash())
                .expectedProjectScopeVersion(7L)
                .matchingInput(new cn.iocoder.yudao.module.pms.cutover.service.checklist.command.CutoverChecklistMatchInput("配置变更", "普通双机", List.of("ROUTER")))
                .selectedConflictDefinitions(Map.of())
                .idempotencyKey("rematch-1")
                .correlationId("corr-rematch-1").build());
        var accepted = fixture.service.requestCollection(new RequestCollectionCommand(
                1L, 8L, 1000L, 2L, generated.checklistId(), rematched.checklistFactVersion(), 7L,
                "SYS-COLLECT", 400L, 700L, "collect-1", "corr-collect-1"));
        var collection = fixture.service.requestCollection(new RequestCollectionCommand(
                1L, 8L, 1000L, 2L, generated.checklistId(), accepted.checklistVersion(), 7L,
                "SYS-COLLECT", 400L, 700L, "collect-refresh-1", "corr-collect-refresh-1"));
        var incomplete = org.junit.jupiter.api.Assertions.assertThrows(CutoverChecklistException.class,
                () -> fixture.service.submit(new SubmitChecklistCommand(1L, 8L, 1000L, 2L, 1L,
                        generated.checklistId(), collection.checklistVersion(), 7L, "before-analysis", "corr-analysis")));
        assertEquals(CutoverChecklistException.Code.STATE_CONFLICT, incomplete.getCode());
        var analyzed = fixture.service.save(new SaveChecklistCommand(1L, 8L, 1000L, 2L,
                generated.checklistId(), collection.checklistVersion(), 7L,
                List.of(new SaveChecklistCommand.DirectAnswer("SYS-COLLECT", "{\"value\":{\"status\":\"ok\"}}")),
                cn.iocoder.yudao.module.pms.cutover.support.CutoverTestCommands.intent()));
        CutoverChecklistView view = fixture.service.getView(new cn.iocoder.yudao.module.pms.cutover.service.checklist.query.CutoverChecklistReadQuery(new cn.iocoder.yudao.module.pms.cutover.service.checklist.context.CutoverChecklistContext(1L, 8L, "corr-read"), 1000L, null));
        ChecklistCommandResult submitted = fixture.service.submit(new SubmitChecklistCommand(
                1L, 8L, 1000L, 2L, 1L, generated.checklistId(), analyzed.checklistFactVersion(), 7L,
                "submit-1", "corr-submit-1"));
        ChecklistCommandResult replayed = fixture.service.submit(new SubmitChecklistCommand(
                1L, 8L, 1000L, 2L, 1L, generated.checklistId(), analyzed.checklistFactVersion(), 7L,
                "submit-1", "corr-submit-1"));

        assertEquals("DRAFT", generated.checklistStatus());
        assertEquals(3, saved.checklistFactVersion());
        assertEquals(4, custom.checklistVersion());
        assertEquals(1, manual.resultVersion());
        assertEquals(2, rematched.checklistVersion());
        assertEquals(5, view.items().size());
        assertEquals("MANUAL", view.items().stream().filter(item -> "SYS-MATERIAL".equals(item.stableItemKey()))
                .findFirst().orElseThrow().currentResult().resultSourceCode());
        assertEquals("现场截图", view.items().stream().filter(item -> "SYS-MATERIAL".equals(item.stableItemKey()))
                .findFirst().orElseThrow().currentResult().factDescription());
        assertEquals(9100L, view.items().stream().filter(item -> "SYS-COLLECT".equals(item.stableItemKey()))
                .findFirst().orElseThrow().currentResult().collectionResultReferenceId());
        assertEquals("SUBMITTED", submitted.checklistStatus());
        assertEquals("P4", submitted.taskStage());
        assertEquals("TASK_OVERVIEW", submitted.navigationDecision().target());
        assertTrue(replayed.replayed());
        assertEquals("TASK_OVERVIEW", replayed.navigationDecision().target());
        assertEquals("PLAN_DRAFTING", fixture.task.get().getTaskStatus());
        assertEquals(List.of("P3_CHECKLIST_SUBMITTED"), fixture.history.stream()
                .map(CutoverTaskStageHistoryDO::getTriggerType).toList());
        assertEquals("ACCEPTED", accepted.technicalStatus());
        assertEquals("COMPLETED", collection.technicalStatus());
        assertEquals(5, fixture.platform.facts.size());
        PlatformCommandExecutionApi.SuccessFacts submitFacts = fixture.platform.facts.stream()
                .filter(fact -> "CUTOVER_CHECKLIST_SUBMIT".equals(fact.operationCode()))
                .findFirst().orElseThrow();
        assertFalse(submitFacts.detailSnapshot().contains("navigationDecision"));
        assertEquals(List.of("CutoverChecklistItemResultLinked", "CutoverChecklistItemResultLinked"),
                fixture.platform.facts.stream().flatMap(fact -> fact.businessEvents().stream())
                        .map(PlatformCommandExecutionApi.BusinessEvent::eventType).toList());
        verify(fixture.collectionPort, times(1)).request(any());
        verify(fixture.collectionPort, times(2)).inspect(any());
        assertTrue(fixture.results.stream().filter(row -> "DIRECT".equals(row.getResultSourceCode()))
                .allMatch(row -> row.getSelectionEndedAt() == null));
    }

    @Test
    void rejectsManualReplacementOfDirectAndCollectionWithoutFailure() {
        var fixture = fixture(); var generated = generate(fixture);
        for (var key : List.of("SYS-IP", "SYS-COLLECT")) {
            var error = org.junit.jupiter.api.Assertions.assertThrows(CutoverChecklistException.class,
                    () -> fixture.service.selectManual(manual(generated, 0L, key)));
            assertEquals(CutoverChecklistException.Code.INVALID_REQUEST, error.getCode());
        }
        assertTrue(fixture.results.isEmpty());
    }

    @Test
    void refusesSubmissionOfLegacyManualFallbackWithoutFailureEvidence() {
        var fixture = fixture(); var generated = generate(fixture);
        var view = fixture.service.getView(new cn.iocoder.yudao.module.pms.cutover.service.checklist.query.CutoverChecklistReadQuery(
                new cn.iocoder.yudao.module.pms.cutover.service.checklist.context.CutoverChecklistContext(1L, 8L, "read"), 1000L, null));
        var item = view.items().stream().filter(value -> "SYS-COLLECT".equals(value.stableItemKey())).findFirst().orElseThrow();
        var oldManual = new CutoverChecklistItemResultDO();
        oldManual.setId(81L); oldManual.setTenantId(1L); oldManual.setChecklistItemId(item.itemId());
        oldManual.setResultVersion(1); oldManual.setResultSourceCode("MANUAL");
        oldManual.setFactDescription("旧入口录入但没有自动失败来源");
        fixture.results.add(oldManual);

        var error = org.junit.jupiter.api.Assertions.assertThrows(CutoverChecklistException.class,
                () -> fixture.service.submit(new SubmitChecklistCommand(1L, 8L, 1000L, 2L, 1L,
                        generated.checklistId(), generated.checklistFactVersion(), 7L, "legacy-manual", "legacy-manual")));

        assertEquals(CutoverChecklistException.Code.STATE_CONFLICT, error.getCode());
        assertTrue(error.getMessage().contains("人工降级缺少自动失败来源"));
        assertEquals("P3", fixture.task.get().getCurrentStage());
        assertTrue(fixture.history.isEmpty());
    }

    @Test
    void retainsOriginalCollectionFailureWhenManualEvidenceIsReplaced() {
        var fixture = fixture(); var generated = generate(fixture);
        var view = fixture.service.getView(new cn.iocoder.yudao.module.pms.cutover.service.checklist.query.CutoverChecklistReadQuery(
                new cn.iocoder.yudao.module.pms.cutover.service.checklist.context.CutoverChecklistContext(1L, 8L, "read"), 1000L, null));
        var item = view.items().stream().filter(value -> "SYS-COLLECT".equals(value.stableItemKey())).findFirst().orElseThrow();
        var failure = new CutoverChecklistItemResultDO(); failure.setId(81L); failure.setTenantId(1L);
        failure.setChecklistItemId(item.itemId()); failure.setResultVersion(3); failure.setResultSourceCode("COLLECTION");
        failure.setCollectionStatusCode("FAILED"); failure.setCollectionTaskId(99L); failure.setLoadFailureCode("SSH_FAILED");
        fixture.results.add(failure);
        fixture.service.selectManual(manual(generated, 0L, "SYS-COLLECT"));
        fixture.service.selectManual(manual(generated, 1L, "SYS-COLLECT"));
        var selected = fixture.results.stream().filter(value -> value.getSelectionEndedAt() == null).findFirst().orElseThrow();
        assertEquals("MANUAL", selected.getResultSourceCode());
        assertEquals(99L, selected.getOriginalFailedCollectionTaskId());
        assertEquals(3L, selected.getOriginalFailedResultVersion());
        assertEquals("SSH_FAILED", selected.getOriginalFailureCode());
        assertEquals("SSH_FAILED", failure.getLoadFailureCode());
        assertTrue(failure.getSelectionEndedAt() != null);
    }

    private static ChecklistCommandResult generate(Fixture fixture) {
        return fixture.service.generate(GenerateChecklistCommand.builder().tenantId(1L).actorId(8L).taskId(1000L)
                .expectedTaskVersion(2L).expectedAssessmentVersion(1L).expectedProjectScopeVersion(7L)
                .matchingInput(new cn.iocoder.yudao.module.pms.cutover.service.checklist.command.CutoverChecklistMatchInput("配置变更", "普通双机", List.of("ROUTER")))
                .selectedConflictDefinitions(Map.of()).idempotencyKey("generate").correlationId("generate").build());
    }

    @Test
    void matchingUsesLockedProjectLevelAndRejectsUnmappedValuesBeforeWriting() {
        for (String level : List.of("无", "办事处级", "未映射历史值")) {
            var fixture = fixture();
            var configurationService = (CutoverChecklistConfigurationQueryService)
                    org.springframework.test.util.ReflectionTestUtils.getField(fixture.service, "configurationService");
            var original = configurationService.resolveFrozen(null);
            var dictionaries = (tools.jackson.databind.node.ObjectNode) cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(original.dictionarySnapshot());
            dictionaries.set("majorProjectLevel", cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree("[{\"value\":\"无\",\"label\":\"无\"},{\"value\":\"办事处级\",\"label\":\"办事处级\"}]"));
            var dimensions = (tools.jackson.databind.node.ArrayNode) cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(original.dimensionDefinitionSnapshot());
            dimensions.add(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree("{\"code\":\"PROJECT_MAJOR_LEVEL\",\"enabled\":true,\"valueSource\":\"DICT:majorProjectLevel\"}"));
            when(configurationService.resolveFrozen(any())).thenReturn(new CutoverFrozenConfiguration(original.revisionId(), original.configurationCode(),
                    original.revisionNo(), original.statusCode(), dictionaries.toString(), dimensions.toString(), original.items(), original.rules()));
            var owner = mock(cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort.class);
            var fact = cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort.ProjectContextFact.builder()
                    .tenantId(1L).projectId(10L).projectVersion(12).projectScopeVersion(7L).majorProjectLevel(level).build();
            when(owner.inspect(any())).thenReturn(fact); when(owner.lockAndRevalidate(fact)).thenReturn(fact);
            org.springframework.test.util.ReflectionTestUtils.setField(fixture.service, "projectContext", owner);
            var metadata = (CutoverChecklistMetadataStore) org.springframework.test.util.ReflectionTestUtils.getField(fixture.service, "checklistMetadata");
            if ("未映射历史值".equals(level)) {
                var error = org.junit.jupiter.api.Assertions.assertThrows(CutoverChecklistException.class, () -> generate(fixture));
                assertEquals(CutoverChecklistException.Code.FROZEN_CONFIGURATION_INVALID, error.getCode());
                verify(metadata, org.mockito.Mockito.never()).replace(any());
                assertTrue(fixture.platform.facts.isEmpty());
            } else {
                var generated = generate(fixture);
                var write = org.mockito.ArgumentCaptor.forClass(CutoverChecklistMetadataStore.Write.class);
                verify(metadata).replace(write.capture());
                assertEquals(List.of(level), write.getValue().dimensions().get("PROJECT_MAJOR_LEVEL"));
                assertEquals(12L, write.getValue().checklist().getMatchingProjectVersion());
                verify(owner).lockAndRevalidate(fact);
                var changed = cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort.ProjectContextFact.builder()
                        .tenantId(1L).projectId(10L).projectVersion(13).projectScopeVersion(7L).majorProjectLevel("办事处级").build();
                when(owner.inspect(any())).thenReturn(changed); when(owner.lockAndRevalidate(changed)).thenReturn(changed);
                var stale = org.junit.jupiter.api.Assertions.assertThrows(CutoverChecklistException.class,
                        () -> fixture.service.submit(new SubmitChecklistCommand(1L, 8L, 1000L, 2L, 1L, generated.checklistId(),
                                generated.checklistFactVersion(), 7L, "stale-project", "stale-project")));
                assertEquals(CutoverChecklistException.Code.VERSION_CONFLICT, stale.getCode());
                assertEquals("P3", fixture.task.get().getCurrentStage());
            }
        }
    }

    @Test
    void changedMatchingInputsAdvanceTaskOnceAndReplayOriginalRequest() {
        var fixture = fixture();
        var mapper = (CutoverTaskMapper) org.springframework.test.util.ReflectionTestUtils.getField(fixture.service, "taskMapper");
        when(mapper.updateMatchingInputIfMatch(any())).thenReturn(1);
        var command = GenerateChecklistCommand.builder().tenantId(1L).actorId(8L).taskId(1000L)
                .expectedTaskVersion(2L).expectedAssessmentVersion(1L).expectedProjectScopeVersion(7L)
                .matchingInput(new cn.iocoder.yudao.module.pms.cutover.service.checklist.command.CutoverChecklistMatchInput("版本升级", null, List.of("ROUTER")))
                .selectedConflictDefinitions(Map.of()).idempotencyKey("changed-input").correlationId("changed-input").build();
        var generated = fixture.service.generate(command);
        var replay = fixture.service.generate(command);
        assertEquals(3L, fixture.task.get().getVersion());
        assertEquals("版本升级", fixture.task.get().getCutoverType());
        org.junit.jupiter.api.Assertions.assertNull(fixture.task.get().getNetworkMode());
        assertEquals(generated.checklistId(), replay.checklistId());
        assertTrue(replay.replayed());
        verify(mapper, times(1)).updateMatchingInputIfMatch(any());
    }

    private static SelectManualResultCommand manual(ChecklistCommandResult generated, long version, String key) {
        return new SelectManualResultCommand(1L, 8L, 1000L, 2L, generated.checklistId(), version, 7L, key,
                CutoverChecklistFilePort.FileHandle.builder().artifactId(90L).versionNo(2).referenceKey("ref-90")
                        .fileFactVersion(new CutoverChecklistFilePort.FileFactVersion(3, 4, 5)).scopeVersion(7L).build(),
                "现场核验材料", cn.iocoder.yudao.module.pms.cutover.support.CutoverTestCommands.intent());
    }

    private static Fixture fixture() {
        CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        CutoverTaskDeviceScopeMapper deviceMapper = mock(CutoverTaskDeviceScopeMapper.class);
        CutoverAssessmentMapper assessmentMapper = mock(CutoverAssessmentMapper.class);
        CutoverTaskStageHistoryMapper historyMapper = mock(CutoverTaskStageHistoryMapper.class);
        CutoverChecklistMapper checklistMapper = mock(CutoverChecklistMapper.class);
        CutoverChecklistItemMapper itemMapper = mock(CutoverChecklistItemMapper.class);
        CutoverChecklistItemResultMapper resultMapper = mock(CutoverChecklistItemResultMapper.class);
        CutoverChecklistConfigurationQueryService configurationService =
                mock(CutoverChecklistConfigurationQueryService.class);
        CutoverProjectScopePort scopePort = mock(CutoverProjectScopePort.class);
        CutoverCollectionPort collectionPort = mock(CutoverCollectionPort.class);
        CutoverChecklistFilePort filePort = mock(CutoverChecklistFilePort.class);
        CutoverNavigationDecisionQueryService navigationDecisionQueryService =
                mock(CutoverNavigationDecisionQueryService.class);
        DirectPlatform platform = new DirectPlatform();
        AtomicReference<CutoverTaskDO> task = new AtomicReference<>(task());
        AtomicReference<CutoverChecklistDO> checklist = new AtomicReference<>();
        List<CutoverChecklistItemDO> items = new ArrayList<>();
        List<CutoverChecklistItemResultDO> results = new ArrayList<>();
        List<CutoverTaskStageHistoryDO> history = new ArrayList<>();
        CutoverTaskDeviceScopeDO device = new CutoverTaskDeviceScopeDO();
        device.setTenantId(1L);
        device.setCutoverTaskId(1000L);
        device.setProjectId(10L);
        device.setDeviceId(400L);
        device.setSerialNumberSnapshot("SN-400");
        device.setProjectAssignmentVersion(9L);
        device.setDeviceTypeCodeSnapshot("ROUTER");
        device.setDeviceTypeSourceVersionSnapshot("pt-v1");
        device.setActiveMarker(1);

        CutoverAssessmentDO assessment = new CutoverAssessmentDO();
        assessment.setId(2000L);
        assessment.setTenantId(1L);
        assessment.setCutoverTaskId(1000L);
        assessment.setAssessmentVersion(1L);
        assessment.setAssessmentStatus("SUBMITTED");
        assessment.setManualGrade("A");
        assessment.setSimpleFlow(false);
        assessment.setVersion(1L);
        CutoverFrozenConfiguration configuration = new CutoverFrozenConfiguration(3000L, "CUT-CONFIG", 1,
                "PUBLISHED", "{\"pms_cutover_task_type\":[{\"value\":\"配置变更\",\"label\":\"配置变更\"},{\"value\":\"版本升级\",\"label\":\"版本升级\"}],\"pms_cutover_task_network_mode\":[{\"value\":\"普通双机\",\"label\":\"普通双机\"}],\"pms_device_type\":[{\"value\":\"ROUTER\",\"label\":\"路由器\"}]}",
                "[{\"code\":\"CUTOVER_TYPE\",\"enabled\":true,\"valueSource\":\"DICT:pms_cutover_task_type\"},{\"code\":\"NETWORK_MODE\",\"enabled\":true,\"valueSource\":\"DICT:pms_cutover_task_network_mode\"},{\"code\":\"DEVICE_TYPE\",\"valueSource\":\"DICT:pms_device_type\",\"enabled\":true}]", List.of(
                new CutoverFrozenConfiguration.ItemDefinition(4000L, "SYS-IP", 1, "TEXT", "管理地址", null,
                        "TEXT", "{\"type\":\"string\"}", "DIRECT", true, 10),
                new CutoverFrozenConfiguration.ItemDefinition(4001L, "SYS-COLLECT", 1, "TEXT", "设备采集", null,
                        "TEXT", "{\"type\":\"object\"}", "COLLECTION", true, 20),
                new CutoverFrozenConfiguration.ItemDefinition(4002L, "SYS-MATERIAL", 1, "TEXT", "人工材料", null,
                        "FILE", "{}", "MANUAL", true, 30)),
                List.of(
                new CutoverFrozenConfiguration.BindingRule(5000L, "RULE-IP", 4000L, 1,
                        "{\"CUTOVER_TYPE\":[\"配置变更\"],\"DEVICE_TYPE\":[\"ROUTER\"]}", 100, true, 0),
                new CutoverFrozenConfiguration.BindingRule(5001L, "RULE-COLLECT", 4001L, 1,
                        "{\"CUTOVER_TYPE\":[\"配置变更\"],\"DEVICE_TYPE\":[\"ROUTER\"]}", 90, true, 0),
                new CutoverFrozenConfiguration.BindingRule(5002L, "RULE-MATERIAL", 4002L, 1,
                        "{\"CUTOVER_TYPE\":[\"配置变更\"],\"DEVICE_TYPE\":[\"ROUTER\"]}", 90, true, 0)));
        CutoverProjectScopePort.ProjectScopeFact scope = new CutoverProjectScopePort.ProjectScopeFact(10L, 7L, true);
        CutoverChecklistFilePort.FileFactVersion fileVersion =
                new CutoverChecklistFilePort.FileFactVersion(3, 4, 5);

        when(taskMapper.selectById(1000L)).thenAnswer(ignored -> task.get());
        when(taskMapper.selectForUpdate(any())).thenAnswer(ignored -> task.get());
        when(deviceMapper.selectActiveByTask(any())).thenReturn(List.of(device));
        when(assessmentMapper.selectForUpdate(any())).thenReturn(assessment);
        when(scopePort.inspect(8L, 10L, "ACTION_EDIT")).thenReturn(scope);
        when(scopePort.inspect(8L, 10L, "ACTION_VIEW")).thenReturn(scope);
        when(scopePort.lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery.builder()
                .actorId(8L)
                .projectId(10L)
                .action("ACTION_EDIT")
                .expectedProjectScopeVersion(7L)
                .build())).thenReturn(scope);
        when(configurationService.resolveFrozen(any())).thenReturn(configuration);
        when(checklistMapper.selectCurrentForUpdate(any())).thenAnswer(ignored -> checklist.get());
        when(checklistMapper.selectCurrent(any())).thenAnswer(ignored -> checklist.get());
        when(checklistMapper.insert(any(CutoverChecklistDO.class))).thenAnswer(invocation -> {
            checklist.set(invocation.getArgument(0));
            return 1;
        });
        when(itemMapper.insert(any(CutoverChecklistItemDO.class))).thenAnswer(invocation -> {
            items.add(invocation.getArgument(0));
            return 1;
        });
        when(itemMapper.selectListForUpdate(any())).thenAnswer(ignored -> List.copyOf(items));
        when(itemMapper.selectListByChecklist(any())).thenAnswer(ignored -> List.copyOf(items));
        when(itemMapper.updateApplicability(any())).thenAnswer(invocation -> {
            var update = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistItemApplicabilityUpdate)
                    invocation.getArgument(0);
            CutoverChecklistItemDO replacement = update.item();
            items.removeIf(row -> row.getId().equals(replacement.getId()));
            items.add(replacement);
            return 1;
        });
        when(itemMapper.removeCustomIfMatch(any())).thenAnswer(invocation -> {
            var update = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistCustomRemoveUpdate)
                    invocation.getArgument(0);
            CutoverChecklistItemDO item = items.stream().filter(row -> row.getId().equals(update.itemId()))
                    .findFirst().orElseThrow();
            item.setApplicableFlag(false);
            item.setVersion(item.getVersion() + 1);
            return 1;
        });
        when(resultMapper.selectCurrentForUpdate(any())).thenAnswer(invocation -> {
            var query = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistCurrentResultQuery)
                    invocation.getArgument(0);
            return results.stream().filter(row -> row.getChecklistItemId().equals(query.checklistItemId())
                    && row.getSelectionEndedAt() == null).findFirst().orElse(null);
        });
        when(resultMapper.selectMaxVersion(any())).thenAnswer(invocation -> {
            var query = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistCurrentResultQuery)
                    invocation.getArgument(0);
            return results.stream().filter(row -> row.getChecklistItemId().equals(query.checklistItemId()))
                    .map(CutoverChecklistItemResultDO::getResultVersion).max(Integer::compareTo).orElse(0);
        });
        when(resultMapper.closeCurrentIfMatch(any())).thenAnswer(invocation -> {
            var update = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistResultCloseUpdate)
                    invocation.getArgument(0);
            results.stream().filter(row -> row.getId().equals(update.resultId()))
                    .findFirst().orElseThrow().setSelectionEndedAt(update.endedAt());
            return 1;
        });
        when(resultMapper.insert(any(CutoverChecklistItemResultDO.class))).thenAnswer(invocation -> {
            results.add(invocation.getArgument(0));
            return 1;
        });
        when(resultMapper.selectCurrentByChecklistForUpdate(any())).thenAnswer(ignored -> results.stream()
                .filter(row -> row.getSelectionEndedAt() == null).toList());
        when(resultMapper.selectCurrentByChecklist(any())).thenAnswer(ignored -> results.stream()
                .filter(row -> row.getSelectionEndedAt() == null).toList());
        when(checklistMapper.touchDraftIfMatch(any())).thenAnswer(ignored -> {
            checklist.get().setVersion(checklist.get().getVersion() + 1);
            return 1;
        });
        when(checklistMapper.submitIfMatch(any())).thenAnswer(ignored -> {
            checklist.get().setStatusCode("SUBMITTED");
            checklist.get().setVersion(checklist.get().getVersion() + 1);
            return 1;
        });
        when(checklistMapper.rematchIfMatch(any())).thenAnswer(invocation -> {
            var update = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRematchUpdate)
                    invocation.getArgument(0);
            checklist.set(update.checklist());
            checklist.get().setVersion(update.expectedVersion() + 1);
            return 1;
        });
        when(taskMapper.submitChecklistIfMatch(any())).thenAnswer(ignored -> {
            task.get().setCurrentStage("P4");
            task.get().setTaskStatus("PLAN_DRAFTING");
            task.get().setVersion(3L);
            return 1;
        });
        when(historyMapper.insert(any(CutoverTaskStageHistoryDO.class))).thenAnswer(invocation -> {
            history.add(invocation.getArgument(0));
            return 1;
        });
        when(filePort.lockAndRevalidate(any(CutoverChecklistFilePort.FileQuery.class))).thenReturn(
                CutoverChecklistFilePort.FileFact.builder()
                        .artifactId(90L)
                        .versionNo(2)
                        .referenceKey("ref-90")
                        .fileFactVersion(fileVersion)
                        .scopeVersion(7L)
                        .sha256("sha-90")
                        .build());
        when(collectionPort.request(any())).thenReturn(new CutoverCollectionPort.RequestReceipt(9000L,
                CutoverCollectionPort.TechnicalStatus.ACCEPTED));
        when(collectionPort.inspect(any()))
                .thenReturn(new CutoverCollectionPort.CollectionFact(9000L,
                                CutoverCollectionPort.TechnicalStatus.ACCEPTED, null, null, null, null),
                        new CutoverCollectionPort.CollectionFact(9000L,
                                CutoverCollectionPort.TechnicalStatus.COMPLETED, 9100L, 1L,
                                "{\"status\":\"ok\"}", null));
        when(navigationDecisionQueryService.decide(1L, 1000L)).thenReturn(
                new cn.iocoder.yudao.module.pms.cutover.service.checklist.result.NavigationDecision(
                        "POST_SUBMIT", 3000L, "TASK_OVERVIEW"));

        CutoverChecklistApplicationService service = new CutoverChecklistApplicationService(taskMapper,
                deviceMapper, assessmentMapper, historyMapper, checklistMapper, itemMapper, resultMapper, configurationService,
                new CutoverChecklistMatcher(), scopePort, collectionPort, filePort, platform,
                navigationDecisionQueryService,
                Clock.fixed(Instant.parse("2026-08-31T02:00:00Z"), ZoneOffset.UTC));
        var fields = mock(cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldDefinitionStore.class);
        var resultStore = mock(CutoverChecklistResultStore.class);
        var customStore = mock(CutoverCustomResultStore.class);
        var metadata = mock(CutoverChecklistMetadataStore.class);
        Map<Long, String> storedAnswers = new HashMap<>();
        Map<Long, CutoverChecklistFilePort.FileFact> storedFiles = new HashMap<>();
        when(fields.forItem(any())).thenAnswer(invocation -> {
            CutoverChecklistItemDO item = invocation.getArgument(0);
            return cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldSchemaCodec.encode(
                    cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldSchemaCodec.Write.builder()
                    .tenantId(item.getTenantId()).itemDefinitionRevisionId(item.getItemDefinitionId())
                    .customChecklistItemId(item.getItemDefinitionId() == null ? item.getId() : null)
                    .itemTypeCode(item.getItemTypeCode()).schema(Map.of("type", "COLLECTION".equals(item.getWorkModeCode()) ? "object" : "string")).build());
        });
        when(fields.withSchema(any())).thenAnswer(invocation -> {
            CutoverChecklistItemDO item = invocation.getArgument(0);
            item.setInterfaceSchemaSnapshot("COLLECTION".equals(item.getWorkModeCode())
                    ? "{\"type\":\"object\"}" : "{\"type\":\"string\"}"); return item;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            CutoverChecklistResultStore.Write write = invocation.getArgument(0);
            storedAnswers.put(write.result().getId(), write.answer()); return null;
        }).when(resultStore).insertAnswer(any());
        org.mockito.Mockito.doAnswer(invocation -> {
            CutoverCustomResultStore.Manual write = invocation.getArgument(0);
            storedFiles.put(write.result().getId(), write.fact()); return null;
        }).when(customStore).insertManual(any());
        when(customStore.manualFile(any())).thenAnswer(invocation -> storedFiles.get(((CutoverChecklistItemResultDO) invocation.getArgument(0)).getId()));
        when(resultStore.answer(any())).thenAnswer(invocation -> {
            CutoverChecklistResultStore.Read read = invocation.getArgument(0);
            String answer = storedAnswers.get(read.result().getId());
            return answer != null ? answer : cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(
                    "MANUAL".equals(read.result().getResultSourceCode())
                    ? Map.of("file", storedFiles.get(read.result().getId())) : Map.of("value", "collection-result"));
        });
        when(resultStore.value(any())).thenAnswer(invocation -> {
            CutoverChecklistResultStore.Read read = invocation.getArgument(0);
            return cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                    storedAnswers.get(read.result().getId()), Map.class).get("value");
        });
        when(itemMapper.selectById(any())).thenAnswer(invocation -> items.stream()
                .filter(item -> item.getId().equals(invocation.getArgument(0))).findFirst().orElse(null));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "fieldDefinitions", fields);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "resultStorage", resultStore);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "customResultStorage", customStore);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "checklistMetadata", metadata);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "commands",
                cn.iocoder.yudao.module.pms.cutover.support.CutoverTestCommands.executor());
        when(metadata.dimensions(any())).thenReturn(Map.of("DEVICE_TYPE", List.of("ROUTER")));
        return new Fixture(service, task, results, history, platform, collectionPort);
    }

    private static CutoverTaskDO task() {
        CutoverTaskDO task = new CutoverTaskDO();
        task.setId(1000L);
        task.setTenantId(1L);
        task.setProjectId(10L);
        task.setTaskOrigin("NEW_PLATFORM");
        task.setCurrentStage("P3");
        task.setTaskStatus("SURVEYING");
        task.setOwnerUserId(8L);
        task.setManualGrade("A");
        task.setCurrentAssessmentId(2000L);
        task.setProjectScopeVersion(7L);
        task.setConfigurationRevisionId(3000L);
        task.setConfigurationCode("CUT-CONFIG");
        task.setConfigurationRevisionNo(1);
        task.setCutoverType("配置变更");
        task.setNetworkMode("普通双机");
        task.setVersion(2L);
        return task;
    }

    private record Fixture(CutoverChecklistApplicationService service, AtomicReference<CutoverTaskDO> task,
                           List<CutoverChecklistItemResultDO> results,
                           List<CutoverTaskStageHistoryDO> history, DirectPlatform platform,
                           CutoverCollectionPort collectionPort) {
    }

    private static final class DirectPlatform implements PlatformCommandExecutionApi {
        private final List<SuccessFacts> facts = new ArrayList<>();
        private final Map<String, Object> responses = new HashMap<>();

        @Override
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest, Class<T> responseType,
                                              Supplier<T> operation, Function<T, SuccessFacts> successFactsFactory) {
            @SuppressWarnings("unchecked") T replay = (T) responses.get(scope.key());
            if (replay != null) {
                return new ExecutionResult<>(Decision.REPLAY_COMPLETED, replay);
            }
            T response = operation.get();
            facts.add(successFactsFactory.apply(response));
            responses.put(scope.key(), response);
            return new ExecutionResult<>(Decision.NEW, response);
        }
    }
}
