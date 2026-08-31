package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskStageHistoryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemResultMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.AddCustomItemCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.GenerateChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RematchChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SelectManualResultCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SubmitChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistItemCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistView;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CutoverChecklistApplicationServiceTest {

    @Test
    void completesP3WithDirectCustomAndControlledManualEvidence() {
        Fixture fixture = fixture();

        ChecklistCommandResult generated = fixture.service.generate(new GenerateChecklistCommand(
                1L, 8L, 1000L, 2, 1, 7L, Map.of(), "generate-1", "corr-generate-1"));
        ChecklistCommandResult saved = fixture.service.save(new SaveChecklistCommand(
                1L, 8L, 1000L, 2, generated.checklistId(), 0, 7L,
                List.of(new SaveChecklistCommand.DirectAnswer("SYS-IP", "{\"value\":\"10.0.0.1\"}"))));
        ChecklistItemCommandResult custom = fixture.service.addCustomItem(new AddCustomItemCommand(
                1L, 8L, 1000L, 2, generated.checklistId(), saved.checklistFactVersion(), 7L,
                "TEXT", "现场补充项", null, "TEXT", "{\"type\":\"string\"}", true,
                "{\"value\":\"已核对\"}"));
        CutoverChecklistFilePort.FileFactVersion fileVersion =
                new CutoverChecklistFilePort.FileFactVersion(3, 4, 5);
        ChecklistItemCommandResult manual = fixture.service.selectManual(new SelectManualResultCommand(
                1L, 8L, 1000L, 2, generated.checklistId(), custom.checklistVersion(), 7L,
                "SYS-IP", new CutoverChecklistFilePort.FileHandle(90L, 2, "ref-90", fileVersion, 7L),
                "现场截图"));
        CutoverChecklistView beforeRematch = fixture.service.getView(1L, 8L, 1000L);
        ChecklistCommandResult rematched = fixture.service.rematch(new RematchChecklistCommand(
                1L, 8L, 1000L, 2, 1, generated.checklistId(), manual.checklistVersion(),
                beforeRematch.inputSnapshotHash(), 7L, Map.of(), "rematch-1", "corr-rematch-1"));
        CutoverChecklistView view = fixture.service.getView(1L, 8L, 1000L);
        ChecklistCommandResult submitted = fixture.service.submit(new SubmitChecklistCommand(
                1L, 8L, 1000L, 2, 1, generated.checklistId(), rematched.checklistFactVersion(), 7L,
                "submit-1", "corr-submit-1"));

        assertEquals("DRAFT", generated.checklistStatus());
        assertEquals(1, saved.checklistFactVersion());
        assertEquals(2, custom.checklistVersion());
        assertEquals(2, manual.resultVersion());
        assertEquals(2, rematched.checklistVersion());
        assertEquals(2, view.items().size());
        assertEquals("MANUAL", view.items().stream().filter(item -> "SYS-IP".equals(item.stableItemKey()))
                .findFirst().orElseThrow().currentResult().resultSourceCode());
        assertEquals("现场截图", view.items().stream().filter(item -> "SYS-IP".equals(item.stableItemKey()))
                .findFirst().orElseThrow().currentResult().factDescription());
        assertEquals("SUBMITTED", submitted.checklistStatus());
        assertEquals("P4", submitted.taskStage());
        assertEquals("PLAN_DRAFTING", fixture.task.get().getTaskStatus());
        assertEquals(List.of("P3_CHECKLIST_SUBMITTED"), fixture.history.stream()
                .map(CutoverTaskStageHistoryDO::getTriggerType).toList());
        assertEquals(3, fixture.platform.facts.size());
        assertFalse(fixture.results.stream().filter(row -> "DIRECT".equals(row.getResultSourceCode()))
                .allMatch(row -> row.getSelectionEndedAt() == null));
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
        CutoverChecklistFilePort filePort = mock(CutoverChecklistFilePort.class);
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
        assessment.setAssessmentVersion(1);
        assessment.setAssessmentStatus("SUBMITTED");
        assessment.setManualGrade("A");
        assessment.setSimpleFlow(false);
        assessment.setVersion(1);
        CutoverFrozenConfiguration configuration = new CutoverFrozenConfiguration(3000L, "CUT-CONFIG", 1,
                "PUBLISHED", "{}", "{}", List.of(new CutoverFrozenConfiguration.ItemDefinition(
                4000L, "SYS-IP", 1, "TEXT", "管理地址", null, "TEXT",
                "{\"type\":\"string\"}", "DIRECT", true, 10)),
                List.of(new CutoverFrozenConfiguration.BindingRule(5000L, "RULE-IP", 4000L, 1,
                        "{\"CUTOVER_TYPE\":[\"配置变更\"],\"DEVICE_TYPE\":[\"ROUTER\"]}", 100, true, 0)));
        CutoverProjectScopePort.ProjectScopeFact scope = new CutoverProjectScopePort.ProjectScopeFact(10L, 7L, true);
        CutoverChecklistFilePort.FileFactVersion fileVersion =
                new CutoverChecklistFilePort.FileFactVersion(3, 4, 5);

        when(taskMapper.selectById(1000L)).thenAnswer(ignored -> task.get());
        when(taskMapper.selectForUpdate(any())).thenAnswer(ignored -> task.get());
        when(deviceMapper.selectActiveByTask(any())).thenReturn(List.of(device));
        when(assessmentMapper.selectForUpdate(any())).thenReturn(assessment);
        when(scopePort.inspect(8L, 10L, "ACTION_EDIT")).thenReturn(scope);
        when(scopePort.inspect(8L, 10L, "ACTION_VIEW")).thenReturn(scope);
        when(scopePort.lockAndRevalidate(8L, 10L, "ACTION_EDIT", 7L)).thenReturn(scope);
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
            CutoverChecklistItemDO item = items.stream().filter(row -> row.getId().equals(update.itemId()))
                    .findFirst().orElseThrow();
            item.setApplicableFlag(update.applicable());
            item.setRequiredFlag(update.required());
            item.setItemDefinitionId(update.itemDefinitionId());
            item.setItemDefinitionVersion(update.itemDefinitionVersion());
            item.setItemTypeCode(update.itemTypeCode());
            item.setItemName(update.itemName());
            item.setItemDescription(update.itemDescription());
            item.setInterfaceFormatCode(update.interfaceFormatCode());
            item.setInterfaceSchemaSnapshot(update.interfaceSchemaSnapshot());
            item.setDisplayConditionSnapshot(update.displayConditionSnapshot());
            item.setWorkModeCode(update.workModeCode());
            item.setMatchedRuleId(update.matchedRuleId());
            item.setMatchedRuleVersion(update.matchedRuleVersion());
            item.setSortOrder(update.sortOrder());
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
            checklist.get().setChecklistVersion(update.nextChecklistVersion());
            checklist.get().setAssessmentId(update.assessmentId());
            checklist.get().setAssessmentVersion(update.assessmentVersion());
            checklist.get().setInputSnapshot(update.inputSnapshot());
            checklist.get().setInputSnapshotHash(update.inputSnapshotHash());
            checklist.get().setMatchTrace(update.matchTrace());
            checklist.get().setConfigGapSnapshot(update.configGapSnapshot());
            checklist.get().setVersion(checklist.get().getVersion() + 1);
            return 1;
        });
        when(taskMapper.submitChecklistIfMatch(any())).thenAnswer(ignored -> {
            task.get().setCurrentStage("P4");
            task.get().setTaskStatus("PLAN_DRAFTING");
            task.get().setVersion(3);
            return 1;
        });
        when(historyMapper.insert(any(CutoverTaskStageHistoryDO.class))).thenAnswer(invocation -> {
            history.add(invocation.getArgument(0));
            return 1;
        });
        when(filePort.lockAndRevalidate(any(), any(), any(), any(), any(Long.class), any())).thenReturn(
                new CutoverChecklistFilePort.FileFact(90L, 2, "ref-90", fileVersion, 7L, "sha-90"));

        CutoverChecklistApplicationService service = new CutoverChecklistApplicationService(taskMapper,
                deviceMapper, assessmentMapper, historyMapper, checklistMapper, itemMapper, resultMapper, configurationService,
                new CutoverChecklistMatcher(), scopePort, filePort, platform,
                Clock.fixed(Instant.parse("2026-08-31T02:00:00Z"), ZoneOffset.UTC));
        return new Fixture(service, task, results, history, platform);
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
        task.setVersion(2);
        return task;
    }

    private record Fixture(CutoverChecklistApplicationService service, AtomicReference<CutoverTaskDO> task,
                           List<CutoverChecklistItemResultDO> results,
                           List<CutoverTaskStageHistoryDO> history, DirectPlatform platform) {
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
