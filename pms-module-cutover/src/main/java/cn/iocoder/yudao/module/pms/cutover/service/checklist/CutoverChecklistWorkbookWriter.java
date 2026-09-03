package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistView;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistExportException.Code.EXPORT_PROJECTION_INVALID;

public final class CutoverChecklistWorkbookWriter {

    private static final List<String> HEADERS = List.of("序号", "稳定项键", "项目名称", "项目说明", "工作模式",
            "是否必填", "来源", "当前答案", "事实说明", "证据状态");
    private static final Comparator<CutoverChecklistView.Item> ROW_ORDER = Comparator
            .comparingInt((CutoverChecklistView.Item item) -> item.sortOrder() == null ? 0 : item.sortOrder())
            .thenComparing(CutoverChecklistView.Item::stableItemKey);

    public WorkbookContent write(CutoverChecklistView view) {
        require(view != null && view.items() != null, "授权清单投影为空");
        List<CutoverChecklistView.Item> businessRows = rows(view, "BUSINESS_SURVEY");
        List<CutoverChecklistView.Item> riskRows = rows(view, "RISK");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeSheet(workbook.createSheet("业务调研"), businessRows);
            writeSheet(workbook.createSheet("风险考察"), riskRows);
            workbook.write(output);
            return new WorkbookContent(output.toByteArray(), businessRows.size(), riskRows.size());
        } catch (IOException exception) {
            throw new CutoverChecklistExportException(EXPORT_PROJECTION_INVALID, "授权清单工作簿生成失败", exception);
        }
    }

    private static List<CutoverChecklistView.Item> rows(CutoverChecklistView view, String itemTypeCode) {
        return view.items().stream()
                .filter(item -> item != null && item.applicable() && itemTypeCode.equals(item.itemTypeCode()))
                .peek(CutoverChecklistWorkbookWriter::validateItem)
                .sorted(ROW_ORDER)
                .toList();
    }

    private static void writeSheet(Sheet sheet, List<CutoverChecklistView.Item> items) {
        Row header = sheet.createRow(0);
        for (int column = 0; column < HEADERS.size(); column++) {
            text(header.createCell(column, CellType.STRING), HEADERS.get(column));
        }
        for (int index = 0; index < items.size(); index++) {
            CutoverChecklistView.Item item = items.get(index);
            Row row = sheet.createRow(index + 1);
            row.createCell(0, CellType.NUMERIC).setCellValue(index + 1);
            text(row.createCell(1, CellType.STRING), item.stableItemKey());
            text(row.createCell(2, CellType.STRING), item.itemName());
            text(row.createCell(3, CellType.STRING), item.itemDescription());
            text(row.createCell(4, CellType.STRING), item.workModeCode());
            text(row.createCell(5, CellType.STRING), item.required() ? "是" : "否");
            text(row.createCell(6, CellType.STRING), item.sourceCode());
            text(row.createCell(7, CellType.STRING), answer(item.currentResult()));
            text(row.createCell(8, CellType.STRING), factDescription(item.currentResult()));
            text(row.createCell(9, CellType.STRING), evidenceStatus(item.currentResult()));
        }
    }

    private static String answer(CutoverChecklistView.CurrentResult result) {
        if (result == null) {
            return "";
        }
        return switch (result.resultSourceCode()) {
            case "DIRECT" -> value(result.answerSnapshot());
            case "MANUAL" -> "人工结果已提交";
            case "COLLECTION" -> "采集结果已形成";
            case "EXTERNAL" -> "外部结果已形成";
            default -> throw invalid("清单结果来源非法");
        };
    }

    private static String factDescription(CutoverChecklistView.CurrentResult result) {
        return result == null ? "" : value(result.factDescription());
    }

    private static String evidenceStatus(CutoverChecklistView.CurrentResult result) {
        if (result == null) {
            return "";
        }
        if ("MANUAL".equals(result.resultSourceCode()) && present(result.manualEvidenceFileReference())) {
            return "人工附件已关联";
        }
        if ("COLLECTION".equals(result.resultSourceCode())
                && result.collectionResultReferenceId() != null && result.collectionResultVersion() != null) {
            return "采集结果已关联";
        }
        return "";
    }

    private static void validateItem(CutoverChecklistView.Item item) {
        require(present(item.stableItemKey()) && present(item.itemName()) && present(item.workModeCode())
                && present(item.sourceCode()), "授权清单项投影不完整");
    }

    private static void text(Cell cell, String value) {
        cell.setCellValue(value(value));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw invalid(message);
        }
    }

    private static CutoverChecklistExportException invalid(String message) {
        return new CutoverChecklistExportException(EXPORT_PROJECTION_INVALID, message);
    }

    public record WorkbookContent(byte[] bytes, int businessSurveyRowCount, int riskRowCount) {
    }
}
