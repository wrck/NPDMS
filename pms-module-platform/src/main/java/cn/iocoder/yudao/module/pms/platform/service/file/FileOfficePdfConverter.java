package cn.iocoder.yudao.module.pms.platform.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** 本机无界面文档转换；每次预览使用独立临时目录和用户配置，不更改原文件。 */
@Component
public class FileOfficePdfConverter {
    private final String executable;
    public FileOfficePdfConverter(@Value("${pms.file.office-executable:${user.home}/.cache/npdms/office-preview/26.2.6/runtime/program/soffice.com}") String executable) {
        this.executable = executable;
    }
    public static boolean supports(String mediaType) {
        return Set.of("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document").contains(mediaType == null ? "" : mediaType);
    }
    public record Document(byte[] bytes, String mediaType) { }

    // LibreOffice CLI: https://help.libreoffice.org/latest/en-US/text/shared/guide/start_parameters.html
    public byte[] convert(Document document) {
        if (!supports(document.mediaType())) throw new IllegalArgumentException("不支持的Office预览格式");
        Path directory = null;
        Process process = null;
        try {
            directory = Files.createTempDirectory("pms-office-preview-");
            Path profile = Files.createDirectories(directory.resolve("profile/user"));
            Files.writeString(profile.resolve("registrymodifications.xcu"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <oor:items xmlns:oor="http://openoffice.org/2001/registry">
                      <item oor:path="/org.openoffice.Office.Common/Security/Scripting"><prop oor:name="MacroSecurityLevel" oor:op="fuse"><value>3</value></prop></item>
                      <item oor:path="/org.openoffice.Office.Writer/Content/Update"><prop oor:name="Link" oor:op="fuse"><value>2</value></prop><prop oor:name="Field" oor:op="fuse"><value>false</value></prop></item>
                    </oor:items>
                    """, StandardCharsets.UTF_8);
            Path input = directory.resolve("document" + ("application/msword".equals(document.mediaType()) ? ".doc" : ".docx"));
            Files.write(input, document.bytes());
            process = new ProcessBuilder(executable, "-env:UserInstallation=" + directory.resolve("profile").toUri(),
                    "--headless", "--nologo", "--nodefault", "--norestore", "--convert-to", "pdf:writer_pdf_Export",
                    "--outdir", directory.toString(), input.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) throw new IllegalStateException("方案预览转换超时，请重试");
            Path output = directory.resolve("document.pdf");
            if (process.exitValue() != 0 || !Files.isRegularFile(output) || Files.size(output) < 5 || Files.size(output) > 52_428_800L)
                throw new IllegalStateException("方案预览转换失败，请检查文档后重试");
            byte[] bytes = Files.readAllBytes(output);
            if (!new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) throw new IllegalStateException("无效的预览文件");
            return bytes;
        } catch (IOException ex) {
            throw new IllegalStateException("文档预览转换服务不可用", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("文档预览已中断", ex);
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            if (directory != null) {
                try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                } catch (IOException ex) {
                    org.slf4j.LoggerFactory.getLogger(FileOfficePdfConverter.class).warn("Preview temporary files could not be removed");
                }
            }
        }
    }
}
