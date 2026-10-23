package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistItemDefinitionRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.*;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.LoadExternalChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverExternalDataPort;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverExternalChecklistServiceTest {
    private final CutoverChecklistApplicationService checklist = mock(CutoverChecklistApplicationService.class);
    private final CutoverExternalBulletinStore storage = mock(CutoverExternalBulletinStore.class);
    private final CutoverExternalDataDirectory directory = mock(CutoverExternalDataDirectory.class);
    private final CutoverChecklistMapper checklists = mock(CutoverChecklistMapper.class);
    private final CutoverChecklistItemMapper items = mock(CutoverChecklistItemMapper.class);
    private final CutoverChecklistItemResultMapper results = mock(CutoverChecklistItemResultMapper.class);
    private final CutoverChecklistExternalMappingMapper mappings = mock(CutoverChecklistExternalMappingMapper.class);
    private final CutoverChecklistExternalQueryMapper queries = mock(CutoverChecklistExternalQueryMapper.class);
    private final CutoverChecklistExternalQueryParameterMapper parameters = mock(CutoverChecklistExternalQueryParameterMapper.class);
    private final CutoverAssessmentDeviceMapper assessment = mock(CutoverAssessmentDeviceMapper.class);
    private final CutoverExternalChecklistMapper mutations = mock(CutoverExternalChecklistMapper.class);
    private final CutoverExternalDataPort provider = mock(CutoverExternalDataPort.class);
    private final TransactionTemplate transactions = new TransactionTemplate(new BoundaryTransactions());
    private final CutoverExternalChecklistService service = new CutoverExternalChecklistService(checklist, storage, directory,
            checklists, items, results, mappings, queries, parameters, assessment, mutations);
    private final LoadExternalChecklistCommand command = new LoadExternalChecklistCommand(1L,
                2L,
                3L,
                4L,
                5L,
                6L,
                7L,
                "RISK_CURRENT_VERSION_BULLETIN",
                cn.iocoder.yudao.module.pms.cutover.support.CutoverTestCommands.intent());
    private final CutoverChecklistItemDO item = new CutoverChecklistItemDO();
    private final CutoverChecklistItemDefinitionRevisionDO definition = new CutoverChecklistItemDefinitionRevisionDO();
    private final List<CutoverChecklistDeviceContextDO> devices = new ArrayList<>();
    private final List<CutoverChecklistExternalMappingDO> mappingRows = new ArrayList<>();
    private final List<CutoverChecklistExternalQueryDO> queryRows = new ArrayList<>();
    private final List<CutoverChecklistExternalQueryParameterDO> parameterRows = new ArrayList<>();

    @BeforeEach void setup() {
        var platform = new cn.iocoder.yudao.module.pms.cutover.support.CutoverTestCommands() {
            @Override public <T> ExecutionResult<T> execute(IdempotencyScope scope, String digest, Class<T> type,
                    java.util.function.Supplier<T> action, java.util.function.Function<T, SuccessFacts> facts) {
                return transactions.execute(status -> super.execute(scope, digest, type, action, facts));
            }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(service, "commands",
                new cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandExecutor(platform));
        var task = new CutoverTaskDO(); task.setId(3L); task.setProjectId(33L);
        var root = new CutoverChecklistDO(); root.setId(5L); root.setAssessmentId(55L);
        item.setId(10L); item.setTenantId(1L); item.setChecklistId(5L); item.setStableItemKey(command.stableItemKey()); item.setApplicableFlag(true);
        definition.setId(30L); definition.setExternalInterfaceCode("FORMAL_BULLETIN"); definition.setBusinessCategoryCode("CURRENT_VERSION_BULLETIN");
        when(checklist.lockExternal(any())).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return new CutoverChecklistApplicationService.ExternalDraft(task, root, List.of(item));
        });
        when(storage.definition(item)).thenReturn(definition);
        when(storage.history(item)).thenReturn(List.of());
        devices.add(device(40L, "current-40", "target-40"));
        when(storage.contexts(any())).thenAnswer(call -> List.copyOf(devices));
        when(assessment.selectById(anyLong())).thenAnswer(call -> {
            Long deviceId = call.getArgument(0);
            return CutoverAssessmentDeviceDO.builder().id(deviceId).tenantId(1L).assessmentId(55L).deviceId(deviceId).sn("SN-" + deviceId).build();
        });
        when(directory.find("FORMAL_BULLETIN")).thenReturn(Optional.of(provider));
        var responseFields = List.of("number", "subject", "description", "solution").stream()
                .map(key -> new CutoverExternalDataPort.Field(key, key, "STRING", true)).toList();
        when(provider.definition()).thenReturn(new CutoverExternalDataPort.Definition("FORMAL_BULLETIN", "正式公告接口",
                List.of(new CutoverExternalDataPort.Field("version", "软件版本", "STRING", true)), responseFields));
        when(provider.query(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "外部调用不能占用数据库事务");
            return new CutoverExternalDataPort.Response(List.of());
        });
        mappingRows.add(mapping(100L, "PARAM", "version", "softwareVersion"));
        for (String field : List.of("number", "subject", "description", "solution")) mappingRows.add(mapping(100L + mappingRows.size(), "RETURN", field, field));
        when(mappings.selectListByParents(any())).thenAnswer(call -> List.copyOf(mappingRows));
        when(queries.insert(any(CutoverChecklistExternalQueryDO.class))).thenAnswer(call -> { queryRows.add(call.getArgument(0)); return 1; });
        when(parameters.insert(any(CutoverChecklistExternalQueryParameterDO.class))).thenAnswer(call -> {
            parameterRows.add(call.getArgument(0)); return 1;
        });
        when(parameters.selectListByParents(any())).thenAnswer(call -> List.copyOf(parameterRows));
        when(queries.selectByIds(any())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return queryRows.stream().filter(row -> ids.contains(row.getId())).toList();
        });
        when(items.selectById(10L)).thenReturn(item);
        when(checklists.touchDraftIfMatch(any())).thenReturn(1);
        when(mutations.lockItem(any())).thenReturn(10L);
        when(mutations.finishQuery(any())).thenReturn(1);
        when(storage.createResult(any())).thenAnswer(call -> {
            CutoverExternalBulletinStore.ResultCreate create = call.getArgument(0);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return CutoverChecklistItemResultDO.builder().id(60L).resultVersion(1).selectionEndedAt(create.selected() ? null : create.time()).build();
        });
    }

    @Test void successfulZeroIsRecordedSeparatelyFromUnavailableProvider() {
        var success = service.load(command);
        assertTrue(success.selected()); assertTrue(success.queries().getFirst().succeeded());
        assertEquals(0, success.queries().getFirst().returnedRowCount()); assertNull(success.queries().getFirst().failureCode());
        assertEquals(8L, success.checklistFactVersion());
        when(directory.find("FORMAL_BULLETIN")).thenReturn(Optional.empty());
        var failed = service.load(new LoadExternalChecklistCommand(command.tenantId(), command.actorId(),
                command.taskId(), command.expectedTaskVersion(), command.checklistId(),
                command.expectedChecklistVersion(), command.expectedProjectScopeVersion(), command.stableItemKey(),
                cn.iocoder.yudao.module.pms.cutover.support.CutoverTestCommands.intent()));
        assertFalse(failed.queries().getFirst().succeeded()); assertNull(failed.queries().getFirst().returnedRowCount());
        assertEquals("PROVIDER_UNAVAILABLE", failed.queries().getFirst().failureCode());
        verify(provider, times(1)).query(any());
    }

    @Test void retryAfterSuccessfulLoadReusesQueryIdentityAndCompletedResult() {
        var first = service.load(command);
        var replay = service.load(command);
        assertEquals(first, replay);
        assertEquals(1, queryRows.size());
        assertEquals(first.resultId(), queryRows.getFirst().getLoadedChecklistResultId());
        verify(provider, times(1)).query(any());
        verify(storage, times(1)).createResult(any());
        verify(mutations, times(1)).finishQuery(any());
        verify(checklist, times(2)).authorizeDraftAccess(command);
    }

    @Test void changedRequestWithTheSameKeyCannotReserveOrCallAnotherQuery() {
        service.load(command);
        var changed = new LoadExternalChecklistCommand(command.tenantId(), command.actorId(), command.taskId(),
                command.expectedTaskVersion(), command.checklistId(), command.expectedChecklistVersion() + 1,
                command.expectedProjectScopeVersion(), command.stableItemKey(), command.intent());
        var failure = assertThrows(cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandException.class,
                () -> service.load(changed));
        assertEquals(cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandException.Reason.IDEMPOTENCY_CONFLICT,
                failure.reason());
        assertEquals(1, queryRows.size());
        verify(provider, times(1)).query(any());
    }

    @Test void replayStillChecksCurrentAuthorizationBeforeReturningFacts() {
        service.load(command);
        doThrow(new CutoverChecklistException(CutoverChecklistException.Code.DATA_SCOPE_FORBIDDEN, "权限已撤销"))
                .when(checklist).authorizeDraftAccess(command);
        assertThrows(CutoverChecklistException.class, () -> service.load(command));
        verify(provider, times(1)).query(any());
        verify(storage, times(1)).createResult(any());
    }

    @Test void exactPerDeviceTargetVersionsArePassedAndFrozen() {
        definition.setBusinessCategoryCode("TARGET_VERSION_BULLETIN"); devices.add(device(41L, "current-41", "target-41"));
        var loaded = service.load(command);
        var requests = ArgumentCaptor.forClass(CutoverExternalDataPort.Request.class);
        verify(provider, times(2)).query(requests.capture());
        assertEquals(List.of("target-40", "target-41"), requests.getAllValues().stream().map(request -> request.parameters().get("version")).toList());
        assertEquals(List.of("TARGET", "TARGET"), loaded.queries().stream().map(CutoverExternalChecklistService.QueryView::versionContext).toList());
        var actual = ArgumentCaptor.forClass(CutoverChecklistExternalQueryParameterDO.class);
        verify(parameters, times(2)).insert(actual.capture());
        assertEquals(List.of("target-40", "target-41"), actual.getAllValues().stream().map(CutoverChecklistExternalQueryParameterDO::getValueText).toList());
    }

    @Test void missingVersionStopsBeforeCreatingOrCallingAQuery() {
        devices.getFirst().setCurrentSoftwareVersion(null);
        var failure = assertThrows(CutoverChecklistException.class, () -> service.load(command));
        assertEquals(CutoverChecklistException.Code.INVALID_REQUEST, failure.getCode());
        verify(queries, never()).insert(any(CutoverChecklistExternalQueryDO.class)); verify(provider, never()).query(any());
    }

    @Test void invalidMappingCannotTurnEvenAnEmptyResponseIntoSuccess() {
        mappingRows.removeLast();
        var result = service.load(command);
        assertEquals("MAPPING_INVALID", result.queries().getFirst().failureCode());
        assertNull(result.queries().getFirst().returnedRowCount()); verify(provider, never()).query(any());
    }

    @Test void staleDraftKeepsQueryHistoryButDoesNotSelectOrReplaceCurrentFeedback() {
        doThrow(new CutoverChecklistException(CutoverChecklistException.Code.VERSION_CONFLICT, "版本改变"))
                .when(checklist).lockExternal(command.afterReservation());
        var result = service.load(command);
        assertFalse(result.selected()); assertNull(result.checklistFactVersion());
        var created = ArgumentCaptor.forClass(CutoverExternalBulletinStore.ResultCreate.class);
        verify(storage).createResult(created.capture()); assertFalse(created.getValue().selected());
        verify(mutations).finishQuery(any()); verify(checklists, times(1)).touchDraftIfMatch(any());
    }

    @Test void transportExceptionDoesNotPersistRawResponseOrCredentialText() {
        doThrow(new IllegalStateException("secret-test-value response-header-example")).when(provider).query(any());
        var result = service.load(command);
        assertEquals("EXTERNAL_CALL_FAILED", result.queries().getFirst().failureCode());
        assertFalse(result.queries().getFirst().failureDetail().contains("secret-test-value"));
        assertNull(result.queries().getFirst().returnedRowCount());
    }

    @Test void rejectedOwnerGuardRunsBeforeAnyExternalSideEffect() {
        doThrow(new CutoverChecklistException(CutoverChecklistException.Code.DATA_SCOPE_FORBIDDEN, "不是负责人"))
                .when(checklist).lockExternal(command);
        assertThrows(CutoverChecklistException.class, () -> service.load(command));
        verifyNoInteractions(queries, parameters); verify(provider, never()).query(any());
    }

    private static CutoverChecklistDeviceContextDO device(Long id, String current, String target) {
        var row = CutoverChecklistDeviceContextDO.builder().deviceId(id).assessmentDeviceId(id)
                .currentSoftwareVersion(current).targetSoftwareVersion(target).build(); row.setTenantId(1L); return row;
    }
    private static CutoverChecklistExternalMappingDO mapping(Long id, String direction, String key, String path) {
        return CutoverChecklistExternalMappingDO.builder().id(id).mappingDirectionCode(direction).targetKey(key).sourcePath(path)
                .requiredFlag(true).valueTypeCode("STRING").build();
    }
    /** 仅验证 Spring 事务边界，不冒充真实数据库集成测试。 */
    private static class BoundaryTransactions extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
