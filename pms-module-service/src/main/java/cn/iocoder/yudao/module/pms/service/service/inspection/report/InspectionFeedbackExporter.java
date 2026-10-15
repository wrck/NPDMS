package cn.iocoder.yudao.module.pms.service.service.inspection.report;

import lombok.Builder;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Renders already authorized feedback rows; it neither queries nor writes business data. */
public final class InspectionFeedbackExporter {
    private static final List<String> HEADERS = List.of("检测ID", "检测项目", "规则版本", "检测分类", "严重级别",
            "执行命令", "检测输出", "反馈原因", "标注人", "标注时间", "巡检任务编码", "设备序列号", "产品型号", "确认情况", "当前状态");
    private static final int[] COLUMN_WIDTHS = { 20, 28, 18, 20, 14, 48, 64, 40, 20, 24, 24, 24, 24, 28, 18 };

    public enum Format { XLSX, CSV }
    public record Request(Format format, List<Row> rows) { }
    @Builder
    public record Row(String detectionId, String inspectionItem, String ruleRevision, String category,
                      String severity, String commands, String output, String reason, String annotator,
                      String annotatedAt, String taskCode, String deviceSerial, String productModel,
                      String confirmation, String currentStatus) { }
    public record Exported(String fileName, String mediaType, byte[] content) { }

    public Exported render(Request request) {
        if (request == null || request.format() == null) throw new IllegalArgumentException("请选择巡检反馈导出格式");
        if (request.rows() == null) throw new IllegalArgumentException("请提供巡检反馈记录列表，允许空列表");
        if (request.format() == Format.XLSX && request.rows().size() >= SpreadsheetVersion.EXCEL2007.getMaxRows()) {
            throw new IllegalArgumentException("反馈行数超过XLSX格式上限，请改用CSV导出");
        }
        for (Row row : request.rows()) {
            if (row == null) throw new IllegalArgumentException("巡检反馈导出数据不能包含空记录");
            if (request.format() == Format.XLSX && cells(row).stream()
                    .anyMatch(cell -> cell.length() > SpreadsheetVersion.EXCEL2007.getMaxTextLength())) {
                throw new IllegalArgumentException("反馈字段超过XLSX单元格字符上限，请改用CSV保留完整内容");
            }
        }
        try {
            return switch (request.format()) {
                case XLSX -> new Exported("巡检反馈导出.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx(request.rows()));
                case CSV -> new Exported("巡检反馈导出.csv", "text/csv;charset=UTF-8", csv(request.rows()));
            };
        } catch (IOException failed) {
            throw new IllegalStateException("巡检反馈文件生成失败");
        }
    }

    private record SheetRow(org.apache.poi.ss.usermodel.Row target, List<String> values, CellStyle style) { }

    private static byte[] xlsx(List<Row> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("巡检反馈");
            CellStyle body = workbook.createCellStyle();
            body.setWrapText(true);
            body.setVerticalAlignment(VerticalAlignment.TOP);
            body.setDataFormat(workbook.createDataFormat().getFormat("@"));
            CellStyle header = workbook.createCellStyle();
            header.cloneStyleFrom(body);
            var font = workbook.createFont();
            font.setBold(true);
            header.setFont(font);
            sheet.setDefaultRowHeightInPoints(36);
            sheet.createRow(0).setHeightInPoints(24);
            writeRow(new SheetRow(sheet.getRow(0), HEADERS, header));
            for (int index = 0; index < rows.size(); index++) {
                writeRow(new SheetRow(sheet.createRow(index + 1), cells(rows.get(index)), body));
            }
            for (int index = 0; index < COLUMN_WIDTHS.length; index++) sheet.setColumnWidth(index, COLUMN_WIDTHS[index] * 256);
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, rows.size(), 0, HEADERS.size() - 1));
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void writeRow(SheetRow row) {
        for (int index = 0; index < row.values().size(); index++) {
            var cell = row.target().createCell(index, CellType.STRING);
            cell.setCellValue(row.values().get(index));
            cell.setCellStyle(row.style());
        }
    }

    private static byte[] csv(List<Row> rows) {
        StringBuilder output = new StringBuilder("\uFEFF");
        csvRow(new CsvRow(output, HEADERS));
        for (Row row : rows) csvRow(new CsvRow(output, cells(row)));
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private record CsvRow(StringBuilder output, List<String> values) { }

    private static void csvRow(CsvRow row) {
        for (int index = 0; index < row.values().size(); index++) {
            if (index > 0) row.output().append(',');
            String cell = row.values().get(index);
            if (formulaPrefix(cell)) cell = "'" + cell;
            row.output().append('"').append(cell.replace("\"", "\"\"")).append('"');
        }
        row.output().append("\r\n");
    }

    private static boolean formulaPrefix(String cell) {
        for (int index = 0; index < cell.length();) {
            int character = cell.codePointAt(index);
            if (Character.isISOControl(character)) return true;
            if (Character.isWhitespace(character) || Character.isSpaceChar(character) || character == 0xFEFF) {
                index += Character.charCount(character);
                continue;
            }
            return character == '=' || character == '+' || character == '-' || character == '@';
        }
        return false;
    }

    private static List<String> cells(Row row) {
        return List.of(value(row.detectionId()), value(row.inspectionItem()), value(row.ruleRevision()), value(row.category()),
                value(row.severity()), value(row.commands()), value(row.output()), value(row.reason()), value(row.annotator()),
                value(row.annotatedAt()), value(row.taskCode()), value(row.deviceSerial()), value(row.productModel()),
                value(row.confirmation()), value(row.currentStatus()));
    }

    private static String value(String value) { return value == null ? "" : value; }
}
