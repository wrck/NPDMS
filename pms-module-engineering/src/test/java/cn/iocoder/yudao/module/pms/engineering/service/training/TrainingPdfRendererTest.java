package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class TrainingPdfRendererTest {
    @Test void exportsChinesePaginationSignatureAndFrozenLayout() throws Exception {
        TrainingDO row = record();
        row.setStatus(2);
        row.setContent("培训内容：设备操作、故障排查、维护注意事项。\n".repeat(65));
        row.setSignConfirmerName("张三");
        row.setSkillRating("很好"); row.setEffectRating("良好"); row.setSatisfactionRating("非常满意");
        row.setPrintLayoutSnapshot(JsonUtils.toJsonString(new TrainingPrintLayout("客户培训交付记录", "项目交付平台", "客户确认存档", "A4", false, 11, 40, List.of("BASIC", "CONTENT", "CONFIRMATION", "REMARK"))));
        BufferedImage signature = new BufferedImage(400, 150, BufferedImage.TYPE_INT_RGB);
        var graphics = signature.createGraphics(); graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0,0,400,150);
        graphics.setColor(java.awt.Color.BLACK); graphics.drawLine(30,40,280,100); graphics.drawLine(50,100,290,35); graphics.dispose();
        var png = new ByteArrayOutputStream(); ImageIO.write(signature, "png", png);
        row.setSignatureImageDataUrl("data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray()));
        byte[] bytes = TrainingPdfRenderer.render(row);
        assertTrue(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"));
        try (var pdf = Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("客户培训交付记录")); assertTrue(text.contains("张三"));
            assertTrue(text.contains("非常满意")); assertTrue(text.contains("客户确认存档"));
            boolean foundImage = false;
            for (var page : pdf.getPages()) for (var name : page.getResources().getXObjectNames()) foundImage |= page.getResources().isImageXObject(name);
            assertTrue(foundImage);
            Path output = Path.of("target", "training-pdf-evidence"); Files.createDirectories(output);
            Files.write(output.resolve("training.pdf"), bytes);
            var renderer = new PDFRenderer(pdf);
            for (int i=0; i<pdf.getNumberOfPages(); i++) ImageIO.write(renderer.renderImageWithDPI(i, 100), "png", output.resolve("page-" + (i+1) + ".png").toFile());
        }
    }
    @Test void legacyDraftUsesDefaultWithoutChangingStoredHtml() throws Exception {
        TrainingDO row = record(); row.setStatus(0); row.setFileUrl("/immutable/original.html");
        try (var pdf = Loader.loadPDF(TrainingPdfRenderer.render(row))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("现场培训记录表")); assertTrue(text.contains("尚无客户评价与签字确认"));
        }
        assertEquals("/immutable/original.html", row.getFileUrl()); assertNull(row.getPrintLayoutSnapshot());
    }
    @Test void rejectsInvalidLayoutsAndRemoteSignatureImages() {
        assertThrows(RuntimeException.class, () -> TrainingPrintLayout.parse("{}"));
        var invalid = new TrainingPrintLayout("标题", "", "", "A4", false, 11, 40, List.of("BASIC"));
        assertThrows(RuntimeException.class, () -> TrainingPrintLayout.parse(JsonUtils.toJsonString(invalid)));
        TrainingDO row = record(); row.setStatus(2); row.setSignatureImageDataUrl("https://example.com/signature.png");
        assertThrows(RuntimeException.class, () -> TrainingPdfRenderer.render(row));
    }
    private TrainingDO record() {
        TrainingDO row = new TrainingDO(); row.setCode("TR-PDF-001"); row.setName("设备运维培训");
        row.setTrainerName("王工"); row.setTrainingTypes("TECHNICAL_PRINCIPLE,PRODUCT_OPS"); row.setTraineeCount(12);
        row.setContactName("张三"); row.setContactPhone("13800000000"); return row;
    }
}
