package cn.iocoder.yudao.module.pms.service.service.inspection.report;

import lombok.Builder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.io.ClassPathResource;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Renders an already authorized, frozen inspection snapshot without reading or changing business data. */
public final class InspectionReportRenderer {
    private static final String TITLE = "巡检基础报告";
    private static final List<String> COLUMNS = List.of("设备序列号", "检测规则", "类别", "严重程度", "检测状态", "检测值", "检测详情");
    private static final String HANDLING_PROMPT = "请依据设备资料和现场情况制定处理措施";
    private static final Set<String> PASS_OUTCOMES = Set.of("通过", "PASS");
    private static final Set<String> ABNORMAL_OUTCOMES = Set.of("异常", "ABNORMAL");

    public enum Format { PDF, DOCX, XML }

    @Builder
    public record ReportContext(String taskCode, String taskName, String projectName, String customerName,
                                String mode, int revision, String generatedAt, List<ResultRow> results) {
        public ReportContext {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    public record ResultRow(String deviceSerial, String ruleName, String category, String severity,
                            String outcome, String measuredValue, String detail) { }

    public record RenderRequest(ReportContext report, Format format) { }

    public record Rendered(String fileName, String mediaType, byte[] content) { }

    public Rendered render(RenderRequest request) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.report(), "report");
        Objects.requireNonNull(request.format(), "format");
        try {
            return switch (request.format()) {
                case PDF -> new Rendered(fileName(request), "application/pdf", pdf(request.report()));
                case DOCX -> new Rendered(fileName(request),
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx(request.report()));
                case XML -> new Rendered(fileName(request), "application/xml", xml(request.report()));
            };
        } catch (IOException | XMLStreamException ex) {
            throw new IllegalStateException("巡检报告生成失败", ex);
        }
    }

    private static String fileName(RenderRequest request) {
        String code = request.report().taskCode() == null ? "inspection" : request.report().taskCode()
                .replaceAll("[^\\p{L}\\p{N}_-]", "_");
        if (code.isBlank()) code = "inspection";
        code = code.substring(0, code.offsetByCodePoints(0, Math.min(80, code.codePointCount(0, code.length()))));
        return code + "-V" + request.report().revision() + "." + request.format().name().toLowerCase(java.util.Locale.ROOT);
    }

    private static List<String> metadata(ReportContext report) {
        return List.of("任务编号：" + value(report.taskCode()), "任务名称：" + value(report.taskName()),
                "项目：" + value(report.projectName()), "客户：" + value(report.customerName()),
                "巡检方式：" + value(report.mode()), "报告版本：V" + report.revision(),
                "生成时间：" + value(report.generatedAt()));
    }

    private static List<String> cells(ResultRow row) {
        return List.of(value(row.deviceSerial()), value(row.ruleName()), value(row.category()),
                value(row.severity()), value(row.outcome()), value(row.measuredValue()), value(row.detail()));
    }

    private static String value(String text) { return text == null || text.isBlank() ? "—" : text; }

    @Builder
    private record Overview(int total, long passed, long abnormal, long general, long severe, long fatal,
                            List<ResultRow> issues, Map<String, Integer> devices) { }

    private static Overview overview(ReportContext report) {
        List<ResultRow> issues = report.results().stream().filter(row -> row.outcome() != null
                && ABNORMAL_OUTCOMES.contains(row.outcome())).toList();
        long passed = report.results().stream().filter(row -> row.outcome() != null
                && PASS_OUTCOMES.contains(row.outcome())).count();
        Map<String, Integer> devices = new LinkedHashMap<>();
        for (ResultRow row : report.results()) devices.merge(value(row.deviceSerial()), 1, Integer::sum);
        return Overview.builder().total(report.results().size()).passed(passed).abnormal(issues.size())
                .general(issues.stream().filter(row -> "一般".equals(row.severity()) || "GENERAL".equals(row.severity())).count())
                .severe(issues.stream().filter(row -> "严重".equals(row.severity()) || "SEVERE".equals(row.severity())).count())
                .fatal(issues.stream().filter(row -> "致命".equals(row.severity()) || "FATAL".equals(row.severity())).count())
                .issues(issues).devices(devices).build();
    }

    private static List<String> overviewLines(Overview overview) {
        return List.of("检测项总数：" + overview.total(), "通过数：" + overview.passed(), "异常数：" + overview.abnormal(),
                "一般异常数：" + overview.general(), "严重异常数：" + overview.severe(), "致命异常数：" + overview.fatal(),
                "其他结果数：" + (overview.total() - overview.passed() - overview.abnormal()));
    }

    private static List<String> deviceLines(Overview overview) {
        if (overview.devices().isEmpty()) return List.of("暂无设备检测记录");
        return overview.devices().entrySet().stream().map(entry -> "设备序列号：" + entry.getKey()
                + "　检测项数：" + entry.getValue()).toList();
    }

    private static byte[] pdf(ReportContext report) throws IOException {
        try (PDDocument document = new PDDocument();
             InputStream fontInput = new ClassPathResource("inspection/fonts/NpdmsPrintSansSC-Regular.ttf").getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType0Font font = PDType0Font.load(document, fontInput);
            document.getDocumentInformation().setTitle(TITLE);
            document.getDocumentInformation().setAuthor("NPDMS");
            Overview overview = overview(report);
            try (PdfWriter writer = new PdfWriter(document, font)) {
                writer.paragraph(new Paragraph(TITLE, 20));
                for (String field : metadata(report)) writer.paragraph(new Paragraph(field, 11));
                writer.paragraph(new Paragraph("报告概览", 14));
                for (String line : overviewLines(overview)) writer.paragraph(new Paragraph(line, 11));
                writer.paragraph(new Paragraph("设备范围", 14));
                for (String line : deviceLines(overview)) writer.paragraph(new Paragraph(line, 11));
                Paragraph resultHeading = new Paragraph("检测详情（" + report.results().size() + " 项）", 14);
                if (report.results().isEmpty()) {
                    writer.paragraph(resultHeading);
                    writer.paragraph(new Paragraph("暂无检测结果", 11));
                } else {
                    writer.startTable(resultHeading, List.of());
                    for (ResultRow row : report.results()) writer.result(row);
                }
                Paragraph issueHeading = new Paragraph("异常问题清单（" + overview.abnormal() + " 项）", 14);
                if (overview.issues().isEmpty()) {
                    writer.paragraph(issueHeading);
                    writer.paragraph(new Paragraph("暂无异常问题", 11));
                } else {
                    writer.startTable(issueHeading, List.of(new Paragraph("处理措施提示：" + HANDLING_PROMPT, 11)));
                    for (ResultRow issue : overview.issues()) writer.result(issue);
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] docx(ReportContext report) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getProperties().getCoreProperties().setTitle(TITLE);
            Overview overview = overview(report);
            wordParagraph(document.createParagraph(), new Paragraph(TITLE, 20));
            for (String field : metadata(report)) wordParagraph(document.createParagraph(), new Paragraph(field, 11));
            wordParagraph(document.createParagraph(), new Paragraph("报告概览", 14));
            for (String line : overviewLines(overview)) wordParagraph(document.createParagraph(), new Paragraph(line, 11));
            wordParagraph(document.createParagraph(), new Paragraph("设备范围", 14));
            for (String line : deviceLines(overview)) wordParagraph(document.createParagraph(), new Paragraph(line, 11));
            wordParagraph(document.createParagraph(), new Paragraph("检测详情（" + report.results().size() + " 项）", 14));
            if (report.results().isEmpty()) {
                wordParagraph(document.createParagraph(), new Paragraph("暂无检测结果", 11));
            } else {
                var table = document.createTable(1, COLUMNS.size());
                table.setWidth("100%");
                XWPFTableRow header = table.getRow(0);
                header.setRepeatHeader(true);
                wordRow(new WordRow(header, COLUMNS, true));
                for (ResultRow row : report.results()) wordRow(new WordRow(table.createRow(), cells(row), false));
            }
            wordParagraph(document.createParagraph(), new Paragraph("异常问题清单（" + overview.abnormal() + " 项）", 14));
            if (overview.issues().isEmpty()) {
                wordParagraph(document.createParagraph(), new Paragraph("暂无异常问题", 11));
            } else {
                wordParagraph(document.createParagraph(), new Paragraph("处理措施提示：" + HANDLING_PROMPT, 11));
                var issues = document.createTable(1, COLUMNS.size());
                issues.setWidth("100%");
                issues.getRow(0).setRepeatHeader(true);
                wordRow(new WordRow(issues.getRow(0), COLUMNS, true));
                for (ResultRow issue : overview.issues()) wordRow(new WordRow(issues.createRow(), cells(issue), false));
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private record Paragraph(String text, int fontSize) { }
    private record WordRow(XWPFTableRow row, List<String> cells, boolean header) { }

    private static void wordParagraph(XWPFParagraph paragraph, Paragraph content) {
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("NPDMS Print Sans SC");
        run.setFontSize(content.fontSize());
        run.setBold(content.fontSize() > 11);
        String[] lines = content.text().split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) run.addBreak();
            run.setText(lines[i]);
        }
    }

    private static void wordRow(WordRow content) {
        // No fixed row height: Word can split long details across pages without clipping them.
        for (int i = 0; i < content.cells().size(); i++) {
            var cell = content.row().getCell(i);
            wordParagraph(cell.getParagraphs().get(0), new Paragraph(content.cells().get(i), 10));
            if (content.header() || i == 4) {
                cell.setColor(content.header() ? "E4ECF4" : "EDF3F8");
                cell.getParagraphs().get(0).getRuns().forEach(run -> run.setBold(true));
            }
        }
    }

    private static byte[] xml(ReportContext report) throws XMLStreamException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output, "UTF-8");
        try {
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeStartElement("inspectionReport");
            xmlField(writer, new XmlField("title", TITLE));
            xmlField(writer, new XmlField("taskCode", report.taskCode()));
            xmlField(writer, new XmlField("taskName", report.taskName()));
            xmlField(writer, new XmlField("projectName", report.projectName()));
            xmlField(writer, new XmlField("customerName", report.customerName()));
            xmlField(writer, new XmlField("mode", report.mode()));
            xmlField(writer, new XmlField("revision", Integer.toString(report.revision())));
            xmlField(writer, new XmlField("generatedAt", report.generatedAt()));
            Overview overview = overview(report);
            writer.writeStartElement("overview");
            xmlField(writer, new XmlField("totalCount", Integer.toString(overview.total())));
            xmlField(writer, new XmlField("passCount", Long.toString(overview.passed())));
            xmlField(writer, new XmlField("abnormalCount", Long.toString(overview.abnormal())));
            xmlField(writer, new XmlField("otherCount", Long.toString(overview.total() - overview.passed() - overview.abnormal())));
            writer.writeStartElement("abnormalSeverityCounts");
            xmlField(writer, new XmlField("general", Long.toString(overview.general())));
            xmlField(writer, new XmlField("severe", Long.toString(overview.severe())));
            xmlField(writer, new XmlField("fatal", Long.toString(overview.fatal())));
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeStartElement("devices");
            for (Map.Entry<String, Integer> device : overview.devices().entrySet()) {
                writer.writeStartElement("device");
                xmlField(writer, new XmlField("deviceSerial", device.getKey()));
                xmlField(writer, new XmlField("detectionCount", Integer.toString(device.getValue())));
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeStartElement("results");
            for (ResultRow row : report.results()) {
                writer.writeStartElement("result");
                xmlField(writer, new XmlField("deviceSerial", row.deviceSerial()));
                xmlField(writer, new XmlField("ruleName", row.ruleName()));
                xmlField(writer, new XmlField("category", row.category()));
                xmlField(writer, new XmlField("severity", row.severity()));
                xmlField(writer, new XmlField("outcome", row.outcome()));
                xmlField(writer, new XmlField("measuredValue", row.measuredValue()));
                xmlField(writer, new XmlField("detail", row.detail()));
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeStartElement("issues");
            for (ResultRow issue : overview.issues()) {
                writer.writeStartElement("issue");
                xmlField(writer, new XmlField("deviceSerial", issue.deviceSerial()));
                xmlField(writer, new XmlField("ruleName", issue.ruleName()));
                xmlField(writer, new XmlField("category", issue.category()));
                xmlField(writer, new XmlField("severity", issue.severity()));
                xmlField(writer, new XmlField("outcome", issue.outcome()));
                xmlField(writer, new XmlField("measuredValue", issue.measuredValue()));
                xmlField(writer, new XmlField("detail", issue.detail()));
                xmlField(writer, new XmlField("handlingPrompt", HANDLING_PROMPT));
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
            return output.toByteArray();
        } finally {
            writer.close();
        }
    }

    private record XmlField(String name, String value) { }

    private static void xmlField(XMLStreamWriter writer, XmlField field) throws XMLStreamException {
        String text = field.value() == null ? "" : field.value();
        boolean valid = text.codePoints().allMatch(cp -> cp == 9 || cp == 10 || cp == 13
                || cp >= 0x20 && cp <= 0xD7FF || cp >= 0xE000 && cp <= 0xFFFD || cp >= 0x10000 && cp <= 0x10FFFF);
        if (!valid) throw new IllegalArgumentException("报告字段包含 XML 1.0 不支持的字符：" + field.name());
        writer.writeStartElement(field.name());
        writer.writeCharacters(text);
        writer.writeEndElement();
    }

    private static final class PdfWriter implements AutoCloseable {
        private static final PDRectangle PAPER = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        private static final float MARGIN = 38;
        private static final float FONT_SIZE = 8.5f;
        private static final float LINE_HEIGHT = 13;
        private static final float[] WIDTHS = { 95, 125, 65, 60, 85, 85, PAPER.getWidth() - MARGIN * 2 - 515 };
        private final PDDocument document;
        private final PDType0Font font;
        private PDPageContentStream stream;
        private float y;
        private boolean table;

        PdfWriter(PDDocument document, PDType0Font font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PAPER);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            draw(new Text(TITLE + "    第 " + document.getNumberOfPages() + " 页", 9, MARGIN, 24));
            y = PAPER.getHeight() - MARGIN;
            if (table) tableHeader();
        }

        void paragraph(Paragraph paragraph) throws IOException {
            endTable(paragraph);
            for (String line : wrap(new Wrap(paragraph.text(), paragraph.fontSize(), PAPER.getWidth() - MARGIN * 2))) {
                if (y < 65) newPage();
                draw(new Text(line, paragraph.fontSize(), MARGIN, y));
                y -= paragraph.fontSize() * 1.5f;
            }
            y -= 6;
        }

        private void endTable(Paragraph next) {
            // y is below the table border, while paragraphs are positioned by their baseline.
            if (table) y -= next.fontSize() + 6;
            table = false;
        }

        private float paragraphHeight(Paragraph paragraph) throws IOException {
            return wrap(new Wrap(paragraph.text(), paragraph.fontSize(), PAPER.getWidth() - MARGIN * 2)).size()
                    * paragraph.fontSize() * 1.5f + 6;
        }

        void startTable(Paragraph heading, List<Paragraph> introduction) throws IOException {
            endTable(heading);
            float required = paragraphHeight(heading) + 22 + LINE_HEIGHT;
            for (Paragraph paragraph : introduction) required += paragraphHeight(paragraph);
            // Keep the section heading, any introduction, the header and the first result line together.
            if (y - required < 60) newPage();
            paragraph(heading);
            for (Paragraph paragraph : introduction) paragraph(paragraph);
            table = true;
            tableHeader();
        }

        private void tableHeader() throws IOException {
            tableLine(new TableLine(COLUMNS, 22, true));
        }

        void result(ResultRow row) throws IOException {
            List<List<String>> lines = new ArrayList<>();
            List<String> values = cells(row);
            for (int i = 0; i < values.size(); i++) lines.add(wrap(new Wrap(values.get(i), FONT_SIZE, WIDTHS[i] - 8)));
            int count = lines.stream().mapToInt(List::size).max().orElse(1);
            int offset = 0;
            while (offset < count) {
                int available = (int) ((y - 52 - 8) / LINE_HEIGHT);
                if (available < 1) { newPage(); continue; }
                int chunk = Math.min(available, count - offset);
                int renderedLines = chunk;
                boolean[] repeat = new boolean[lines.size()];
                for (int column = 0; column < lines.size(); column++) {
                    int length = lines.get(column).size();
                    repeat[column] = (column == 0 || column == 4) && offset >= length && length <= available;
                    if (repeat[column]) renderedLines = Math.max(renderedLines, length);
                }
                for (int line = 0; line < renderedLines; line++) {
                    List<String> valuesAtLine = new ArrayList<>();
                    for (int column = 0; column < lines.size(); column++) {
                        List<String> cell = lines.get(column);
                        // Repeat a completed identity/status in full only when it fits on this page.
                        // Longer cells continue at their original offset, preserving every source line.
                        int index = repeat[column] ? line : offset + line;
                        valuesAtLine.add(index < cell.size() ? cell.get(index) : "");
                    }
                    tableLine(new TableLine(valuesAtLine, LINE_HEIGHT, false));
                }
                offset += chunk;
                y -= 5;
                if (offset < count) newPage();
            }
        }

        private record Text(String value, float size, float x, float y) { }
        private record Wrap(String value, float size, float width) { }
        private record TableLine(List<String> values, float height, boolean header) { }

        private void tableLine(TableLine line) throws IOException {
            float x = MARGIN;
            for (int i = 0; i < line.values().size(); i++) {
                if (line.header() || i == 4) {
                    stream.setNonStrokingColor(0.92f, 0.95f, 0.98f);
                    stream.addRect(x, y - line.height(), WIDTHS[i], line.height());
                    stream.fill();
                    stream.setNonStrokingColor(0f, 0f, 0f);
                }
                stream.setStrokingColor(0.7f, 0.7f, 0.7f);
                stream.setLineWidth(0.3f);
                stream.addRect(x, y - line.height(), WIDTHS[i], line.height());
                stream.stroke();
                draw(new Text(line.values().get(i), FONT_SIZE, x + 4, y - FONT_SIZE - 2));
                x += WIDTHS[i];
            }
            y -= line.height();
        }

        private List<String> wrap(Wrap text) throws IOException {
            List<String> result = new ArrayList<>();
            for (String paragraph : text.value().split("\\R", -1)) {
                StringBuilder line = new StringBuilder();
                float width = 0;
                for (int cp : supported(paragraph).codePoints().toArray()) {
                    String character = new String(Character.toChars(cp));
                    float advance = font.getStringWidth(character) / 1000 * text.size();
                    if (width + advance > text.width() && !line.isEmpty()) {
                        result.add(line.toString());
                        line.setLength(0);
                        width = 0;
                    }
                    line.append(character);
                    width += advance;
                }
                result.add(line.toString());
            }
            return result;
        }

        private String supported(String text) throws IOException {
            StringBuilder result = new StringBuilder();
            for (int cp : text.codePoints().toArray()) {
                if (cp == '\t') { result.append("    "); continue; }
                if (Character.isISOControl(cp)) continue;
                String character = new String(Character.toChars(cp));
                try { font.encode(character); result.append(character); }
                catch (IllegalArgumentException unsupported) { result.append('□'); }
            }
            return result.toString();
        }

        private void draw(Text text) throws IOException {
            stream.beginText();
            stream.setFont(font, text.size());
            stream.newLineAtOffset(text.x(), text.y());
            stream.showText(text.value());
            stream.endText();
        }

        @Override public void close() throws IOException { if (stream != null) stream.close(); }
    }
}
