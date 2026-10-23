package cn.iocoder.yudao.module.pms.service.service.inspection.report;

import cn.iocoder.yudao.module.pms.service.service.inspection.report.InspectionFeedbackExporter.Format;
import cn.iocoder.yudao.module.pms.service.service.inspection.report.InspectionFeedbackExporter.Request;
import cn.iocoder.yudao.module.pms.service.service.inspection.report.InspectionFeedbackExporter.Row;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InspectionFeedbackExporterTest {
    private static final List<String> HEADERS = List.of("检测ID", "检测项目", "规则版本", "检测分类", "严重级别",
            "执行命令", "检测输出", "反馈原因", "标注人", "标注时间", "巡检任务编码", "设备序列号", "产品型号", "确认情况", "当前状态");
    private final InspectionFeedbackExporter exporter = new InspectionFeedbackExporter();

    @Test
    void realXlsxRoundTripsFifteenColumnsUnicodeLongOutputAndLiteralFormulaPrefixes() throws Exception {
        String longOutput = "中文输出,\"引用\"\n第二行🙂\r\n".repeat(250) + "最后完整内容";
        Row row = Row.builder().detectionId("00017").inspectionItem("链路检测").ruleRevision("V2")
                .category("路由检测").severity("严重").commands("=SUM(1,2)\nshow route")
                .output(longOutput).reason("  +人工核对").annotator("测试标注人").annotatedAt("2026-10-15 10:00:00")
                .taskCode("0012").deviceSerial("00001234").productModel("产品型号")
                .confirmation("已确认,保留\"原文\"").currentStatus("处理中").build();
        var file = exporter.render(new Request(Format.XLSX, List.of(row)));
        assertTrue(file.fileName().endsWith(".xlsx"));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file.mediaType());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(2, sheet.getPhysicalNumberOfRows());
            assertEquals(15, sheet.getRow(0).getLastCellNum());
            assertEquals(15, sheet.getRow(1).getLastCellNum());
            for (int column = 0; column < HEADERS.size(); column++) assertEquals(HEADERS.get(column), sheet.getRow(0).getCell(column).getStringCellValue());
            List<String> expected = List.of("00017", "链路检测", "V2", "路由检测", "严重", "=SUM(1,2)\nshow route",
                    longOutput, "  +人工核对", "测试标注人", "2026-10-15 10:00:00", "0012", "00001234", "产品型号", "已确认,保留\"原文\"", "处理中");
            for (int column = 0; column < expected.size(); column++) {
                var cell = sheet.getRow(1).getCell(column);
                assertEquals(CellType.STRING, cell.getCellType());
                assertEquals(expected.get(column), cell.getStringCellValue());
                assertTrue(cell.getCellStyle().getWrapText());
            }
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
            assertEquals("A1:O2", sheet.getCTWorksheet().getAutoFilter().getRef());
        }
    }

    @Test
    void csvParsesUnicodeCommasQuotesAndEmbeddedLineBreaksAsOneFifteenColumnRow() {
        String output = "输出第一行,含逗号\r\n第二行包含\"引号\"\n末行🙂";
        Row row = Row.builder().detectionId("00017").inspectionItem("中文检测项目")
                .commands("show a,show b").output(output).reason("原因,\"人工核验\"")
                .annotator("标注人甲").currentStatus("待确认").build();
        var file = exporter.render(new Request(Format.CSV, List.of(row)));
        assertTrue(file.fileName().endsWith(".csv"));
        assertEquals("text/csv;charset=UTF-8", file.mediaType());
        assertArrayEquals(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF }, Arrays.copyOf(file.content(), 3));
        var records = readCsv(file.content());
        assertEquals(2, records.size());
        assertEquals(HEADERS, records.get(0));
        assertEquals(List.of("00017", "中文检测项目", "", "", "", "show a,show b", output,
                "原因,\"人工核验\"", "标注人甲", "", "", "", "", "", "待确认"), records.get(1));
        assertEquals(15, records.get(1).size());
    }

    @Test
    void csvNeutralizesFormulaPrefixesAfterWhitespaceWithoutTrimmingOriginalText() {
        List<String> prefixes = List.of("=1+1", "+SUM(1,2)", "-12", "@SUM(1,2)", "   =1+1",
                "\u00A0+1", "\u2000@SUM(1,2)", "\uFEFF-1", "\tplain text", "\rplain text", "\nplain text");
        List<Row> rows = prefixes.stream().map(value -> Row.builder().reason(value).build()).toList();
        var records = readCsv(exporter.render(new Request(Format.CSV, rows)).content());
        assertEquals(prefixes.size() + 1, records.size());
        for (int index = 0; index < prefixes.size(); index++) {
            assertEquals(15, records.get(index + 1).size());
            assertEquals("'" + prefixes.get(index), records.get(index + 1).get(7));
        }
        String ordinary = " 正常输出\n=后续行原文";
        var safe = readCsv(exporter.render(new Request(Format.CSV, List.of(Row.builder().output(ordinary).build()))).content());
        assertEquals(ordinary, safe.get(1).get(6));
    }

    @Test
    void bothFormatsExportHeadersForEmptyRowsAndKeepNullFieldsEmpty() throws Exception {
        assertEquals(List.of(HEADERS), readCsv(exporter.render(new Request(Format.CSV, List.of())).content()));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(exporter.render(new Request(Format.XLSX, List.of())).content()))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(1, sheet.getPhysicalNumberOfRows());
            assertEquals(15, sheet.getRow(0).getLastCellNum());
            assertEquals("A1:O1", sheet.getCTWorksheet().getAutoFilter().getRef());
        }
        var records = readCsv(exporter.render(new Request(Format.CSV, List.of(Row.builder().build()))).content());
        assertEquals(15, records.get(1).size());
        assertTrue(records.get(1).stream().allMatch(String::isEmpty));
    }

    @Test
    void rejectsInvalidRequestsAndOffersCsvInsteadOfSilentlyTruncatingOversizeXlsxCells() {
        assertThrows(IllegalArgumentException.class, () -> exporter.render(null));
        assertThrows(IllegalArgumentException.class, () -> exporter.render(new Request(null, List.of())));
        assertThrows(IllegalArgumentException.class, () -> exporter.render(new Request(Format.CSV, null)));
        assertThrows(IllegalArgumentException.class, () -> exporter.render(new Request(Format.CSV, Arrays.asList((Row) null))));
        String longOutput = "值".repeat(32768) + "结尾";
        Row row = Row.builder().output(longOutput).build();
        var error = assertThrows(IllegalArgumentException.class, () -> exporter.render(new Request(Format.XLSX, List.of(row))));
        assertTrue(error.getMessage().contains("CSV"));
        assertEquals(longOutput, readCsv(exporter.render(new Request(Format.CSV, List.of(row))).content()).get(1).get(6));
    }

    /** Independent CSV reader checks record boundaries and unescaped values, not writer internals. */
    private static List<List<String>> readCsv(byte[] content) {
        String csv = new String(content, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 1; index < csv.length(); index++) {
            char character = csv.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else quoted = !quoted;
            } else if (!quoted && character == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (!quoted && character == '\r') {
                assertTrue(index + 1 < csv.length() && csv.charAt(index + 1) == '\n', "CSV记录必须使用CRLF分隔");
                index++;
                row.add(cell.toString());
                rows.add(List.copyOf(row));
                row.clear();
                cell.setLength(0);
            } else cell.append(character);
        }
        assertFalse(quoted, "CSV引号必须闭合");
        assertTrue(row.isEmpty() && cell.isEmpty(), "末条记录应完整结束");
        return rows;
    }
}
