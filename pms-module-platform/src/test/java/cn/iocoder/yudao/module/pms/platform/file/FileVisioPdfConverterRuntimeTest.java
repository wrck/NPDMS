package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.module.pms.platform.service.file.FileVisioPdfConverter;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "pms.visio.runtime-test", matches = "true")
class FileVisioPdfConverterRuntimeTest {
    @Test void originalVisioConvertsAllPagesWithoutChangingSource() throws Exception {
        Path source = Path.of(System.getProperty("pms.visio.fixture"));
        byte[] original = Files.readAllBytes(source);
        String media;
        try (var input = TikaInputStream.get(original)) { media = new Tika().detect(input); }
        assertEquals("application/vnd.ms-visio.drawing", media);
        var policy = new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact(
                true, 7L, "MUTABLE", "MULTIPLE", java.util.Set.of("DYNAMIC_FORM_ATTACHMENT"),
                java.util.Set.of(media), 52_428_800L, "INTERNAL");
        var validation = new cn.iocoder.yudao.module.pms.platform.service.file.FileContentPolicyService(
                new cn.iocoder.yudao.module.pms.platform.service.file.BoundedMultipartReader(), java.util.List.of(), false);
        var validated = validation.validateBounded(new cn.iocoder.yudao.module.pms.platform.service.file.command.BoundedFileContentValidationCommand(
                original, source.getFileName().toString(), original.length, media, null, policy));
        assertArrayEquals(original, validated.content());
        assertThrows(RuntimeException.class, () -> validation.validateBounded(
                new cn.iocoder.yudao.module.pms.platform.service.file.command.BoundedFileContentValidationCommand(
                        new byte[]{80, 75, 3, 4, 0, 0}, "renamed.vsdx", 6, media, null, policy)));
        Path executable = Path.of(System.getProperty("pms.visio.executable"));
        var converter = new FileVisioPdfConverter(executable.toString());
        byte[] pdf = converter.convert(new FileVisioPdfConverter.Document(original, media));
        String objects = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(objects.startsWith("%PDF-"));
        long expected;
        try (var zip = new java.util.zip.ZipFile(source.toFile())) {
            expected = zip.stream().filter(entry -> entry.getName().matches("visio/pages/page[0-9]+\\.xml")).count();
        }
        assertTrue(expected > 0);
        assertEquals(expected, Pattern.compile("/Type\\s*/Page\\b").matcher(objects).results().count());
        assertArrayEquals(original, Files.readAllBytes(source));
        Files.write(Path.of(System.getProperty("pms.visio.evidence")), pdf);
        assertThrows(IllegalArgumentException.class, () -> converter.convert(new FileVisioPdfConverter.Document(original, "application/zip")));
    }
}
