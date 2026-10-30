package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceLogParsingApi;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Serializes already-redacted command blocks for UMC; never asserts actual device execution. */
public final class UmcLogDocument {
    private UmcLogDocument() { }
    public record Input(String deviceLabel, List<DeviceLogParsingApi.LogBlock> blocks) { }
    public record NormalizedInput(String deviceLabel, byte[] content) { }
    public static byte[] fromNormalized(NormalizedInput input) {
        var document = cn.iocoder.yudao.module.pms.service.service.inspection.log.InspectionParserDocuments.decode(input.content());
        return render(new Input(input.deviceLabel(), document.commandBlocks().stream().map(b -> new DeviceLogParsingApi.LogBlock(
                b.commandIndex(), b.commandText(), b.stdout(), b.stderr(), b.status(), b.truncated(), b.exitCode())).toList()));
    }
    public static byte[] render(Input input) {
        InspectionException.require(input != null && input.blocks() != null && !input.blocks().isEmpty(), "日志没有可用命令输出");
        String label = input.deviceLabel() == null ? "NPDMS" : input.deviceLabel().replaceAll("[^\\p{L}\\p{N}_.-]", "_");
        if (label.isBlank()) label = "NPDMS";
        label = label.substring(0, Math.min(label.length(), 64));
        StringBuilder out = new StringBuilder();
        for (var block : input.blocks()) {
            InspectionException.require(block != null && block.stdout() != null && !Boolean.TRUE.equals(block.truncated()), "日志命令输出缺失或已截断");
            String text = block.stdout().replace("\r\n", "\n").replace('\r', '\n');
            String command = block.commandText();
            if (command != null && !command.isBlank()) {
                InspectionException.require(!command.contains("\n") && !command.contains("\r"), "日志命令包含无效换行");
                out.append('<').append(label).append('>').append(command).append('\n');
                if (text.startsWith(command + "\n")) text = text.substring(command.length() + 1);
            }
            out.append(text).append('\n');
        }
        byte[] bytes = out.toString().replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
        InspectionException.require(bytes.length > 0 && bytes.length <= 52_428_800, "脱敏日志超过文件大小限制");
        return bytes;
    }
    /** The UMC filename itself supplies the address; command output is never searched for one. */
    public static String sourceHostFromFileName(String name) {
        if (name == null || name.length() > 255 || name.matches("(?s).*[\\\\/:*?\"<>|\\p{Cntrl}].*")) return null;
        var match = java.util.regex.Pattern.compile("^([^()（）]*?)[(（]([^()（）]+)[)）]$").matcher(name);
        String host = match.matches() ? match.group(2) : name;
        try { requireIpv4(host); return host; }
        catch (InspectionException invalid) { return null; }
    }
    public static void requireIpv4(String value) {
        InspectionException.require(value != null && value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"), "请填写日志来源设备的IPv4地址");
        for (var part : value.split("\\.")) InspectionException.require(Integer.parseInt(part) <= 255
                && (part.length() == 1 || part.charAt(0) != '0'), "设备IP地址无效");
    }
}
