package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistItemDefinitionRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistItemDefinitionRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.*;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveChecklistDeviceVersionsCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveExternalChecklistFeedbackCommand.Feedback;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.*;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverExternalBulletinStoreTest {
    private final CutoverChecklistDeviceContextMapper devices = mock(CutoverChecklistDeviceContextMapper.class);
    private final CutoverChecklistItemDefinitionRevisionMapper definitions = mock(CutoverChecklistItemDefinitionRevisionMapper.class);
    private final CutoverChecklistExternalQueryMapper queries = mock(CutoverChecklistExternalQueryMapper.class);
    private final CutoverChecklistResultQueryReferenceMapper references = mock(CutoverChecklistResultQueryReferenceMapper.class);
    private final CutoverResultTechnicalBulletinMapper bulletins = mock(CutoverResultTechnicalBulletinMapper.class);
    private final CutoverResultBulletinSummaryMapper summaries = mock(CutoverResultBulletinSummaryMapper.class);
    private final CutoverChecklistItemResultMapper results = mock(CutoverChecklistItemResultMapper.class);
    private final CutoverExternalChecklistMapper mutations = mock(CutoverExternalChecklistMapper.class);
    private final CutoverFieldDefinitionStore fields = mock(CutoverFieldDefinitionStore.class);
    private final CutoverExternalBulletinStore store = new CutoverExternalBulletinStore(devices, definitions, queries, references, bulletins, summaries, results, mutations, fields);
    private final List<CutoverChecklistDeviceContextDO> context = new ArrayList<>();
    private final List<CutoverChecklistExternalQueryDO> queryRows = new ArrayList<>();
    private final List<CutoverChecklistResultQueryReferenceDO> refRows = new ArrayList<>();
    private final List<CutoverResultTechnicalBulletinDO> bulletinRows = new ArrayList<>();
    private final List<CutoverResultBulletinSummaryDO> summaryRows = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(1000);
    private final CutoverChecklistItemDO item = new CutoverChecklistItemDO();
    private final CutoverChecklistItemDefinitionRevisionDO definition = new CutoverChecklistItemDefinitionRevisionDO();
    private final LocalDateTime time = LocalDateTime.of(2026, 10, 14, 0, 30);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        item.setId(10L); item.setTenantId(1L); item.setChecklistId(20L); item.setItemDefinitionId(30L);
        item.setItemDefinitionVersion(2); item.setWorkModeCode("EXTERNAL"); item.setStableItemKey("RISK_CURRENT_VERSION_BULLETIN");
        definition.setId(30L); definition.setTenantId(1L); definition.setItemDefinitionVersion(2);
        definition.setWorkModeCode("EXTERNAL"); definition.setBusinessCategoryCode("CURRENT_VERSION_BULLETIN");
        definition.setExternalInterfaceCode("FORMAL_BULLETIN"); definition.setExternalManualFallbackFlag(true);
        context.add(device(40L, "v1", "v2"));
        when(devices.selectListByParents(any())).thenAnswer(call -> List.copyOf(context));
        when(definitions.selectById(30L)).thenReturn(definition);
        when(queries.selectListByParents(any())).thenAnswer(call -> List.copyOf(queryRows));
        when(references.selectListByParents(any())).thenAnswer(call -> {
            var query = (CutoverChecklistResultQueryReferenceListQuery) call.getArgument(0);
            return refRows.stream().filter(row -> query.checklistResultIds().contains(row.getChecklistResultId())).toList();
        });
        when(references.insert(any(CutoverChecklistResultQueryReferenceDO.class))).thenAnswer(call -> { refRows.add(call.getArgument(0)); return 1; });
        when(bulletins.selectListByParents(any())).thenAnswer(call -> {
            var query = (CutoverResultTechnicalBulletinListQuery) call.getArgument(0);
            return bulletinRows.stream().filter(row -> query.checklistResultIds().contains(row.getChecklistResultId())).toList();
        });
        when(bulletins.selectByIds(anyCollection())).thenAnswer(call -> {
            Collection<?> selected = call.getArgument(0); return bulletinRows.stream().filter(row -> selected.contains(row.getId())).toList();
        });
        when(bulletins.insert(any(CutoverResultTechnicalBulletinDO.class))).thenAnswer(call -> {
            CutoverResultTechnicalBulletinDO row = call.getArgument(0); row.setId(ids.incrementAndGet()); bulletinRows.add(row); return 1;
        });
        when(summaries.insert(any(CutoverResultBulletinSummaryDO.class))).thenAnswer(call -> { summaryRows.add(call.getArgument(0)); return 1; });
        when(results.insert(any(CutoverChecklistItemResultDO.class))).thenReturn(1);
        var root = CutoverChecklistFieldDefinitionDO.builder().id(500L).builtinFieldCode("CURRENT_VERSION_BULLETIN").build();
        var table = CutoverChecklistFieldDefinitionDO.builder().id(501L).parentFieldId(500L).fieldCode("bulletins").dataTypeCode("TABLE").build();
        when(fields.forResult(any())).thenReturn(new CutoverFieldSchemaCodec.Definition(List.of(root, table), List.of()));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void successfulZeroRowsRetainsQueryAndStillRequiresAnExplicitFeedbackSave() {
        var loaded = result(60L, "EXTERNAL_LOAD"); var query = query(70L, 40L, "v1", true, 0, loaded.getId());
        queryRows.add(query);
        store.insertLoaded(new CutoverExternalBulletinStore.Loaded(item, loaded, List.of(query), List.of()));
        assertEquals(false, summaryRows.getFirst().getHasBulletinsFlag());
        assertEquals(70L, refRows.getFirst().getExternalQueryId());
        assertThrows(CutoverChecklistException.class, () -> store.validateSubmission(read(loaded)));
        var feedback = result(61L, "EXTERNAL_FEEDBACK"); feedback.setBaseChecklistResultId(60L);
        store.insertFeedback(new CutoverExternalBulletinStore.FeedbackWrite(read(loaded), feedback, List.of()));
        assertDoesNotThrow(() -> store.validateSubmission(read(feedback)));
        assertTrue(bulletinRows.isEmpty()); assertNull(loaded.getAnswerSnapshot());
    }

    @Test void failureNeverBecomesNoBulletinsAndManualFallbackRetainsFailedQuery() {
        var loaded = result(60L, "EXTERNAL_LOAD"); var query = query(70L, 40L, "v1", false, null, loaded.getId());
        queryRows.add(query); store.insertLoaded(new CutoverExternalBulletinStore.Loaded(item, loaded, List.of(query), List.of()));
        assertNull(summaryRows.getFirst().getHasBulletinsFlag()); assertEquals("EXTERNAL_LOAD_FAILED", loaded.getLoadFailureCode());
        definition.setExternalManualFallbackFlag(false);
        assertThrows(CutoverChecklistException.class, () -> store.requireManual(read(loaded)));
        definition.setExternalManualFallbackFlag(true);
        var manual = result(61L, "MANUAL_EVIDENCE_SELECTED"); manual.setResultSourceCode("MANUAL");
        store.referenceManual(new CutoverExternalBulletinStore.ManualWrite(read(loaded), manual));
        assertEquals("FAILED_FALLBACK", refRows.getLast().getReferencePurposeCode());
        assertDoesNotThrow(() -> store.validateSubmission(read(manual)));
        context.getFirst().setCurrentSoftwareVersion("v3");
        assertThrows(CutoverChecklistException.class, () -> store.validateSubmission(read(manual)));
    }

    @Test void feedbackOnlyReferencesRawRowAndHistoryKeepsOriginalValues() {
        var loaded = loadBulletin(); var original = bulletinRows.getFirst();
        var feedback = result(61L, "EXTERNAL_FEEDBACK"); feedback.setBaseChecklistResultId(60L);
        store.insertFeedback(new CutoverExternalBulletinStore.FeedbackWrite(read(loaded), feedback,
                List.of(new Feedback(original.getId(), true, "已安排升级前检查"))));
        var saved = bulletinRows.getLast();
        assertEquals(original.getId(), saved.getSourceBulletinRowId());
        assertNull(saved.getBulletinNumber()); assertNull(saved.getBulletinSubject()); assertNull(saved.getExternalQueryId());
        var history = store.resolvedRows(read(feedback)).getFirst();
        assertEquals("TB-1", history.getBulletinNumber()); assertEquals("原始主题", history.getBulletinSubject());
        assertEquals("已安排升级前检查", history.getMeasures()); assertNull(original.getInvolvedFlag());
        assertDoesNotThrow(() -> store.validateSubmission(read(feedback)));
        context.getFirst().setCurrentSoftwareVersion("changed");
        assertThrows(CutoverChecklistException.class, () -> store.validateSubmission(read(feedback)));
        assertEquals("原始主题", store.resolvedRows(read(feedback)).getFirst().getBulletinSubject());
    }

    @Test void foreignOrChainedOriginalAndMissingMeasuresAreRejected() {
        var loaded = loadBulletin(); var original = bulletinRows.getFirst(); var feedback = result(61L, "EXTERNAL_FEEDBACK");
        assertThrows(CutoverChecklistException.class, () -> store.insertFeedback(new CutoverExternalBulletinStore.FeedbackWrite(
                read(loaded), feedback, List.of(new Feedback(999L, false, null)))));
        verify(results, times(1)).insert(any(CutoverChecklistItemResultDO.class));
        store.insertFeedback(new CutoverExternalBulletinStore.FeedbackWrite(read(loaded), feedback, List.of(new Feedback(original.getId(), true, null))));
        assertThrows(CutoverChecklistException.class, () -> store.validateSubmission(read(feedback)));
        original.setSourceBulletinRowId(999L);
        assertThrows(CutoverChecklistException.class, () -> store.resolvedRows(read(feedback)));
        original.setSourceBulletinRowId(null); original.setTenantId(2L);
        assertThrows(CutoverChecklistException.class, () -> store.resolvedRows(read(feedback)));
    }

    @Test void multipleDevicesAndCurrentTargetVersionsCannotBeMixed() {
        context.add(device(41L, "other-current", "other-target")); definition.setBusinessCategoryCode("TARGET_VERSION_BULLETIN");
        var loaded = result(60L, "EXTERNAL_LOAD");
        var one = query(70L, 40L, "v2", true, 0, 60L); one.setVersionContextCode("TARGET");
        var two = query(71L, 41L, "other-target", true, 0, 60L); two.setVersionContextCode("TARGET");
        queryRows.addAll(List.of(one, two));
        store.insertLoaded(new CutoverExternalBulletinStore.Loaded(item, loaded, queryRows, List.of()));
        assertDoesNotThrow(() -> store.requireCurrent(read(loaded)));
        two.setQueriedSoftwareVersion("v2");
        assertThrows(CutoverChecklistException.class, () -> store.requireCurrent(read(loaded)));
        two.setQueriedSoftwareVersion("other-target"); two.setVersionContextCode("CURRENT");
        assertThrows(CutoverChecklistException.class, () -> store.requireCurrent(read(loaded)));
        two.setVersionContextCode("TARGET"); two.setTenantId(2L);
        assertThrows(CutoverChecklistException.class, () -> store.requireCurrent(read(loaded)));
    }

    @Test void contextsRejectForeignTenantAndPartialDeviceReplacement() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(CutoverChecklistException.class, () -> store.contexts(new CutoverExternalBulletinStore.ContextKey(1L, 20L)));
        TenantContextHolder.setTenantId(1L);
        assertThrows(CutoverChecklistException.class, () -> store.saveVersions(new SaveChecklistDeviceVersionsCommand(
                1L, 90L, 100L, 1L, 20L, 1L, 1L, List.of(new SaveChecklistDeviceVersionsCommand.Device(999L, null, "v1", "v2")))));
        verify(mutations, never()).updateDevice(any());
    }

    private CutoverChecklistItemResultDO loadBulletin() {
        var result = result(60L, "EXTERNAL_LOAD"); var query = query(70L, 40L, "v1", true, 1, result.getId()); queryRows.add(query);
        store.insertLoaded(new CutoverExternalBulletinStore.Loaded(item, result, List.of(query), List.of(
                new CutoverExternalBulletinStore.RawBulletin(70L, "source-1", "source-v1", "TB-1", "原始主题", "描述", "方案"))));
        return result;
    }
    private CutoverExternalBulletinStore.Read read(CutoverChecklistItemResultDO result) { return new CutoverExternalBulletinStore.Read(item, result); }
    private CutoverChecklistItemResultDO result(Long id, String reason) {
        return CutoverChecklistItemResultDO.builder().id(id).tenantId(1L).checklistItemId(10L).fieldDefinitionId(500L)
                .resultSourceCode("EXTERNAL").selectionReasonCode(reason).createdBy(90L).createdAt(time).build();
    }
    private CutoverChecklistDeviceContextDO device(Long id, String current, String target) {
        var row = CutoverChecklistDeviceContextDO.builder().deviceId(id).checklistId(20L).currentSoftwareVersion(current).targetSoftwareVersion(target).build();
        row.setTenantId(1L); return row;
    }
    private CutoverChecklistExternalQueryDO query(Long id, Long device, String version, boolean succeeded, Integer count, Long resultId) {
        return CutoverChecklistExternalQueryDO.builder().id(id).tenantId(1L).checklistItemId(10L).itemDefinitionRevisionId(30L)
                .deviceId(device).interfaceCode("FORMAL_BULLETIN").versionContextCode("CURRENT").queriedSoftwareVersion(version)
                .succeededFlag(succeeded).returnedRowCount(count).failureCode(succeeded ? null : "PROVIDER_UNAVAILABLE")
                .loadedChecklistResultId(resultId).startTime(time).finishTime(time.plusSeconds(1)).build();
    }
}
