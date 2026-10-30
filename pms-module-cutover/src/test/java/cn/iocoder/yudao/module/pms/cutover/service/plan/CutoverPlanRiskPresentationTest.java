package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverChecklistFieldDefinitionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverChecklistFieldOptionMapper;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldDefinitionStore;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldSchemaCodec;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverPlanRiskPresentationTest {
    private final CutoverChecklistItemMapper items = mock(CutoverChecklistItemMapper.class);
    private final CutoverChecklistFieldDefinitionMapper fieldRows = mock(CutoverChecklistFieldDefinitionMapper.class);
    private final CutoverChecklistFieldOptionMapper choices = mock(CutoverChecklistFieldOptionMapper.class);
    private final CutoverPlanRiskPresentation presentation = new CutoverPlanRiskPresentation(items,
            new CutoverFieldDefinitionStore(fieldRows, choices));
    private final CutoverPlanRevisionDO plan = new CutoverPlanRevisionDO();
    private final CutoverChecklistItemDO item = new CutoverChecklistItemDO();
    private final String answer = "{\"value\":{\"executed\":false,\"remark\":\"原始检查说明\"}}";

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        plan.setTenantId(1L); plan.setChecklistId(20L); plan.setRevisionNo(1); plan.setGradeCode("A");
        item.setId(30L); item.setTenantId(1L); item.setChecklistId(20L);
        item.setItemDefinitionId(40L); item.setItemTypeCode("DUAL_MACHINE_CHECK");
        when(items.selectById(30L)).thenReturn(item);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    private tools.jackson.databind.JsonNode fact(String description) {
        return JsonUtils.getObjectMapper().valueToTree(Map.of("checklistItemId", "30", "itemName", "双机链路检查", "factDescription", description));
    }
    private void definitions() {
        var definition = CutoverFieldSchemaCodec.encode(CutoverFieldSchemaCodec.Write.builder().tenantId(1L)
                .itemDefinitionRevisionId(40L).itemTypeCode("DUAL_MACHINE_CHECK").schema(Map.of("fields", List.of(
                        Map.of("code", "executed", "label", "是否已执行", "type", "BOOLEAN"),
                        Map.of("code", "remark", "label", "事实说明", "type", "TEXT")))).build());
        when(fieldRows.selectListByParents(any())).thenReturn(definition.fields());
        when(choices.selectListByParents(any())).thenReturn(definition.options());
    }

    @Test void existingWordRiskUsesTheSameFieldSchemaAndFrozenAnswerWithoutAddingAnAppendix() throws Exception {
        definitions();
        var risk = fact(answer); String original = risk.toString();
        var task = new CutoverTaskDO(); task.setTaskName("统一说明验证");
        var body = JsonUtils.getObjectMapper().createObjectNode();
        body.putArray("riskMitigations").addObject().set("riskFact", risk);
        byte[] documentBytes = CutoverPlanDocumentRenderer.render(CutoverPlanDocumentRenderer.RenderContext.builder()
                .task(task).plan(plan).content(body).steps(List.of()).supports(List.of())
                .riskDescription(frozen -> presentation.describe(new CutoverPlanRiskPresentation.Query(1L, plan, frozen))).build());
        try (var document = new XWPFDocument(new ByteArrayInputStream(documentBytes)); var text = new XWPFWordExtractor(document)) {
            assertTrue(text.getText().contains("检查是否正常：否"));
            assertTrue(text.getText().contains("事实说明：原始检查说明"));
            assertFalse(text.getText().contains("是否已执行"));
            assertFalse(text.getText().contains("executed"));
            assertEquals(0, document.getTables().size());
        }
        assertEquals(original, risk.toString());
        verify(items).selectById(30L);
        verify(items, never()).updateById(any(CutoverChecklistItemDO.class));
        var output = java.nio.file.Path.of("..", ".run", "cutover-dual-label-generated.docx");
        java.nio.file.Files.write(output, documentBytes);
    }

    @Test void manualTextAndOtherStructuredResultsRetainTheirOriginalWordsWithoutMetadataReads() {
        for (String original : List.of("是否已执行：否（人工原文保留）", "{\"other\":false}", "普通风险说明")) {
            assertEquals(original, presentation.describe(new CutoverPlanRiskPresentation.Query(1L, plan, fact(original))));
        }
        verifyNoInteractions(items, fieldRows, choices);
    }

    @Test void foreignTenantIgnoredTenantOrForeignChecklistCannotSupplyFieldDescriptions() {
        var query = new CutoverPlanRiskPresentation.Query(1L, plan, fact(answer));
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class, () -> presentation.describe(query));
        TenantContextHolder.setTenantId(1L); TenantContextHolder.setIgnore(true);
        assertThrows(IllegalArgumentException.class, () -> presentation.describe(query));
        TenantContextHolder.setIgnore(false); item.setChecklistId(21L);
        assertThrows(IllegalArgumentException.class, () -> presentation.describe(query));
        item.setChecklistId(20L); item.setTenantId(2L);
        assertThrows(IllegalArgumentException.class, () -> presentation.describe(query));
        verifyNoInteractions(fieldRows, choices);
    }

    @Test void otherItemTypesKeepTheirOwnDescriptionAndDoNotBorrowDualLabels() {
        item.setItemTypeCode("RISK");
        assertEquals(answer, presentation.describe(new CutoverPlanRiskPresentation.Query(1L, plan, fact(answer))));
        verifyNoInteractions(fieldRows, choices);
    }
}
