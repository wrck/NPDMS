package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.*;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverChecklistRevisionServiceTest {
    @Test
    void rebindsNestedDirectAttachmentsWithoutChangingHistoricalReferences() {
        var f = new Fixture("DIRECT");
        var historical = Map.of("rows", List.of(Map.of("attachment", f.file, "description", "现场材料")));
        when(f.answers.value(any())).thenReturn(historical);
        f.service.copyItems(f.copy(false));
        var attached = ArgumentCaptor.forClass(AttachExistingFileVersionsCommand.class);
        verify(f.files).attachExistingVersions(attached.capture());
        var item = attached.getValue().items().getFirst();
        assertEquals("11", item.source().objectId());
        assertEquals("old-reference", item.source().referenceKey());
        assertNotEquals("11", item.target().objectId());
        var stored = ArgumentCaptor.forClass(CutoverChecklistResultStore.Write.class);
        verify(f.answers).insertAnswer(stored.capture());
        assertEquals("new-reference", JsonUtils.parseTree(stored.getValue().answer()).path("value").path("rows").get(0).path("attachment").path("referenceKey").asText());
        assertEquals("old-reference", f.file.referenceKey());
        assertNull(f.selected.getBaseChecklistResultId());
    }

    @Test
    void carriesManualEvidenceAndOriginalFailureThroughFormalFileOwner() {
        var f = new Fixture("MANUAL"); f.sourceItem.setWorkModeCode("COLLECTION");
        f.selected.setOriginalFailedCollectionTaskId(80L); f.selected.setOriginalFailureCode("DEVICE_UNREACHABLE");
        when(f.custom.manualFile(f.selected)).thenReturn(f.file);
        f.service.copyItems(f.copy(false));
        var stored = ArgumentCaptor.forClass(CutoverCustomResultStore.Manual.class);
        verify(f.custom).insertManual(stored.capture());
        assertEquals("new-reference", stored.getValue().fact().referenceKey());
        assertEquals(80L, stored.getValue().result().getOriginalFailedCollectionTaskId());
        assertEquals("DEVICE_UNREACHABLE", stored.getValue().result().getOriginalFailureCode());
        assertEquals(31L, stored.getValue().result().getBaseChecklistResultId());
    }

    @Test
    void neverReactivatesLegacyManualFallbackWithoutAnAutomaticFailure() {
        for (String mode : List.of("DIRECT", "COLLECTION")) {
            var f = new Fixture("MANUAL");
            f.sourceItem.setWorkModeCode(mode);
            f.service.copyItems(f.copy(false));
            verify(f.results, never()).insert(any(CutoverChecklistItemResultDO.class));
            verify(f.files, never()).attachExistingVersions(any());
            assertNull(f.selected.getBaseChecklistResultId());
            assertEquals("MANUAL", f.selected.getResultSourceCode());
        }
    }

    @Test
    void propagatesFileOwnerDenialInsteadOfInsertingManualResult() {
        var f = new Fixture("MANUAL");
        when(f.custom.manualFile(f.selected)).thenReturn(f.file);
        when(f.files.attachExistingVersions(any())).thenThrow(new IllegalStateException("reference denied"));
        assertThrows(IllegalStateException.class, () -> f.service.copyItems(f.copy(false)));
        verify(f.results, never()).insert(any(CutoverChecklistItemResultDO.class));
        verify(f.custom, never()).insertManual(any());
    }

    @Test
    void newConfigurationCarriesOnlyIdenticalFieldDefinitionsAndNeverCopiesAutomaticResults() {
        for (String kind : List.of("SAME", "CHANGED", "AUTOMATIC")) {
            var f = new Fixture("DIRECT");
            var targetItem = new CutoverChecklistItemDO(); org.springframework.beans.BeanUtils.copyProperties(f.sourceItem, targetItem);
            targetItem.setId(12L); targetItem.setChecklistId(2L); targetItem.setItemDefinitionId(102L);
            when(f.items.selectListByChecklist(argThat(query -> query.checklistId().equals(2L)))).thenReturn(List.of(targetItem));
            when(f.fields.forItem(targetItem)).thenReturn(f.definition(102L, kind.equals("CHANGED") ? "变更后的字段" : "现场材料"));
            when(f.answers.value(any())).thenReturn("已核实");
            if (kind.equals("AUTOMATIC")) f.selected.setResultSourceCode("COLLECTION");
            f.service.copyItems(f.copy(true));
            verify(f.answers, times(kind.equals("SAME") ? 1 : 0)).insertAnswer(any());
            verify(f.items, never()).insert(any(CutoverChecklistItemDO.class));
        }
    }

    private static final class Fixture {
        final CutoverChecklistMapper checklists = mock(CutoverChecklistMapper.class);
        final CutoverChecklistItemMapper items = mock(CutoverChecklistItemMapper.class);
        final CutoverChecklistItemResultMapper results = mock(CutoverChecklistItemResultMapper.class);
        final CutoverChecklistMetadataStore metadata = mock(CutoverChecklistMetadataStore.class);
        final CutoverFieldDefinitionStore fields = mock(CutoverFieldDefinitionStore.class);
        final CutoverChecklistResultStore answers = mock(CutoverChecklistResultStore.class);
        final CutoverCustomResultStore custom = mock(CutoverCustomResultStore.class);
        final FileArtifactApi files = mock(FileArtifactApi.class);
        final CutoverChecklistRevisionService service = new CutoverChecklistRevisionService(checklists, items, results, metadata, fields, answers, custom);
        final CutoverChecklistDO source = new CutoverChecklistDO(), target = new CutoverChecklistDO();
        final CutoverChecklistItemDO sourceItem = new CutoverChecklistItemDO();
        final CutoverChecklistItemResultDO selected = new CutoverChecklistItemResultDO();
        final CutoverChecklistFilePort.FileFact file = CutoverChecklistFilePort.FileFact.builder()
                .artifactId(99L).versionNo(1).referenceKey("old-reference").scopeVersion(7L).sha256("a".repeat(64))
                .fileFactVersion(new CutoverChecklistFilePort.FileFactVersion(2, 3, 4)).build();

        Fixture(String resultSource) {
            source.setId(1L); source.setTenantId(1L); source.setCutoverTaskId(10L);
            target.setId(2L); target.setTenantId(1L); target.setCutoverTaskId(10L); target.setStatusCode("DRAFT"); target.setProjectScopeVersion(7L);
            sourceItem.setId(11L); sourceItem.setTenantId(1L); sourceItem.setChecklistId(1L); sourceItem.setStableItemKey("SYS-MATERIAL");
            sourceItem.setApplicableFlag(true); sourceItem.setSourceCode("SYSTEM_MATCHED"); sourceItem.setItemDefinitionId(101L);
            sourceItem.setItemDefinitionVersion(1); sourceItem.setWorkModeCode(resultSource); sourceItem.setItemTypeCode("MATERIAL");
            selected.setId(31L); selected.setTenantId(1L); selected.setChecklistItemId(11L); selected.setResultSourceCode(resultSource);
            when(items.selectListByChecklist(any())).thenReturn(List.of(sourceItem));
            when(results.selectCurrentByChecklist(any())).thenReturn(List.of(selected));
            when(items.insert(any(CutoverChecklistItemDO.class))).thenReturn(1);
            when(results.insert(any(CutoverChecklistItemResultDO.class))).thenReturn(1);
            when(fields.forItem(any())).thenReturn(definition(101L, "现场材料"));
            when(files.attachExistingVersions(any())).thenReturn(List.of(new FileArtifactVersionFact(99L, 1, "new-reference", "MANUAL_EVIDENCE",
                    "proof.pdf", 10L, "application/pdf", "a".repeat(64), "AVAILABLE", "ACTIVE", new FileFactVersion(2, 5, 4), 7L)));
            ReflectionTestUtils.setField(service, "files", files);
        }
        CutoverFieldSchemaCodec.Definition definition(Long id, String label) {
            return CutoverFieldSchemaCodec.encode(CutoverFieldSchemaCodec.Write.builder().tenantId(1L).itemDefinitionRevisionId(id)
                    .businessCategoryCode("CUSTOM").itemTypeCode("MATERIAL").schema(Map.of("type", "text", "label", label)).build());
        }
        CutoverChecklistRevisionService.Copy copy(boolean matched) {
            return new CutoverChecklistRevisionService.Copy(source, target, 8L, LocalDateTime.now(), matched);
        }
    }
}
