package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.module.pms.platform.service.file.FileOfficePdfConverter;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "pms.office.runtime-test", matches = "true")
class FileOfficePdfConverterRuntimeTest {
    @Test void docxAndLegacyDocRetainTwoPagesAndEmbeddedPicture() throws Exception {
        Path executable = Path.of(System.getProperty("user.home"), ".cache/npdms/office-preview/26.2.6/runtime/program/soffice.com");
        assertTrue(Files.isRegularFile(executable), "Office preview runtime must be provisioned for this acceptance test");
        Path evidence = Path.of(System.getProperty("pms.office.preview.evidence"));
        Files.createDirectories(evidence);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var word = new XWPFDocument()) {
            word.createParagraph().createRun().setText("割接方案预览验证：第一页 / PAGE ONE");
            var table = word.createTable(2, 2);
            table.getRow(0).getCell(0).setText("设备"); table.getRow(0).getCell(1).setText("操作");
            table.getRow(1).getCell(0).setText("测试设备"); table.getRow(1).getCell(1).setText("只读核验");
            BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics(); graphics.setColor(java.awt.Color.BLUE); graphics.fillRect(0, 0, 64, 32); graphics.dispose();
            var picture = new ByteArrayOutputStream(); ImageIO.write(image, "png", picture);
            word.createParagraph().createRun().addPicture(new ByteArrayInputStream(picture.toByteArray()), Document.PICTURE_TYPE_PNG,
                    "topology.png", Units.toEMU(160), Units.toEMU(80));
            var second = word.createParagraph(); second.setPageBreak(true); second.createRun().setText("回退方案：第二页 / PAGE TWO");
            word.write(bytes);
        }
        Path docx = evidence.resolve("cutover-preview-two-pages.docx"); Files.write(docx, bytes.toByteArray());
        var converter = new FileOfficePdfConverter(executable.toString());
        byte[] pdf = converter.convert(new FileOfficePdfConverter.Document(bytes.toByteArray(), "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertPagesAndImage(pdf); Files.write(evidence.resolve("cutover-preview-docx.pdf"), pdf);
        assertArrayEquals(bytes.toByteArray(), Files.readAllBytes(docx));
        var process = new ProcessBuilder(executable.toString(), "-env:UserInstallation=" + evidence.resolve("fixture-profile").toUri(),
                "--headless", "--convert-to", "doc:MS Word 97", "--outdir", evidence.toString(), docx.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(process.waitFor(60, TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
        Path doc = evidence.resolve("cutover-preview-two-pages.doc"); assertTrue(Files.isRegularFile(doc));
        byte[] legacy = Files.readAllBytes(doc);
        byte[] legacyPdf = converter.convert(new FileOfficePdfConverter.Document(legacy, "application/msword"));
        assertPagesAndImage(legacyPdf); Files.write(evidence.resolve("cutover-preview-doc.pdf"), legacyPdf);
        assertArrayEquals(legacy, Files.readAllBytes(doc));
        assertThrows(IllegalStateException.class, () -> new FileOfficePdfConverter(executable + ".missing").convert(
                new FileOfficePdfConverter.Document(bytes.toByteArray(), "application/vnd.openxmlformats-officedocument.wordprocessingml.document")));
    }
    private static void assertPagesAndImage(byte[] pdf) {
        String objects = new String(pdf, StandardCharsets.ISO_8859_1);
        assertEquals(2, Pattern.compile("/Type\\s*/Page\\b").matcher(objects).results().count());
        assertTrue(Pattern.compile("/Subtype\\s*/Image\\b").matcher(objects).find(), "embedded topology must remain visible");
    }
}
