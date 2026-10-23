package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import java.io.*;
import java.util.*;

/** Server fallback for the standard on-site training form; never changes frozen layouts. */
public final class TrainingStandardPdfRenderer {
    private TrainingStandardPdfRenderer() {}
    public record Document(TrainingDO record, String projectName) {}
    private record Cell(String text, float fraction) {}
    private record Row(List<Cell> cells, float minimumHeight) {}
    private record Box(String text, float x, float top, float width, float height) {}

    public static byte[] render(Document input) throws IOException {
        var record = input.record();
        boolean confirmed = Integer.valueOf(2).equals(record.getStatus());
        try (var pdf = new PDDocument();
             var fontInput = new ClassPathResource("training/fonts/NpdmsPrintSansSC-Regular.ttf").getInputStream();
             var output = new ByteArrayOutputStream()) {
            var font = PDType0Font.load(pdf, fontInput);
            pdf.getDocumentInformation().setTitle("现场培训记录表");
            pdf.getDocumentInformation().setAuthor("NPDMS");
            try (var table = new Table(pdf, font)) {
                table.heading("现场培训记录表", 20);
                table.heading("培训记录填写", 12);
                table.row(new Row(List.of(new Cell("工程名称\n" + value(input.projectName()), .44f),
                        new Cell("客户联系人\n" + value(record.getContactName()), .26f),
                        new Cell("客户联系电话\n" + value(record.getContactPhone()), .30f)), 58));
                table.row(new Row(List.of(new Cell("培训类型\n" + types(record.getTrainingTypes()), .44f),
                        new Cell("培训时间\n" + value(record.getTrainingTime()), .26f),
                        new Cell("培训工程师\n" + value(record.getTrainerName()), .30f)), 58));
                table.row(new Row(List.of(new Cell("培训内容\n" + value(record.getContent()), 1)), 112));
                table.heading("客户确认信息", 12);
                table.ratings(record);
                table.row(new Row(List.of(new Cell("综合意见", .14f),
                        new Cell(confirmed ? value(record.getSignOpinion()) : "", .86f)), 76));
                table.signature(record);
                if (!confirmed) table.heading("尚无客户评价与签字确认", 10);
            }
            pdf.save(output);
            return output.toByteArray();
        }
    }
    private static String value(Object value) { return value == null ? "" : value.toString(); }
    private static String types(String value) {
        return value(value).replace("TECHNICAL_PRINCIPLE", "技术原理").replace("PRODUCT_OPS", "产品操作")
                .replace("OTHER", "其他").replace(",", "、");
    }
    private static String options(String selected, List<String> options) {
        return options.stream().map(option -> (option.equals(selected) ? "● " : "○ ") + option)
                .collect(java.util.stream.Collectors.joining("  "));
    }
    private static final class Table implements AutoCloseable {
        private static final float MARGIN = 40, FONT_SIZE = 10, LINE = 16, PADDING = 8;
        private final PDDocument pdf;
        private final PDType0Font font;
        private final float width = PDRectangle.A4.getWidth() - MARGIN * 2;
        private PDPageContentStream stream;
        private float y;
        Table(PDDocument pdf, PDType0Font font) throws IOException { this.pdf = pdf; this.font = font; newPage(); }
        private void newPage() throws IOException {
            if (stream != null) stream.close();
            var page = new PDPage(PDRectangle.A4); pdf.addPage(page);
            stream = new PDPageContentStream(pdf, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
            text(new Box("第 " + pdf.getNumberOfPages() + " 页", MARGIN, 28, width, 16));
        }
        private String supported(String input) {
            var output = new StringBuilder();
            value(input).codePoints().forEach(cp -> {
                if (cp == '\t') { output.append("    "); return; }
                if (Character.isISOControl(cp)) return;
                String ch = new String(Character.toChars(cp));
                try { font.encode(ch); output.append(ch); }
                catch (IOException | IllegalArgumentException missing) { output.append('□'); }
            });
            return output.toString();
        }
        private List<String> lines(String input, float available) throws IOException {
            var result = new ArrayList<String>();
            for (String paragraph : value(input).split("\\R", -1)) {
                var line = new StringBuilder(); float used = 0;
                for (int cp : supported(paragraph).codePoints().toArray()) {
                    String ch = new String(Character.toChars(cp));
                    float advance = font.getStringWidth(ch) / 1000 * FONT_SIZE;
                    if (used + advance > available && !line.isEmpty()) { result.add(line.toString()); line.setLength(0); used = 0; }
                    line.append(ch); used += advance;
                }
                result.add(line.toString());
            }
            return result;
        }
        private void text(Box box) throws IOException {
            stream.beginText(); stream.setFont(font, FONT_SIZE);
            stream.newLineAtOffset(box.x(), box.top()); stream.showText(supported(box.text())); stream.endText();
        }
        private void border(Box box) throws IOException {
            stream.setLineWidth(.5f); stream.addRect(box.x(), box.top() - box.height(), box.width(), box.height()); stream.stroke();
        }
        private void cell(Box box) throws IOException {
            border(box); float baseline = box.top() - PADDING - FONT_SIZE;
            for (String line : lines(box.text(), box.width() - PADDING * 2)) {
                text(new Box(line, box.x() + PADDING, baseline, box.width(), LINE)); baseline -= LINE;
            }
        }
        void heading(String heading, float size) throws IOException {
            if (y < MARGIN + 40) newPage();
            y -= 12;
            stream.beginText(); stream.setFont(font, size);
            float x = size == 20 ? MARGIN + (width - font.getStringWidth(heading) / 1000 * size) / 2 : MARGIN;
            stream.newLineAtOffset(x, y - size); stream.showText(heading); stream.endText();
            y -= size + 12;
        }
        void row(Row row) throws IOException {
            var wrapped = new ArrayList<List<String>>(); int total = 0;
            for (Cell cell : row.cells()) {
                var lines = lines(cell.text(), width * cell.fraction() - PADDING * 2);
                wrapped.add(lines); total = Math.max(total, lines.size());
            }
            int offset = 0;
            while (offset < total) {
                float minimum = offset == 0 ? row.minimumHeight() : LINE + PADDING * 2;
                if (y - MARGIN < minimum) newPage();
                int capacity = Math.max(1, (int)((y - MARGIN - PADDING * 2) / LINE));
                int count = Math.min(total - offset, capacity);
                float height = Math.max(minimum, count * LINE + PADDING * 2), x = MARGIN;
                for (int i = 0; i < row.cells().size(); i++) {
                    var lines = wrapped.get(i); float cellWidth = width * row.cells().get(i).fraction();
                    String chunk = offset < lines.size() ? String.join("\n", lines.subList(offset, Math.min(lines.size(), offset + count))) : "";
                    cell(new Box(chunk, x, y, cellWidth, height)); x += cellWidth;
                }
                y -= height; offset += count;
                if (offset < total) newPage();
            }
        }
        void ratings(TrainingDO record) throws IOException {
            if (y - MARGIN < 132) newPage();
            boolean confirmed = Integer.valueOf(2).equals(record.getStatus());
            String[] labels = {"培训工程师技术水平及表达能力", "培训内容及讲解效果", "培训满意度"};
            String[] answers = {record.getSkillRating(), record.getEffectRating(), record.getSatisfactionRating()};
            cell(new Box("客户意见", MARGIN, y, width * .14f, 132));
            for (int i = 0; i < 3; i++) {
                cell(new Box(labels[i], MARGIN + width * .14f, y - i * 44, width * .43f, 44));
                cell(new Box(options(confirmed ? answers[i] : null, i == 2
                        ? List.of("非常满意", "较满意", "一般", "差") : List.of("很好", "良好", "一般", "差")),
                        MARGIN + width * .57f, y - i * 44, width * .43f, 44));
            }
            y -= 132;
        }
        void signature(TrainingDO record) throws IOException {
            if (y - MARGIN < 96) newPage();
            boolean confirmed = Integer.valueOf(2).equals(record.getStatus());
            cell(new Box("签字：" + (confirmed ? value(record.getSignConfirmerName()) : ""), MARGIN, y, width * .7f, 96));
            String date = confirmed && record.getSignTime() != null
                    ? record.getSignTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日")) : "";
            cell(new Box("日期\n" + date, MARGIN + width * .7f, y, width * .3f, 96));
            if (confirmed && record.getSignatureImageDataUrl() != null) {
                String safe = TrainingSignatureImage.normalize(record.getSignatureImageDataUrl());
                var image = PDImageXObject.createFromByteArray(pdf, Base64.getDecoder().decode(safe.substring(safe.indexOf(',') + 1)), "signature");
                float scale = Math.min((width * .7f - PADDING * 2) / image.getWidth(), 56f / image.getHeight());
                stream.drawImage(image, MARGIN + PADDING, y - 88, image.getWidth() * scale, image.getHeight() * scale);
            }
            y -= 96;
        }
        @Override public void close() throws IOException { if (stream != null) stream.close(); }
    }
}
