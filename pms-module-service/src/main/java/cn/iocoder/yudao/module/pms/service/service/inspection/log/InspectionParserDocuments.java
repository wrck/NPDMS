package cn.iocoder.yudao.module.pms.service.service.inspection.log;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceLogParsingApi;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionResultEvaluator;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Typed bridge to DAC's existing command-output input and genericContent result contracts. */
public final class InspectionParserDocuments {
    // Leave room for the HTTP envelope below DAC's unchanged 8MiB parser input limit.
    private static final int PART_BYTES = 7 * 1024 * 1024;
    public record Block(int commandIndex, String commandText, String status, String stdout, String stderr,
                        Boolean truncated, Integer exitCode) { }
    public record Document(String schemaVersion, List<Block> commandBlocks) { }
    private record CollectionDocument(Boolean truncated, List<Block> commandBlocks) { }
    public record Part(int firstIndex, int lastIndex, boolean oversized) { }
    public record Observation(Block source, JsonNode genericUnits) { }
    private InspectionParserDocuments() { }

    public static Block block(DeviceLogParsingApi.LogBlock source, int index) {
        return new Block(index, source.commandText(), source.status(), source.stdout(), source.stderr(), source.truncated(), source.exitCode());
    }
    public static byte[] encode(List<Block> blocks) {
        return JsonUtils.toJsonString(new Document("1.0.0", List.copyOf(blocks))).getBytes(StandardCharsets.UTF_8);
    }
    /** The bound platform callback envelope is not a DAC parser document. Keep its original file intact. */
    public static String collectionInput(byte[] content) {
        var source = JsonUtils.parseObjectQuietly(new String(content, StandardCharsets.UTF_8), CollectionDocument.class);
        if (source == null || source.truncated() == null || source.commandBlocks() == null
                || source.commandBlocks().isEmpty() || source.commandBlocks().size() > 4096) throw invalidCollection();
        Set<Integer> indexes = new HashSet<>();
        List<Block> blocks = new ArrayList<>();
        for (var block : source.commandBlocks()) {
            if (block == null || block.commandIndex() < 1 || !indexes.add(block.commandIndex())
                    || block.commandText() == null || block.commandText().isBlank()
                    || block.status() == null || block.status().isBlank() || block.stdout() == null
                    || block.stderr() == null || block.truncated() == null) throw invalidCollection();
            blocks.add(new Block(block.commandIndex(), block.commandText(), block.status(), block.stdout(), block.stderr(),
                    source.truncated() || block.truncated(), block.exitCode()));
        }
        blocks.sort(Comparator.comparingInt(Block::commandIndex));
        // Project only parser fields. Callback metadata and its timestamp encodings remain in the original evidence.
        return new String(encode(blocks), StandardCharsets.UTF_8);
    }
    private static InspectionException invalidCollection() {
        return new InspectionException(InspectionException.Reason.FILE_INVALID, "采集日志缺少有效命令块，请核对采集输出");
    }
    public static Document decode(byte[] content) {
        Document result = JsonUtils.parseObject(new String(content, StandardCharsets.UTF_8), Document.class);
        InspectionException.require(result != null && "1.0.0".equals(result.schemaVersion()) && result.commandBlocks() != null,
                "冻结日志内容格式不匹配");
        return result;
    }
    public static List<Part> parts(List<Block> blocks) {
        List<Part> result = new ArrayList<>();
        int first = -1; int last = -1; int bytes = 100;
        for (var block : blocks) {
            int size = encode(List.of(block)).length;
            if (first >= 0 && bytes + size > PART_BYTES) {
                result.add(new Part(first, last, false)); first = -1; bytes = 100;
            }
            if (size > PART_BYTES) { result.add(new Part(block.commandIndex(), block.commandIndex(), true)); continue; }
            if (first < 0) first = block.commandIndex();
            last = block.commandIndex(); bytes += size;
        }
        if (first >= 0) result.add(new Part(first, last, false));
        return List.copyOf(result);
    }

    public static InspectionResultEvaluator.CommandOutput output(Observation observation) {
        var block = observation.source();
        if (Boolean.TRUE.equals(block.truncated())) return new InspectionResultEvaluator.CommandOutput(block.commandText(),
                block.status(), "LIMITED", block.stdout(), List.of(), true);
        List<JsonNode> units = new ArrayList<>();
        if (observation.genericUnits() != null && observation.genericUnits().isArray()) {
            for (var unit : observation.genericUnits()) {
                if (unit.path("commandIndex").asInt() == block.commandIndex()
                        && unit.path("nestingDepth").asInt() == 0
                        && normalizeCommand(unit.path("commandText").asText()).equals(normalizeCommand(block.commandText()))) units.add(unit);
            }
        }
        if (units.size() != 1) return new InspectionResultEvaluator.CommandOutput(block.commandText(), block.status(),
                "LIMITED", block.stdout(), List.of(), true);
        var unit = units.getFirst(); List<String> values = new ArrayList<>();
        collectSections(unit.path("sections"), values);
        return new InspectionResultEvaluator.CommandOutput(block.commandText(), block.status(), unit.path("structureStatus").asText(),
                block.stdout(), List.copyOf(values), true);
    }
    public static String normalizeCommand(String command) {
        return command == null ? "" : command.trim().replaceAll("\\s+", " ");
    }

    private static void collectSections(JsonNode sections, List<String> values) {
        if (!sections.isArray()) return;
        for (var section : sections) {
            switch (section.path("type").asText()) {
                case "keyValue" -> collectEntries(section.path("entries"), values);
                case "table" -> {
                    for (var row : section.path("rows")) for (var value : row.path("values"))
                        if (value.isTextual()) values.add(value.asText());
                }
                case "recordList" -> {
                    for (var record : section.path("records")) {
                        collectEntries(record.path("entries"), values); collectSections(record.path("sections"), values);
                    }
                }
                default -> { /* Text remains text; no inferred field mapping or numeric extraction. */ }
            }
        }
    }
    private static void collectEntries(JsonNode entries, List<String> values) {
        if (!entries.isArray()) return;
        for (var entry : entries) {
            if (entry.path("value").isTextual()) values.add(entry.path("value").asText());
            collectEntries(entry.path("children"), values);
        }
    }
}
