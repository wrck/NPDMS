package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.GenerateChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.context.CutoverChecklistContext;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverCollectionPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverChecklistMatchingTest {
    @Test
    void freezesSelectedCutoverTypeAndMatchesWithoutAssetTypeLookup() {
        Fixture fixture = new Fixture();
        fixture.service.generate(command(List.of("FW")));

        var input = fixture.metadataWrite.get();
        assertEquals(1, input.dimensions().get("DEVICE_TYPE").size());
        assertEquals("FW", input.dimensions().get("DEVICE_TYPE").getFirst());
        var deviceTypes = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(input.configuration().dictionarySnapshot()).path("pms_device_type");
        assertEquals("FW", deviceTypes.get(0).path("value").asText());
        assertEquals("防火墙", deviceTypes.get(0).path("label").asText());
        ArgumentCaptor<CutoverChecklistItemDO> inserted = ArgumentCaptor.forClass(CutoverChecklistItemDO.class);
        verify(fixture.items).insert(inserted.capture());
        assertEquals("RISK-FW", inserted.getValue().getStableItemKey());
        assertEquals("RISK", inserted.getValue().getItemTypeCode());
        assertTrue(inserted.getValue().getApplicableFlag());
        verifyNoInteractions(fixture.devices);

        var options = fixture.service.getMatchingOptions(new cn.iocoder.yudao.module.pms.cutover.service.checklist.query.CutoverChecklistReadQuery(new CutoverChecklistContext(1L, 8L, "corr"), 1000L, null));
        assertEquals(3000L, options.configurationRevisionId());
        assertEquals(1, options.configurationRevisionNo());
        assertEquals(List.of("FW"), options.matchingInput().deviceTypeCodes());
        assertEquals(List.of("FW", "SW"), options.deviceTypes().stream().map(option -> option.value()).toList());
        assertEquals("防火墙", options.deviceTypes().getFirst().label());
    }

    @Test
    void matchesFrozenCutoverLevelAlongsideSelectedDeviceType() {
        Fixture fixture = new Fixture("{\"DEVICE_TYPE\":[\"FW\"],\"CUTOVER_LEVEL\":[\"A\"]}");
        fixture.service.generate(command(List.of("FW")));

        ArgumentCaptor<CutoverChecklistItemDO> inserted = ArgumentCaptor.forClass(CutoverChecklistItemDO.class);
        verify(fixture.items).insert(inserted.capture());
        assertEquals("RISK-FW", inserted.getValue().getStableItemKey());
        assertTrue(inserted.getValue().getApplicableFlag());
        var input = fixture.metadataWrite.get();
        assertEquals("A", input.dimensions().get("CUTOVER_LEVEL").getFirst());
    }

    @Test
    void rejectsEmptyAndUnconfiguredTypesBeforeCommandExecutionOrPersistence() {
        Fixture fixture = new Fixture();
        assertEquals(INVALID_REQUEST, assertThrows(CutoverChecklistException.class,
                () -> fixture.service.generate(command(List.of()))).getCode());
        assertEquals(INVALID_REQUEST, assertThrows(CutoverChecklistException.class,
                () -> fixture.service.generate(command(List.of("ROUTER")))).getCode());
        verifyNoInteractions(fixture.platform);
        verify(fixture.checklists, never()).insert(any(CutoverChecklistDO.class));
        verify(fixture.items, never()).insert(any(CutoverChecklistItemDO.class));
    }

    @Test
    void deniesMatchingMetadataBeforeResolvingFrozenConfiguration() {
        Fixture fixture = new Fixture();
        when(fixture.scopes.inspect(any(), any(), any())).thenReturn(
                new CutoverProjectScopePort.ProjectScopeFact(10L, 7L, false));
        assertEquals(NOT_FOUND, assertThrows(CutoverChecklistException.class,
                () -> fixture.service.getMatchingOptions(new cn.iocoder.yudao.module.pms.cutover.service.checklist.query.CutoverChecklistReadQuery(new CutoverChecklistContext(1L, 8L, "corr"), 1000L, null))).getCode());
        verifyNoInteractions(fixture.configuration);
    }

    private static GenerateChecklistCommand command(List<String> types) {
        return GenerateChecklistCommand.builder()
                .tenantId(1L)
                .actorId(8L)
                .taskId(1000L)
                .expectedTaskVersion(2L)
                .expectedAssessmentVersion(1L)
                .expectedProjectScopeVersion(7L)
                .matchingInput(new cn.iocoder.yudao.module.pms.cutover.service.checklist.command.CutoverChecklistMatchInput("配置变更", null, types))
                .selectedConflictDefinitions(Map.of())
                .idempotencyKey("generate-1")
                .correlationId("corr").build();
    }

    private static final class Fixture {
        final CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
        final CutoverTaskDeviceScopeMapper devices = mock(CutoverTaskDeviceScopeMapper.class);
        final CutoverAssessmentMapper assessments = mock(CutoverAssessmentMapper.class);
        final CutoverChecklistMapper checklists = mock(CutoverChecklistMapper.class);
        final CutoverChecklistItemMapper items = mock(CutoverChecklistItemMapper.class);
        final CutoverChecklistConfigurationQueryService configuration = mock(CutoverChecklistConfigurationQueryService.class);
        final CutoverProjectScopePort scopes = mock(CutoverProjectScopePort.class);
        final DirectPlatform platform = spy(new DirectPlatform());
        final AtomicReference<CutoverChecklistDO> current = new AtomicReference<>();
        final AtomicReference<CutoverChecklistMetadataStore.Write> metadataWrite = new AtomicReference<>();
        final CutoverChecklistApplicationService service;

        Fixture() {
            this("{\"DEVICE_TYPE\":[\"FW\"]}");
        }

        Fixture(String dimensionConditionSnapshot) {
            CutoverTaskDO task = new CutoverTaskDO();
            task.setId(1000L); task.setTenantId(1L); task.setOwnerUserId(8L); task.setProjectId(10L);
            task.setTaskOrigin("NEW_PLATFORM"); task.setCurrentStage("P3"); task.setTaskStatus("SURVEYING");
            task.setVersion(2L); task.setProjectScopeVersion(7L); task.setCurrentAssessmentId(2000L);
            task.setConfigurationRevisionId(3000L); task.setConfigurationCode("CUT-CONFIG");
            task.setConfigurationRevisionNo(1); task.setManualGrade("A"); task.setCutoverType("配置变更");
            CutoverAssessmentDO assessment = new CutoverAssessmentDO();
            assessment.setId(2000L); assessment.setTenantId(1L); assessment.setCutoverTaskId(1000L);
            assessment.setAssessmentStatus("SUBMITTED"); assessment.setAssessmentVersion(1L);
            assessment.setVersion(1L); assessment.setManualGrade("A"); assessment.setSimpleFlow(false);
            when(tasks.selectById(1000L)).thenReturn(task);
            when(tasks.selectForUpdate(any())).thenReturn(task);
            when(assessments.selectForUpdate(any())).thenReturn(assessment);
            var scope = new CutoverProjectScopePort.ProjectScopeFact(10L, 7L, true);
            when(scopes.inspect(any(), any(), any())).thenReturn(scope);
            when(scopes.lockAndRevalidate(any())).thenReturn(scope);
            when(checklists.selectCurrent(any())).thenAnswer(invocation -> current.get());
            when(checklists.insert(any(CutoverChecklistDO.class))).thenAnswer(invocation -> {
                current.set(invocation.getArgument(0));
                return 1;
            });
            when(items.insert(any(CutoverChecklistItemDO.class))).thenReturn(1);
            var frozen = new CutoverFrozenConfiguration(3000L, "CUT-CONFIG", 1, "PUBLISHED",
                    "{\"pms_cutover_task_type\":[{\"value\":\"配置变更\",\"label\":\"配置变更\"},{\"value\":\"版本升级\",\"label\":\"版本升级\"}],\"pms_cutover_task_network_mode\":[{\"value\":\"普通双机\",\"label\":\"普通双机\"}],\"pms_device_type\":[{\"value\":\"FW\",\"label\":\"防火墙\"},{\"value\":\"SW\",\"label\":\"交换机\"}]}",
                    "[{\"code\":\"CUTOVER_TYPE\",\"enabled\":true,\"valueSource\":\"DICT:pms_cutover_task_type\"},{\"code\":\"NETWORK_MODE\",\"enabled\":true,\"valueSource\":\"DICT:pms_cutover_task_network_mode\"},{\"code\":\"DEVICE_TYPE\",\"enabled\":true,\"valueSource\":\"DICT:pms_device_type\"}]",
                    List.of(new CutoverFrozenConfiguration.ItemDefinition(4000L, "RISK-FW", 1,
                            "RISK", "防火墙风险", null, "TEXT", "{\"type\":\"string\"}", "DIRECT", true, 10)),
                    List.of(new CutoverFrozenConfiguration.BindingRule(5000L, "FW-ONLY", 4000L, 1,
                            dimensionConditionSnapshot, 10, true)));
            when(configuration.resolveFrozen(any())).thenReturn(frozen);
            service = new CutoverChecklistApplicationService(tasks, devices, assessments,
                    mock(CutoverTaskStageHistoryMapper.class), checklists, items,
                    mock(CutoverChecklistItemResultMapper.class), configuration, new CutoverChecklistMatcher(),
                    scopes, mock(CutoverCollectionPort.class), mock(CutoverChecklistFilePort.class), platform,
                    mock(CutoverNavigationDecisionQueryService.class), Clock.systemUTC());
            var metadata = mock(CutoverChecklistMetadataStore.class);
            doAnswer(invocation -> { metadataWrite.set(invocation.getArgument(0)); return null; }).when(metadata).replace(any());
            when(metadata.dimensions(any())).thenAnswer(invocation -> metadataWrite.get().dimensions());
            org.springframework.test.util.ReflectionTestUtils.setField(service, "checklistMetadata", metadata);
            org.springframework.test.util.ReflectionTestUtils.setField(service, "fieldDefinitions", mock(cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldDefinitionStore.class));
            org.springframework.test.util.ReflectionTestUtils.setField(service, "resultStorage", mock(CutoverChecklistResultStore.class));
            org.springframework.test.util.ReflectionTestUtils.setField(service, "customResultStorage", mock(CutoverCustomResultStore.class));
        }
    }

    private static class DirectPlatform implements PlatformCommandExecutionApi {
        @Override
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest, Class<T> responseType,
                                              Supplier<T> operation, Function<T, SuccessFacts> successFactsFactory) {
            T response = operation.get();
            successFactsFactory.apply(response);
            return new ExecutionResult<>(Decision.NEW, response);
        }
    }
}
