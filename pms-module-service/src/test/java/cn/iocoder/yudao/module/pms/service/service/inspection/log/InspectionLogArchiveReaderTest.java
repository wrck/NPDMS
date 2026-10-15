package cn.iocoder.yudao.module.pms.service.service.inspection.log;

import cn.iocoder.yudao.module.pms.service.service.inspection.log.InspectionLogArchiveReader.Input;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class InspectionLogArchiveReaderTest {
    private final InspectionLogArchiveReader reader = new InspectionLogArchiveReader();

    @Test
    void readsUtf8BomAndGb18030WithoutChangingLogContents() {
        String original = "设备序列号：中文设备\r\n接口状态正常\n\u001B[32m保留终端控制\u001B[0m\t值=10\n";
        var utf8 = reader.read(new Input("device.LOG", ("\uFEFF" + original).getBytes(StandardCharsets.UTF_8)));
        var gb18030 = reader.read(new Input("device.txt", original.getBytes(Charset.forName("GB18030"))));
        assertEquals(original, utf8.get(0).text());
        assertEquals(original, gb18030.get(0).text());
        assertEquals("device.LOG", utf8.get(0).fileName());
    }

    @Test
    void readsMultipleLogsFromSafeRelativeZipPaths() throws Exception {
        String chinese = "设备甲：链路检测正常\r\n";
        byte[] archive = zip(List.of(new Entry("现场/", new byte[0]),
                new Entry("现场/device-a.log", chinese.getBytes(StandardCharsets.UTF_8)),
                new Entry("现场/device-b.TXT", "设备乙：检测失败".getBytes(Charset.forName("GB18030")))));
        var entries = reader.read(new Input("现场日志.ZIP", archive));
        assertEquals(2, entries.size());
        assertEquals("现场/device-a.log", entries.get(0).fileName());
        assertEquals(chinese, entries.get(0).text());
        assertEquals("现场/device-b.TXT", entries.get(1).fileName());
        assertEquals("设备乙：检测失败", entries.get(1).text());
    }

    @Test
    void rejectsUnsupportedExtensionsAndDisguisedBinaryFormats() throws Exception {
        rejected("只支持", new Input("device.csv", "设备日志".getBytes(StandardCharsets.UTF_8)));
        rejected("只支持", new Input("device.log.exe", "设备日志".getBytes(StandardCharsets.UTF_8)));
        rejected("ZIP 格式不符", new Input("device.zip", "设备日志".getBytes(StandardCharsets.UTF_8)));
        rejected("二进制", new Input("device.log", "%PDF-1.7\nnot a device log".getBytes(StandardCharsets.UTF_8)));
        rejected("NUL", new Input("device.txt", new byte[] { 'A', 0, 'B' }));
        rejected("非文本控制字符", new Input("device.log", new byte[] { 'A', 1, 'B' }));
        rejected("压缩包", new Input("device.log", zip(List.of(new Entry("inner.txt", new byte[] { 'A' })))));
        rejected("只支持", new Input("logs.zip", zip(List.of(new Entry("readme.csv", new byte[] { 'A' })))));
    }

    @Test
    void rejectsEmptyLogsInvalidEncodingAndArchivesWithoutLogs() throws Exception {
        rejected("不能为空", new Input("device.log", new byte[0]));
        rejected("不能为空", new Input("device.log", "\uFEFF \r\n\t".getBytes(StandardCharsets.UTF_8)));
        rejected("不能为空", new Input("logs.zip", zip(List.of(new Entry("device.log", new byte[0])))));
        rejected("没有可读取", new Input("logs.zip", zip(List.of(new Entry("folder/", new byte[0])))));
        rejected("编码无效", new Input("device.log", new byte[] { (byte) 0xFF }));
        rejected("目录条目", new Input("logs.zip", zip(List.of(new Entry("folder/", new byte[] { 'A' })))));
    }

    @Test
    void rejectsNestedArchivesEvenWhenRenamedAsLogs() throws Exception {
        byte[] nested = zip(List.of(new Entry("device.log", "设备日志".getBytes(StandardCharsets.UTF_8))));
        rejected("嵌套", new Input("logs.zip", zip(List.of(new Entry("inner.zip", nested)))));
        rejected("嵌套", new Input("logs.zip", zip(List.of(new Entry("inner.txt", nested)))));
    }

    @Test
    void rejectsUnsafeUploadedAndZipEntryPaths() throws Exception {
        byte[] content = "设备日志".getBytes(StandardCharsets.UTF_8);
        for (String name : List.of("../device.log", "folder/../device.log", "..\\device.log", "/device.log",
                "C:\\device.log", "\\\\server\\device.log", "folder//device.log", "folder/./device.log", "device.log:stream")) {
            rejected("路径", new Input(name, content));
            rejected("路径", new Input("logs.zip", zip(List.of(new Entry(name, content)))));
        }
    }

    @Test
    void rejectsOversizedInputBeforeReadingItsContents() {
        rejected("上传文件超过", new Input("device.log", new byte[50 * 1024 * 1024 + 1]));
    }

    @Test
    void enforcesCumulativeExpandedBytesWhenEachZipEntryIsWithinTheLimit() throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(compressed)) {
            for (int i = 0; i < 51; i++) writeRepeated(new RepeatedEntry(zip, "device-" + i + ".log", 1024 * 1024));
        }
        assertTrue(compressed.size() < 1024 * 1024, "输入虽小，展开仍必须受限");
        rejected("ZIP 展开累计超过", new Input("logs.zip", compressed.toByteArray()));
    }

    @Test
    void rejectsExcessiveZipEntries() throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(compressed)) {
            for (int i = 0; i < 1025; i++) {
                zip.putNextEntry(new ZipEntry("device-" + i + ".log"));
                zip.write('A');
                zip.closeEntry();
            }
        }
        rejected("1024", new Input("logs.zip", compressed.toByteArray()));
    }

    @Test
    void rejectsCorruptZipStreamsWithAChineseFormatError() {
        rejected("损坏", new Input("logs.zip", new byte[] { 'P', 'K', 3, 4, 1 }));
    }

    private record Entry(String name, byte[] content) { }

    private static byte[] zip(List<Entry> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private record RepeatedEntry(ZipOutputStream zip, String name, int bytes) { }

    private static void writeRepeated(RepeatedEntry entry) throws IOException {
        entry.zip().putNextEntry(new ZipEntry(entry.name()));
        byte[] chunk = new byte[8192];
        Arrays.fill(chunk, (byte) 'A');
        int remaining = entry.bytes();
        while (remaining > 0) {
            int size = Math.min(chunk.length, remaining);
            entry.zip().write(chunk, 0, size);
            remaining -= size;
        }
        entry.zip().closeEntry();
    }

    private void rejected(String error, Input input) {
        var exception = assertThrows(IllegalArgumentException.class, () -> reader.read(input));
        assertTrue(exception.getMessage().contains(error), exception.getMessage());
    }
}
