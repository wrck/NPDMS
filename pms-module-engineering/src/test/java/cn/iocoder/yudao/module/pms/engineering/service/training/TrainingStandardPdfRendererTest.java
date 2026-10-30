package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class TrainingStandardPdfRendererTest {
    private TrainingDO record() throws Exception {
        var row = new TrainingDO(); row.setName("设备运维培训"); row.setCode("TR-STANDARD-001");
        row.setContactName("张三"); row.setContactPhone("13800000000"); row.setTrainerName("王工");
        row.setTrainingTypes("TECHNICAL_PRINCIPLE,PRODUCT_OPS"); row.setTrainingTime(java.time.LocalDate.of(2026,10,22));
        row.setContent("设备操作、故障排查、维护注意事项。"); row.setStatus(2);
        row.setSkillRating("很好"); row.setEffectRating("良好"); row.setSatisfactionRating("非常满意");
        row.setSignOpinion("培训内容完整，讲解清楚。"); row.setSignConfirmerName("张三");
        row.setSignTime(java.time.LocalDateTime.of(2026,10,22,12,30));
        var image = new BufferedImage(400,150,BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics(); g.setColor(java.awt.Color.WHITE); g.fillRect(0,0,400,150);
        g.setColor(java.awt.Color.BLACK); g.drawLine(20,100,350,30); g.dispose();
        var png = new ByteArrayOutputStream(); ImageIO.write(image,"png",png);
        row.setSignatureImageDataUrl("data:image/png;base64,"+Base64.getEncoder().encodeToString(png.toByteArray()));
        row.setPrintLayoutSnapshot("{\"engine\":\"FORM_CREATE_ELEMENT_PLUS\"}");
        row.setFileUrl("/immutable/original.html"); return row;
    }
    @Test void standardFormContainsBusinessDataAndSignatureWithoutChangingHistory() throws Exception {
        var row = record(); String snapshot = row.getPrintLayoutSnapshot();
        byte[] bytes = TrainingStandardPdfRenderer.render(new TrainingStandardPdfRenderer.Document(row,"测试工程"));
        try (var pdf = Loader.loadPDF(bytes)) {
            assertEquals(1,pdf.getNumberOfPages());
            String text = new PDFTextStripper().getText(pdf);
            for(String expected : new String[]{"现场培训记录表","测试工程","客户联系电话","培训内容","客户意见","培训工程师技术水平及表达能力","综合意见","张三","2026年10月22日"})
                assertTrue(text.contains(expected),expected);
            assertTrue(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());
            Path output = Path.of("target","training-standard-pdf-evidence"); Files.createDirectories(output);
            Files.write(output.resolve("standard.pdf"),bytes);
            ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0,110),"png",output.resolve("standard.png").toFile());
        }
        assertEquals(snapshot,row.getPrintLayoutSnapshot()); assertEquals("/immutable/original.html",row.getFileUrl());
    }
    @Test void paginatesLongContentWithoutLosingTailOrConfirmation() throws Exception {
        var row = record(); row.setContent("设备操作、故障排查、维护注意事项。\n".repeat(150)+"长内容末尾校验");
        try(var pdf = Loader.loadPDF(TrainingStandardPdfRenderer.render(new TrainingStandardPdfRenderer.Document(row,"长内容工程")))) {
            assertTrue(pdf.getNumberOfPages()>1);
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("长内容末尾校验")); assertTrue(text.contains("客户意见")); assertTrue(text.contains("张三"));
        }
    }
    @Test void draftDoesNotRenderUnconfirmedRatingsOrSignatureAndRemoteImagesAreRejected() throws Exception {
        var row = record(); row.setStatus(0); row.setSignOpinion("不可输出的未确认意见");
        row.setSignatureImageDataUrl("https://example.com/signature.png");
        try(var pdf = Loader.loadPDF(TrainingStandardPdfRenderer.render(new TrainingStandardPdfRenderer.Document(row,"测试工程")))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("尚无客户评价与签字确认")); assertFalse(text.contains("不可输出的未确认意见"));
            assertFalse(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());
        }
        row.setStatus(2);
        assertThrows(RuntimeException.class,()->TrainingStandardPdfRenderer.render(new TrainingStandardPdfRenderer.Document(row,"测试工程")));
    }
}
