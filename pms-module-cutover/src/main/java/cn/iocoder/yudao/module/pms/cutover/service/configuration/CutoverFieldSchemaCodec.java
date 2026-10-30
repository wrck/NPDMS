package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistFieldDefinitionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistFieldOptionDO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 将 HTTP 的界面定义映射到字段树；只处理配置元数据，不存储固定业务答案。 */
public final class CutoverFieldSchemaCodec {
    private static final Set<String> KEYS = Set.of("code", "key", "name", "label", "type", "required",
            "maxLength", "source", "visibleWhen", "fields", "columns", "options");

    private CutoverFieldSchemaCodec() { }

    @Builder
    public record Write(Long tenantId, Long itemDefinitionRevisionId, Long customChecklistItemId,
                        String businessCategoryCode, String itemTypeCode, Map<String, Object> schema) { }

    public record Definition(List<CutoverChecklistFieldDefinitionDO> fields,
                             List<CutoverChecklistFieldOptionDO> options) { }

    @Builder
    private record Node(Write write, Map<String, Object> source, Long parentId, String path,
                        String semanticPath, String kind, int order) { }

    public static Definition encode(Write write) {
        if (write.tenantId() == null || (write.itemDefinitionRevisionId() == null) == (write.customChecklistItemId() == null)) {
            throw new IllegalArgumentException("字段定义必须属于一个精确采集项修订或任务自定义项");
        }
        List<CutoverChecklistFieldDefinitionDO> fields = new ArrayList<>();
        List<CutoverChecklistFieldOptionDO> options = new ArrayList<>();
        append(Node.builder().write(write).source(write.schema() == null ? Map.of() : write.schema())
                .path("$").semanticPath("$").kind("ROOT").order(0).build(), new Definition(fields, options));
        return new Definition(List.copyOf(fields), List.copyOf(options));
    }

    private static void append(Node node, Definition target) {
        Map<String, Object> source = node.source();
        if (!KEYS.containsAll(source.keySet())) throw new IllegalArgumentException("界面定义包含不支持的元数据字段");
        String code = text(source.getOrDefault("code", source.get("key")));
        String semantic = CutoverChecklistBuiltinFields.semanticKey(new CutoverChecklistBuiltinFields.FieldContext(
                node.write().businessCategoryCode(), node.write().itemTypeCode(), node.semanticPath()));
        CutoverChecklistFieldDefinitionDO field = CutoverChecklistFieldDefinitionDO.builder()
                .id(IdWorker.getId()).itemDefinitionRevisionId(node.write().itemDefinitionRevisionId())
                .customChecklistItemId(node.write().customChecklistItemId()).parentFieldId(node.parentId())
                .nodePath(node.path()).nodeKindCode(node.kind()).fieldCode(code).sourceName(text(source.get("name")))
                .label(text(source.get("label"))).dataTypeCode(text(source.get("type")))
                .requiredFlag(bool(source.get("required"), false)).maxLength(integer(source.get("maxLength")))
                .sortOrder(node.order()).builtinFieldCode(semantic)
                .extensionFieldKey(semantic == null ? (code == null ? "$" : code) : null).build();
        field.setTenantId(node.write().tenantId());
        applyVisibility(source, field);
        applySource(source, field);
        target.fields().add(field);
        appendOptions(source, field, target.options());
        for (String collection : List.of("fields", "columns")) {
            Object children = source.get(collection);
            if (children == null) continue;
            if (!(children instanceof List<?> values)) throw new IllegalArgumentException(collection + "必须为数组");
            for (int i = 0; i < values.size(); i++) {
                Map<String, Object> child = object(values.get(i));
                String childCode = text(child.getOrDefault("code", child.get("key")));
                if (childCode == null || childCode.isBlank()) throw new IllegalArgumentException("子字段必须指定编码");
                String path = "$".equals(node.semanticPath()) ? childCode : node.semanticPath() + "." + childCode;
                append(Node.builder().write(node.write()).source(child).parentId(field.getId())
                        .path(node.path() + "." + collection + "[" + i + "]").semanticPath(path)
                        .kind("fields".equals(collection) ? "FIELD" : "COLUMN").order(i).build(), target);
            }
        }
    }

    private static void applyVisibility(Map<String, Object> source, CutoverChecklistFieldDefinitionDO field) {
        if (source.get("visibleWhen") == null) return;
        Map<String, Object> condition = object(source.get("visibleWhen"));
        if (!condition.keySet().equals(Set.of("field", "equals"))) throw new IllegalArgumentException("显示条件必须包含field和equals");
        field.setVisibleWhenFieldPath(text(condition.get("field")));
        if (field.getVisibleWhenFieldPath() == null || field.getVisibleWhenFieldPath().isBlank()) {
            throw new IllegalArgumentException("显示条件字段不能为空");
        }
        CutoverMetadataScalar value = CutoverMetadataScalar.from(condition.get("equals"));
        field.setVisibleEqualsTypeCode(value.type());
        field.setVisibleEqualsText(value.text());
        field.setVisibleEqualsNumber(value.number());
        field.setVisibleEqualsBoolean(value.bool());
        field.setVisibleEqualsDatetime(value.datetime());
    }

    private static void applySource(Map<String, Object> source, CutoverChecklistFieldDefinitionDO field) {
        Object value = source.get("source");
        if (value == null) return;
        if (value instanceof String code) {
            field.setValueSourceCode(code);
            return;
        }
        Map<String, Object> origin = object(value);
        if (!Set.of("file", "sheet", "row", "originalName", "part", "split").containsAll(origin.keySet())) {
            throw new IllegalArgumentException("检查项来源包含未知字段");
        }
        field.setSourceFile(text(origin.get("file")));
        field.setSourceSheet(text(origin.get("sheet")));
        field.setSourceRow(integer(origin.get("row")));
        field.setSourceOriginalName(text(origin.get("originalName")));
        field.setSourcePart(integer(origin.get("part")));
        field.setSourceSplitFlag(bool(origin.get("split"), null));
    }

    private static void appendOptions(Map<String, Object> source, CutoverChecklistFieldDefinitionDO field,
                                      List<CutoverChecklistFieldOptionDO> target) {
        Object raw = source.get("options");
        if (raw == null) return;
        if (!(raw instanceof List<?> options)) throw new IllegalArgumentException("选项必须是数组");
        for (int i = 0; i < options.size(); i++) {
            Object rawOption = options.get(i);
            Map<String, Object> option = rawOption instanceof Map<?, ?> ? object(rawOption) : null;
            if (option != null && (!Set.of("label", "value").containsAll(option.keySet()) || !option.containsKey("value"))) {
                throw new IllegalArgumentException("选项对象只能包含label和value");
            }
            CutoverMetadataScalar value = CutoverMetadataScalar.from(option == null ? rawOption : option.get("value"));
            CutoverChecklistFieldOptionDO row = CutoverChecklistFieldOptionDO.builder().id(IdWorker.getId())
                    .fieldDefinitionId(field.getId()).optionNo(i).label(option == null ? null : text(option.get("label")))
                    .valueTypeCode(value.type()).valueText(value.text()).valueNumber(value.number())
                    .valueBoolean(value.bool()).valueDatetime(value.datetime()).build();
            row.setTenantId(field.getTenantId());
            target.add(row);
        }
    }

    public static Map<String, Object> decode(Definition definition) {
        List<CutoverChecklistFieldDefinitionDO> roots = definition.fields().stream()
                .filter(field -> field.getParentFieldId() == null).toList();
        if (roots.size() != 1) throw new IllegalStateException("采集项字段定义缺失或存在多个根节点");
        return decodeNode(roots.getFirst(), definition);
    }

    private static Map<String, Object> decodeNode(CutoverChecklistFieldDefinitionDO field, Definition definition) {
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "code", field.getFieldCode());
        put(value, "name", field.getSourceName());
        put(value, "label", field.getLabel());
        put(value, "type", field.getDataTypeCode());
        if (Boolean.TRUE.equals(field.getRequiredFlag())) value.put("required", true);
        put(value, "maxLength", field.getMaxLength());
        if (field.getVisibleWhenFieldPath() != null) {
            Map<String, Object> condition = new LinkedHashMap<>();
            condition.put("field", field.getVisibleWhenFieldPath());
            condition.put("equals", new CutoverMetadataScalar(field.getVisibleEqualsTypeCode(), field.getVisibleEqualsText(),
                    field.getVisibleEqualsNumber(), field.getVisibleEqualsBoolean(), field.getVisibleEqualsDatetime()).value());
            value.put("visibleWhen", condition);
        }
        Map<String, Object> source = new LinkedHashMap<>();
        put(source, "file", field.getSourceFile());
        put(source, "sheet", field.getSourceSheet());
        put(source, "row", field.getSourceRow());
        put(source, "originalName", field.getSourceOriginalName());
        put(source, "part", field.getSourcePart());
        put(source, "split", field.getSourceSplitFlag());
        if (!source.isEmpty()) value.put("source", source);
        else put(value, "source", field.getValueSourceCode());
        List<Object> options = definition.options().stream().filter(option -> field.getId().equals(option.getFieldDefinitionId()))
                .sorted(Comparator.comparing(CutoverChecklistFieldOptionDO::getOptionNo)).map(option -> {
                    Object scalar = new CutoverMetadataScalar(option.getValueTypeCode(), option.getValueText(),
                            option.getValueNumber(), option.getValueBoolean(), option.getValueDatetime()).value();
                    if (option.getLabel() == null) return scalar;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("label", option.getLabel());
                    item.put("value", scalar);
                    return (Object) item;
                }).toList();
        if (!options.isEmpty()) value.put("options", options);
        for (String kind : List.of("FIELD", "COLUMN")) {
            List<Map<String, Object>> children = definition.fields().stream()
                    .filter(child -> Objects.equals(field.getId(), child.getParentFieldId()) && kind.equals(child.getNodeKindCode()))
                    .sorted(Comparator.comparing(CutoverChecklistFieldDefinitionDO::getSortOrder))
                    .map(child -> decodeNode(child, definition)).toList();
            if (!children.isEmpty()) value.put("FIELD".equals(kind) ? "fields" : "columns", children);
        }
        return value;
    }

    private static void put(Map<String, Object> target, String key, Object value) { if (value != null) target.put(key, value); }

    private static String text(Object value) {
        if (value == null || value instanceof String) return (String) value;
        throw new IllegalArgumentException("配置文本字段类型不正确");
    }

    private static Integer integer(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return new java.math.BigDecimal(number.toString()).intValueExact();
        throw new IllegalArgumentException("配置整数字段类型不正确");
    }

    private static Boolean bool(Object value, Boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        throw new IllegalArgumentException("配置布尔字段类型不正确");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map && map.keySet().stream().allMatch(String.class::isInstance)) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("配置节点必须是对象");
    }
}
