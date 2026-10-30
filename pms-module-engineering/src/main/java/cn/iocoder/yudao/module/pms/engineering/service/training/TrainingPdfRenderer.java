package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import java.io.*;
import java.util.*;

/** Portable PDF with embedded Chinese font and paginated text; never reads template URLs. */
public final class TrainingPdfRenderer {
    private TrainingPdfRenderer() {}
    public static byte[] render(TrainingDO record) throws IOException {
        TrainingPrintLayout layout = record.getPrintLayoutSnapshot() == null
                ? TrainingPrintLayout.defaults() : TrainingPrintLayout.parse(record.getPrintLayoutSnapshot());
        try (PDDocument document = new PDDocument();
             InputStream fontInput = new ClassPathResource("training/fonts/NpdmsPrintSansSC-Regular.ttf").getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType0Font font = PDType0Font.load(document, fontInput);
            document.getDocumentInformation().setTitle(layout.title());
            document.getDocumentInformation().setAuthor("NPDMS");
            try (PageWriter writer = new PageWriter(document, font, layout)) {
                writer.text(layout.title(), 20);
                writer.text("记录编号：" + value(record.getCode()), 10);
                writer.text("状态：" + switch (record.getStatus() == null ? -1 : record.getStatus()) {
                    case 0 -> "草稿"; case 1 -> "已外发"; case 2 -> "客户已确认"; case 3 -> "已作废"; default -> "未知";
                }, 10);
                for (String section : layout.sections()) {
                    switch (section) {
                        case "BASIC" -> {
                            writer.heading("基本信息");
                            writer.field("培训名称", record.getName());
                            writer.field("培训类型", types(record.getTrainingTypes()));
                            writer.field("培训时间", record.getTrainingTime());
                            writer.field("培训工程师", record.getTrainerName());
                            writer.field("参训人数", record.getTraineeCount());
                            writer.field("客户联系人", record.getContactName());
                            writer.field("联系电话", record.getContactPhone());
                        }
                        case "CONTENT" -> { writer.heading("培训内容"); writer.text(value(record.getContent()), layout.fontSize()); }
                        case "REMARK" -> { writer.heading("备注"); writer.text(value(record.getRemark()), layout.fontSize()); }
                        case "CONFIRMATION" -> {
                            writer.heading("客户评价与确认");
                            if (!Objects.equals(record.getStatus(), 2)) { writer.text("尚无客户评价与签字确认", layout.fontSize()); break; }
                            writer.field("培训技能", record.getSkillRating());
                            writer.field("培训效果", record.getEffectRating());
                            writer.field("满意程度", record.getSatisfactionRating());
                            writer.field("改进意见", record.getSignOpinion());
                            writer.field("客户确认人", record.getSignConfirmerName());
                            writer.field("确认时间", record.getSignTime());
                            if (record.getConfirmationValues() != null && record.getConfirmationFormRules() != null) {
                                var values = JsonUtils.parseTree(record.getConfirmationValues());
                                Set<String> core = Set.of("skillRating", "effectRating", "satisfactionRating", "signOpinion", "signConfirmerName", "signatureImageDataUrl");
                                for (var rule : JsonUtils.parseTree(record.getConfirmationFormRules())) {
                                    String field = rule.path("field").asText();
                                    if (core.contains(field) || !values.has(field)) continue;
                                    var answer = values.get(field);
                                    List<String> labels = new ArrayList<>();
                                    for (var item : answer.isArray() ? answer : List.of(answer)) {
                                        String label = item.asText();
                                        for (var option : rule.path("options")) {
                                            if (option.path("value").equals(item)) { label = option.path("label").asText(); break; }
                                        }
                                        labels.add(label);
                                    }
                                    writer.field(rule.path("title").asText(field), String.join("、", labels));
                                }
                            }
                            if (record.getSignatureImageDataUrl() != null) {
                                writer.heading("客户手写签字");
                                String safeImage = TrainingSignatureImage.normalize(record.getSignatureImageDataUrl());
                                writer.image(Base64.getDecoder().decode(safeImage.substring(safeImage.indexOf(',') + 1)));
                            }
                        }
                        default -> throw new IllegalStateException("Invalid print section");
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
    private static String value(Object value) { return value == null || value.toString().isBlank() ? "—" : value.toString(); }
    private static String types(String types) {
        if (types == null) return "—";
        return types.replace("TECHNICAL_PRINCIPLE", "技术原理").replace("PRODUCT_OPS", "产品操作").replace("OTHER", "其他").replace(",", "、");
    }
    private static final class PageWriter implements AutoCloseable {
        private final PDDocument document;
        private final PDType0Font font;
        private final TrainingPrintLayout layout;
        private final PDRectangle size;
        private PDPageContentStream stream;
        private float y;
        PageWriter(PDDocument document, PDType0Font font, TrainingPrintLayout layout) throws IOException {
            this.document = document; this.font = font; this.layout = layout;
            PDRectangle paper = "LETTER".equals(layout.paper()) ? PDRectangle.LETTER : PDRectangle.A4;
            size = layout.landscape() ? new PDRectangle(paper.getHeight(), paper.getWidth()) : paper;
            newPage();
        }
        private void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(size); document.addPage(page);
            stream = new PDPageContentStream(document, page);
            draw(layout.header(), 9, size.getHeight() - layout.margin());
            draw(layout.footer() + "    第 " + document.getNumberOfPages() + " 页", 9, layout.margin());
            y = size.getHeight() - layout.margin() - 30;
        }
        private String supported(String text) {
            StringBuilder result = new StringBuilder();
            text.codePoints().forEach(cp -> {
                if (cp == '\t') { result.append("    "); return; }
                if (Character.isISOControl(cp)) return;
                try { font.encode(new String(Character.toChars(cp))); result.appendCodePoint(cp); }
                catch (IOException | IllegalArgumentException unsupported) { result.append('□'); }
            });
            return result.toString();
        }
        private void draw(String text, float fontSize, float atY) throws IOException {
            String clean = supported(text);
            float width = font.getStringWidth(clean) / 1000 * fontSize;
            float fitSize = width > size.getWidth() - 2 * layout.margin()
                    ? fontSize * (size.getWidth() - 2 * layout.margin()) / width : fontSize;
            stream.beginText(); stream.setFont(font, fitSize);
            stream.newLineAtOffset(layout.margin(), atY); stream.showText(clean); stream.endText();
        }
        void heading(String title) throws IOException { y -= 10; if (y < layout.margin() + 90) newPage(); text(title, 14); }
        void field(String label, Object value) throws IOException { text(label + "：" + value(value), layout.fontSize()); }
        void text(String text, float fontSize) throws IOException {
            float maxWidth = size.getWidth() - 2 * layout.margin();
            for (String paragraph : text.split("\\R", -1)) {
                StringBuilder line = new StringBuilder(); float width = 0;
                for (int cp : supported(paragraph).codePoints().toArray()) {
                    String ch = new String(Character.toChars(cp)); float advance = font.getStringWidth(ch) / 1000 * fontSize;
                    if (width + advance > maxWidth && !line.isEmpty()) { line(line.toString(), fontSize); line.setLength(0); width = 0; }
                    line.append(ch); width += advance;
                }
                line(line.toString(), fontSize);
            }
            y -= 4;
        }
        private void line(String text, float fontSize) throws IOException {
            if (y < layout.margin() + 35) newPage(); draw(text, fontSize, y); y -= fontSize * 1.6f;
        }
        void image(byte[] png) throws IOException {
            PDImageXObject image = PDImageXObject.createFromByteArray(document, png, "signature");
            float width = Math.min(320, size.getWidth() - 2 * layout.margin());
            float height = width * image.getHeight() / image.getWidth();
            if (height > 160) { width *= 160 / height; height = 160; }
            if (y - height < layout.margin() + 35) newPage();
            stream.drawImage(image, layout.margin(), y - height, width, height); y -= height + 16;
        }
        @Override public void close() throws IOException { if (stream != null) stream.close(); }
    }
}
