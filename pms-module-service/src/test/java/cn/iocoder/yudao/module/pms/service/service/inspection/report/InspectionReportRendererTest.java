package cn.iocoder.yudao.module.pms.service.service.inspection.report;

import cn.iocoder.yudao.module.pms.service.service.inspection.report.InspectionReportRenderer.Format;
import cn.iocoder.yudao.module.pms.service.service.inspection.report.InspectionReportRenderer.RenderRequest;
import cn.iocoder.yudao.module.pms.service.service.inspection.report.InspectionReportRenderer.ReportContext;
import cn.iocoder.yudao.module.pms.service.service.inspection.report.InspectionReportRenderer.ResultRow;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InspectionReportRendererTest {
    private final InspectionReportRenderer renderer = new InspectionReportRenderer();

    @Test
    void pdfEmbedsChineseFontAndKeepsLongResultAndLastDeviceAcrossPages() throws Exception {
        List<ResultRow> rows = new ArrayList<>();
        rows.add(new ResultRow("SN-FIRST", "链路健康检测", "网络", "严重", "检测失败", "低于阈值",
                "中文故障详情<&>\n".repeat(150) + "长详情末尾证据"));
        for (int i = 0; i < 75; i++) {
            rows.add(new ResultRow("SN-" + i, "风扇检测", "硬件", "一般", "检测通过", "正常", "设备检测结果"));
        }
        rows.add(new ResultRow("SN-LAST", "末台设备检测", "网络", "提示", "未完成检测", "—", "末台设备关键证据"));

        var rendered = renderer.render(new RenderRequest(report(rows), Format.PDF));
        assertEquals("application/pdf", rendered.mediaType());
        assertEquals("INSP-001-V3.pdf", rendered.fileName());
        assertEquals("%PDF-", new String(rendered.content(), 0, 5, StandardCharsets.US_ASCII));
        try (var document = Loader.loadPDF(rendered.content())) {
            assertTrue(document.getNumberOfPages() > 3, "长详情和长表均应正常分页");
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("巡检基础报告"));
            assertTrue(text.contains("华东网络项目"));
            assertTrue(text.contains("客户甲"));
            assertTrue(text.contains("检测失败"));
            assertTrue(text.contains("中文故障详情<&>"));
            assertTrue(text.contains("长详情末尾证据"));
            assertTrue(text.contains("SN-LAST"));
            assertTrue(text.contains("末台设备关键证据"));
            for (int i = 0; i < 75; i++) assertTrue(text.contains("SN-" + i + " "), "漏出设备 SN-" + i);
            boolean embedded = false;
            for (var page : document.getPages()) {
                for (var name : page.getResources().getFontNames()) embedded |= page.getResources().getFont(name).isEmbedded();
            }
            assertTrue(embedded, "报告应可脱离宿主机字体阅读中文");
        }
    }

    @Test
    void docxIsReadableOoxmlAndPreservesUnicodeSpecialCharactersAndLongTable() throws Exception {
        String details = "配置检查<&>：保留原始字符\n".repeat(120) + "长详情终点";
        List<ResultRow> rows = new ArrayList<>();
        rows.add(new ResultRow("SN-001", "配置检测", "安全", "严重", "检测失败", "A&B < 10", details));
        for (int i = 0; i < 100; i++) rows.add(new ResultRow("SN-" + i, "端口检测", "网络", "提示", "检测通过", "正常", "第" + i + "条结果"));
        var rendered = renderer.render(new RenderRequest(report(rows), Format.DOCX));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", rendered.mediaType());
        assertEquals("INSP-001-V3.docx", rendered.fileName());
        assertEquals("PK", new String(rendered.content(), 0, 2, StandardCharsets.US_ASCII));
        try (var document = new XWPFDocument(new ByteArrayInputStream(rendered.content()));
             var extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            assertTrue(text.contains("巡检基础报告"));
            assertTrue(text.contains("华东网络项目"));
            assertTrue(text.contains("客户甲"));
            assertTrue(text.contains("检测失败"));
            assertTrue(text.contains("配置检查<&>：保留原始字符"));
            assertTrue(text.contains("A&B < 10"));
            assertTrue(text.contains("长详情终点"));
            assertTrue(text.contains("第99条结果"));
            assertEquals(102, document.getTables().get(0).getNumberOfRows());
            assertEquals(7, document.getTables().get(0).getRow(0).getTableCells().size());
            assertTrue(document.getTables().get(0).getRow(0).isRepeatHeader());
        }
    }

    @Test
    void xmlRoundTripsAllSnapshotFieldsAndEscapesCharacters() throws Exception {
        String special = "中文设备<&\"'>\n下一行🙂";
        var context = ReportContext.builder().taskCode("../INSP:001\r\n").taskName(special).projectName("项目甲")
                .customerName("客户甲").mode("现场巡检").revision(7).generatedAt("2026-10-15 12:30")
                .results(List.of(new ResultRow("SN<&>", special, "网络", "严重", "检测失败", "A&B", special))).build();
        var rendered = renderer.render(new RenderRequest(context, Format.XML));
        assertEquals("application/xml", rendered.mediaType());
        assertTrue(rendered.fileName().endsWith("-V7.xml"));
        assertFalse(rendered.fileName().contains("/"));
        assertFalse(rendered.fileName().contains(":"));
        assertFalse(rendered.fileName().contains("\r"));
        assertFalse(rendered.fileName().contains("\n"));
        String text = new String(rendered.content(), StandardCharsets.UTF_8);
        assertTrue(text.contains("中文设备"));
        assertTrue(text.contains("&lt;"));
        assertTrue(text.contains("&amp;"));
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(rendered.content()));
        assertEquals("inspectionReport", document.getDocumentElement().getTagName());
        assertEquals(special, document.getElementsByTagName("taskName").item(0).getTextContent());
        assertEquals(special, document.getElementsByTagName("detail").item(0).getTextContent());
        assertEquals("SN<&>", document.getElementsByTagName("deviceSerial").item(0).getTextContent());
        assertEquals("检测失败", document.getElementsByTagName("outcome").item(0).getTextContent());
        assertEquals("7", document.getElementsByTagName("revision").item(0).getTextContent());
    }

    @Test
    void emptySnapshotsProduceActualReadableDocuments() throws Exception {
        try (var pdf = Loader.loadPDF(renderer.render(new RenderRequest(report(List.of()), Format.PDF)).content())) {
            assertTrue(new PDFTextStripper().getText(pdf).contains("暂无检测结果"));
        }
        try (var docx = new XWPFDocument(new ByteArrayInputStream(
                renderer.render(new RenderRequest(report(List.of()), Format.DOCX)).content()));
             var extractor = new XWPFWordExtractor(docx)) {
            assertTrue(extractor.getText().contains("暂无检测结果"));
        }
    }

    @Test
    void rejectsInvalidXmlCharactersInsteadOfCreatingAnUnreadableReport() {
        var context = report(List.of(new ResultRow("SN-1", "检测", "网络", "严重", "失败", "值", "非法\u0001字符")));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(new RenderRequest(context, Format.XML)));
        assertThrows(NullPointerException.class, () -> renderer.render(new RenderRequest(context, null)));
    }

    @Test
    void pdfAddsOverviewSeverityCountsAndPaginatedIndependentIssueList() throws Exception {
        try (var document = Loader.loadPDF(renderer.render(new RenderRequest(report(summaryRows()), Format.PDF)).content())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("报告概览"));
            assertTrue(text.contains("设备范围"));
            assertTrue(text.contains("通过数：2"));
            assertTrue(text.contains("异常数：4"));
            assertTrue(text.contains("一般异常数：1"));
            assertTrue(text.contains("严重异常数：2"));
            assertTrue(text.contains("致命异常数：1"));
            assertTrue(text.contains("其他结果数：1"));
            assertTrue(text.contains("设备型号：AR6280"));
            String issues = text.substring(text.indexOf("异常问题清单"));
            assertTrue(issues.contains("一般问题"));
            assertTrue(issues.contains("严重问题甲"));
            assertTrue(issues.contains("严重问题乙"));
            assertTrue(issues.contains("致命问题"));
            assertTrue(issues.contains("独立问题清单末尾证据"));
            assertTrue(issues.contains("请依据设备资料和现场情况制定处理措施"));
            assertFalse(issues.contains("正常检测"));
            assertFalse(issues.contains("终端执行错误"));
            assertTrue(document.getNumberOfPages() > 3, "检测详情及独立长问题清单均应正常分页");
        }
    }

    @Test
    void pdfLeavesClearSpaceBetweenResultsTableAndIssueHeading() throws Exception {
        try (var document = Loader.loadPDF(renderer.render(new RenderRequest(report(layoutRows()), Format.PDF)).content())) {
            var layout = new PdfLayout();
            layout.getText(document);
            var tableTail = layout.find("TABLE-END-03");
            var heading = layout.find("异常问题清单");

            assertEquals(14, heading.size(), "章节标题不能通过缩小字体回避布局问题");
            assertTrue(heading.page() >= tableTail.page());
            if (heading.page() == tableTail.page()) {
                float tableBottom = cellBottom(document, tableTail);
                assertTrue(heading.baseline() + heading.size() <= tableBottom - 6,
                        "异常问题清单标题顶部必须与上一表格底边保留清楚间距");
            }
        }
    }

    @Test
    void pdfKeepsDetectionHeadingWithTableHeaderAndFirstResultLine() throws Exception {
        try (var document = Loader.loadPDF(renderer.render(new RenderRequest(report(layoutRows()), Format.PDF)).content())) {
            var layout = new PdfLayout();
            layout.getText(document);
            var heading = layout.find("检测详情");

            assertTrue(layout.pageText(heading.page()).contains("设备序列号检测规则类别"),
                    "检测详情标题不能孤立在表头的上一页");
            assertEquals(heading.page(), layout.find("DEVICE02-DETAIL-START").page(),
                    "检测详情标题、表头和至少第一行结果应在同页");
        }
    }

    @Test
    void pdfRepeatsCompleteWrappedSerialAndStatusOnContinuationWithoutLosingDetailLines() throws Exception {
        String serial = "NATIVE-UI-20261012-DEVICE-03";
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < 70; i++) detail.append("CONT-LINE-%03d".formatted(i)).append('\n');
        var rows = List.of(layoutRows().getFirst(),
                new ResultRow(serial, "合成延迟", "基础", "一般", "异常", "99ms", detail.toString()));
        try (var document = Loader.loadPDF(renderer.render(new RenderRequest(report(rows), Format.PDF)).content())) {
            var layout = new PdfLayout();
            layout.getText(document);
            float serialLeft = layout.find("设备序列号检测规则").x();
            float serialRight = layout.find("检测规则").x();
            float statusLeft = layout.find("检测状态").x();
            float statusRight = layout.find("检测值").x();
            int detailPages = 0;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                if (!layout.pageText(page).contains("CONT-LINE-")) continue;
                detailPages++;
                assertTrue(layout.columnText(new PdfColumn(page, serialLeft, serialRight)).contains(serial),
                        "第" + page + "页的续表必须重复完整的两行设备序列号");
                assertTrue(layout.columnText(new PdfColumn(page, statusLeft, statusRight)).contains("异常"),
                        "第" + page + "页的续表必须保留原检测状态");
            }
            assertTrue(detailPages >= 2, "回归样例必须实际跨页");
            String text = layout.characters.toString();
            for (int i = 0; i < 70; i++) {
                String marker = "CONT-LINE-%03d".formatted(i);
                assertEquals(2, text.split(marker, -1).length - 1,
                        marker + "必须在检测明细和独立异常清单中各保留一次");
            }
        }
    }

    @Test
    void docxAddsSeverityOverviewAndASeparateIssuesTableContainingOnlyAbnormalResults() throws Exception {
        try (var document = new XWPFDocument(new ByteArrayInputStream(
                renderer.render(new RenderRequest(report(summaryRows()), Format.DOCX)).content()));
             var extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            assertTrue(text.contains("通过数：2"));
            assertTrue(text.contains("异常数：4"));
            assertTrue(text.contains("一般异常数：1"));
            assertTrue(text.contains("严重异常数：2"));
            assertTrue(text.contains("致命异常数：1"));
            assertTrue(text.contains("设备范围"));
            assertTrue(text.contains("软件版本：R1"));
            assertEquals(2, document.getTables().size());
            var issues = document.getTables().get(1);
            assertEquals(5, issues.getNumberOfRows());
            assertTrue(issues.getRow(0).isRepeatHeader());
            String issuesText = issues.getText();
            assertTrue(issuesText.contains("一般问题"));
            assertTrue(issuesText.contains("严重问题甲"));
            assertTrue(issuesText.contains("严重问题乙"));
            assertTrue(issuesText.contains("独立问题清单末尾证据"));
            assertFalse(issuesText.contains("正常检测"));
            assertFalse(issuesText.contains("终端执行错误"));
            assertTrue(text.contains("请依据设备资料和现场情况制定处理措施"));
        }
    }

    @Test
    void xmlAddsExactCountsGroupedDevicesAndIndependentIssuesWithoutChangingDetailedResults() throws Exception {
        var rows = summaryRows();
        byte[] content = renderer.render(new RenderRequest(report(rows), Format.XML)).content();
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(content));
        var overview = (Element) document.getElementsByTagName("overview").item(0);
        assertEquals("7", overview.getElementsByTagName("totalCount").item(0).getTextContent());
        assertEquals("2", overview.getElementsByTagName("passCount").item(0).getTextContent());
        assertEquals("4", overview.getElementsByTagName("abnormalCount").item(0).getTextContent());
        assertEquals("1", overview.getElementsByTagName("otherCount").item(0).getTextContent());
        assertEquals("1", overview.getElementsByTagName("general").item(0).getTextContent());
        assertEquals("2", overview.getElementsByTagName("severe").item(0).getTextContent());
        assertEquals("1", overview.getElementsByTagName("fatal").item(0).getTextContent());
        var devices = (Element) document.getElementsByTagName("devices").item(0);
        assertEquals(5, devices.getElementsByTagName("device").getLength());
        var firstDevice = (Element) devices.getElementsByTagName("device").item(0);
        assertEquals("SN-A", firstDevice.getElementsByTagName("deviceSerial").item(0).getTextContent());
        assertEquals("2", firstDevice.getElementsByTagName("detectionCount").item(0).getTextContent());
        var results = (Element) document.getElementsByTagName("results").item(0);
        assertEquals(7, results.getElementsByTagName("result").getLength());
        var issues = (Element) document.getElementsByTagName("issues").item(0);
        assertEquals(4, issues.getElementsByTagName("issue").getLength());
        assertFalse(issues.getTextContent().contains("正常检测"));
        assertFalse(issues.getTextContent().contains("终端执行错误"));
        var fatalIssue = (Element) issues.getElementsByTagName("issue").item(3);
        assertEquals(rows.get(5).detail(), fatalIssue.getElementsByTagName("detail").item(0).getTextContent());
        assertEquals("请依据设备资料和现场情况制定处理措施", fatalIssue.getElementsByTagName("handlingPrompt").item(0).getTextContent());
    }

    private List<ResultRow> summaryRows() {
        String longDetail = "设备型号：未识别；软件版本：未识别\n检测ID：D-3；预期阈值：10ms\n"
                + "实际输出<&>：异常证据\n".repeat(140) + "独立问题清单末尾证据";
        return List.of(new ResultRow("SN-A", "正常检测", "网络", "GENERAL", "PASS", "10ms", "设备型号：AR6280；软件版本：R1"),
                new ResultRow("SN-NORMAL", "正常检测", "网络", "致命", "通过", "10ms", "正常检测证据"),
                new ResultRow("SN-A", "一般问题", "网络", "GENERAL", "ABNORMAL", "11ms", "一般问题详情"),
                new ResultRow("SN-B", "严重问题甲", "网络", "严重", "异常", "12ms", "严重问题详情甲"),
                new ResultRow("SN-B", "严重问题乙", "网络", "SEVERE", "ABNORMAL", "13ms", "严重问题详情乙"),
                new ResultRow("SN-C", "致命问题", "网络", "FATAL", "异常", "14ms", longDetail),
                new ResultRow("SN-D", "终端执行错误", "网络", "FATAL", "ERROR", "—", "执行错误保持原状态"));
    }

    private List<ResultRow> layoutRows() {
        String detail = "设备型号：未识别；软件版本：未识别\n检测ID：合成延迟规则\n命令：show version\n"
                + "预期正则：AcceptanceLatency:\n预期阈值：10ms\n实际输出：模拟巡检日志\n"
                + "EvidencePurpose: SIMULATED_ACCEPTANCE_ONLY\nAcceptanceLatency: 99ms\n\n"
                + "判定说明：保留原检测结果\n平台登记信息：模拟验收\n";
        return List.of(new ResultRow("NATIVE-UI-20261012-DEVICE-02", "合成延迟", "基础", "一般", "通过", "10ms",
                        "DEVICE02-DETAIL-START\n" + detail + "TABLE-END-02"),
                new ResultRow("NATIVE-UI-20261012-DEVICE-03", "合成延迟", "基础", "一般", "异常", "99ms",
                        detail + "TABLE-END-03"));
    }

    private record PdfGlyph(int page, String text, float x, float baseline, float size) { }
    private record PdfColumn(int page, float left, float right) { }

    private static final class PdfLayout extends PDFTextStripper {
        private final StringBuilder characters = new StringBuilder();
        private final List<PdfGlyph> glyphs = new ArrayList<>();
        private final List<PdfGlyph> characterPositions = new ArrayList<>();

        private PdfLayout() throws IOException { }

        @Override
        protected void processTextPosition(TextPosition position) {
            var glyph = new PdfGlyph(getCurrentPageNo(), position.getUnicode(),
                    position.getTextMatrix().getTranslateX(), position.getTextMatrix().getTranslateY(),
                    position.getFontSizeInPt());
            glyphs.add(glyph);
            characters.append(glyph.text());
            for (int i = 0; i < glyph.text().length(); i++) characterPositions.add(glyph);
            super.processTextPosition(position);
        }

        private PdfGlyph find(String text) {
            int index = characters.indexOf(text);
            assertTrue(index >= 0, "PDF缺少文字：" + text);
            return characterPositions.get(index);
        }

        private String pageText(int page) {
            StringBuilder text = new StringBuilder();
            glyphs.stream().filter(glyph -> glyph.page() == page).forEach(glyph -> text.append(glyph.text()));
            return text.toString();
        }

        private String columnText(PdfColumn column) {
            StringBuilder text = new StringBuilder();
            glyphs.stream().filter(glyph -> glyph.page() == column.page()
                    && glyph.x() >= column.left() - 0.5f && glyph.x() < column.right() - 0.5f)
                    .forEach(glyph -> text.append(glyph.text()));
            return text.toString();
        }
    }

    private float cellBottom(PDDocument document, PdfGlyph glyph) throws IOException {
        // Inspect the emitted PDF's rectangles, rather than reusing the renderer's row-height arithmetic.
        var tokens = new PDFStreamParser(document.getPage(glyph.page() - 1)).parse();
        float bottom = Float.POSITIVE_INFINITY;
        for (int i = 4; i < tokens.size(); i++) {
            if (!(tokens.get(i) instanceof Operator operator) || !"re".equals(operator.getName())) continue;
            float x = ((COSNumber) tokens.get(i - 4)).floatValue();
            float y = ((COSNumber) tokens.get(i - 3)).floatValue();
            float width = ((COSNumber) tokens.get(i - 2)).floatValue();
            float height = ((COSNumber) tokens.get(i - 1)).floatValue();
            if (glyph.x() >= x && glyph.x() <= x + width
                    && glyph.baseline() >= y && glyph.baseline() <= y + height) bottom = Math.min(bottom, y);
        }
        assertNotEquals(Float.POSITIVE_INFINITY, bottom, "末行证据应位于实际绘制的表格单元中");
        return bottom;
    }

    private ReportContext report(List<ResultRow> results) {
        return ReportContext.builder().taskCode("INSP-001").taskName("现场设备巡检")
                .projectName("华东网络项目").customerName("客户甲").mode("现场巡检")
                .revision(3).generatedAt("2026-10-15 12:30").results(results).build();
    }
}
