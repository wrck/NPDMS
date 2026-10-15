package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import java.util.*;

/** One SQL row per business identity, with the original source rows retained for mapping and evidence. */
final class SyncSourceProjection {
    private SyncSourceProjection() {}

    record Input(SyncDefinition definition, SyncDefinition.Source source, List<Map<String,Object>> rows) {}

    static boolean reference(SyncDefinition.Source source) { return source.queryRef() != null; }
    static String readKey(SyncDefinition.Source source) {
        return source.rowProjection() == null ? source.sourceKey() : source.groupKey();
    }

    static void validate(SyncDefinition definition) {
        Map<String,SyncDefinition.Source> preceding = new LinkedHashMap<>();
        for (var source : definition.sources()) {
            if (reference(source) || source.rowProjection() != null || source.groupKey() != null) {
                if (!"ONCE".equals(definition.mode()) || "KEYSET_PAGING".equals(definition.effectiveReadStrategy())
                        || !"RESTART_ALL".equals(definition.effectiveRestartPolicy())
                        || source.sourceKeyColumns() != null && !source.sourceKeyColumns().isEmpty()
                        || !"SQL".equals(source.readMode()))
                    throw new IllegalArgumentException("合并来源查询仅支持一次性SQL读取和完整重放，不支持游标续接");
            }
            if (reference(source)) {
                var root = preceding.get(source.queryRef());
                if (root == null || reference(root) || root.rowProjection() == null
                        || source.sql() != null || source.table() != null || source.groupKey() != null
                        || source.rowProjection() != null || source.parameters() != null && !source.parameters().isEmpty()
                        || source.filters() != null && !source.filters().isEmpty())
                    throw new IllegalArgumentException("共享查询必须引用前置分组SQL，不能另带查询或覆盖范围参数");
            } else if (source.rowProjection() != null || source.groupKey() != null) {
                MysqlSyncReader.identifier(source.groupKey());
                MysqlSyncReader.identifier(source.rowProjection());
            }
            if (preceding.put(source.object(), source) != null)
                throw new IllegalArgumentException("来源对象身份重复");
        }
    }

    static List<MysqlSyncReader.SourceRows> expand(Input input) {
        var root = input.source();
        if (root.rowProjection() == null)
            return List.of(new MysqlSyncReader.SourceRows(root.object(), root.sourceObject(), input.rows()));
        Map<String,SyncDefinition.Source> sources = new LinkedHashMap<>();
        Map<String,List<Map<String,Object>>> rows = new LinkedHashMap<>();
        Map<String,Set<String>> keys = new HashMap<>();
        for (var source : input.definition().sources()) {
            if (source == root || Objects.equals(source.object(), root.object())
                    || Objects.equals(source.queryRef(), root.object())) {
                sources.put(source.object(), source);
                rows.put(source.object(), new ArrayList<>());
                keys.put(source.object(), new HashSet<>());
            }
        }
        for (var group : input.rows()) {
            Object raw = group.get(root.rowProjection());
            if (raw == null) throw new IllegalArgumentException("合并查询缺少原行追溯投影");
            var projection = JsonUtils.parseTree(raw instanceof String text ? text : JsonUtils.toJsonString(raw));
            if (!projection.isArray()) throw new IllegalArgumentException("合并查询的原行投影必须是数组");
            for (var item : projection) {
                // CASE inside JSON_ARRAYAGG retains null slots for explicitly excluded source rows.
                if (item.isNull()) continue;
                String object = item.path("object").asText();
                var source = sources.get(object);
                if (source == null || !item.path("fields").isObject())
                    throw new IllegalArgumentException("合并查询返回未声明的来源对象或缺失原字段");
                Map<String,Object> fields = JsonUtils.parseMap(item.path("fields").toString());
                // JDBC exposes original JSON columns as JSON text. The outer projection embeds
                // objects/arrays; restore that column representation before existing field mapping.
                fields.replaceAll((name,value) -> value instanceof Map<?,?> || value instanceof List<?>
                        ? item.path("fields").path(name).toString() : value);
                Object groupKey = group.get(root.groupKey());
                if (fields.containsKey(root.groupKey()) || groupKey == null)
                    throw new IllegalArgumentException("合并查询分组身份缺失或覆盖了原字段");
                fields.put(root.groupKey(), groupKey);
                Object key = fields.get(source.sourceKey());
                if (key == null || key.toString().isBlank() || key.toString().length() > 128
                        || !keys.get(object).add(key.toString()))
                    throw new IllegalArgumentException("合并查询的原来源身份缺失或重复: " + object);
                rows.get(object).add(Collections.unmodifiableMap(fields));
                if(input.definition().maxRows()>0 && rows.values().stream().mapToInt(List::size).sum()>input.definition().maxRows())
                    throw new IllegalArgumentException("合并查询的原行超过容量限制");
            }
        }
        return sources.values().stream().map(s -> new MysqlSyncReader.SourceRows(s.object(), s.sourceObject(),
                List.copyOf(rows.get(s.object())))).toList();
    }
}
