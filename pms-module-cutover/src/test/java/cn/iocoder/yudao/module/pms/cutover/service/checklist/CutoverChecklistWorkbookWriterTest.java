package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistView;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CutoverChecklistWorkbookWriterTest {

    @Test
    void writesAuthorizedRowsInTwoStableSheetsWithoutFormulaOrInternalEvidenceIdentity() throws Exception {
        CutoverChecklistView view = view(List.of(
                item("survey-manual", "BUSINESS_SURVEY", 2, "MANUAL", "raw-manual",
                        "manual fact", "plt-ref-secret", null, null),
                item("survey-direct", "BUSINESS_SURVEY", null, "DIRECT", "=1+1",
                        "direct fact", null, null, null),
                item("risk-collection", "RISK", 1, "COLLECTION", "raw-collection",
                        "collection fact", null, 91L, 2L),
                item("risk-external", "RISK", 2, "EXTERNAL", "raw-external",
                        "external fact", null, null, null),
                item("dual-hidden", "DUAL_MACHINE_CHECK", 0, "DIRECT", "hidden",
                        "hidden", null, null, null)));

        CutoverChecklistWorkbookWriter.WorkbookContent content = new CutoverChecklistWorkbookWriter().write(view);

        assertThat(content.businessSurveyRowCount()).isEqualTo(2);
        assertThat(content.riskRowCount()).isEqualTo(2);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content.bytes()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetName(0)).isEqualTo("业务调研");
            assertThat(workbook.getSheetName(1)).isEqualTo("风险考察");
            assertThat(workbook.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short) 10);
            var headerRow = workbook.getSheetAt(0).getRow(0);
            assertThat(java.util.stream.IntStream.range(0, 10)
                    .mapToObj(index -> headerRow.getCell(index).getStringCellValue())
                    .toList())
                    .containsExactly("序号", "稳定项键", "项目名称", "项目说明", "工作模式",
                            "是否必填", "来源", "当前答案", "事实说明", "证据状态");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("survey-direct");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(7).getCellType()).isEqualTo(CellType.STRING);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(7).getStringCellValue()).isEqualTo("=1+1");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(7).getStringCellValue())
                    .isEqualTo("人工结果已提交");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(9).getStringCellValue())
                    .isEqualTo("人工附件已关联");
            assertThat(workbook.getSheetAt(1).getRow(1).getCell(7).getStringCellValue())
                    .isEqualTo("采集结果已形成");
            assertThat(workbook.getSheetAt(1).getRow(1).getCell(9).getStringCellValue())
                    .isEqualTo("采集结果已关联");
            assertThat(workbook.getSheetAt(1).getRow(2).getCell(7).getStringCellValue())
                    .isEqualTo("外部结果已形成");
        }
    }

    private static CutoverChecklistView.Item item(String key, String type, Integer sortOrder, String resultSource,
                                                   String answer, String factDescription, String manualReference,
                                                   Long collectionReference, Long collectionVersion) {
        return new CutoverChecklistView.Item(1L, key, type, key, "description", "TEXT", null,
                "DIRECT", true, "CONFIGURATION", true, sortOrder,
                new CutoverChecklistView.CurrentResult(1, resultSource, answer, factDescription,
                        manualReference, null, collectionReference, collectionVersion, null));
    }

    private static CutoverChecklistView view(List<CutoverChecklistView.Item> items) {
        return new CutoverChecklistView(31L, "P3", 7, 19L, 41L, 3, 2, "DRAFT",
                "hash", "config", "trace", null, items);
    }
}
