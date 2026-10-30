package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

/** Server-owned capability directory, never populated from a task request. No entity adapters are needed. */
@Component
public class GenericTargetCatalog {
    public record Table(String table, String label, String context, boolean writable, boolean immutable,
                        List<String> columns, List<String> updateColumns, List<List<String>> keys,
                        Map<String,Object> insertDefaults, String ownerColumn, String tenantColumn,
                        String deletedColumn, boolean versioned, List<String> immutableIgnoreColumns) {}
    private final Map<String,Table> tables = new LinkedHashMap<>();
    public GenericTargetCatalog() {
        try (var in = new ClassPathResource("sync/generic-targets.json").getInputStream()) {
            for (Table table : JsonUtils.parseObject(in.readAllBytes(), Table[].class)) {
                identifier(table.table());
                table.columns().forEach(GenericTargetCatalog::identifier);
                if (tables.put(table.table(), table) != null) throw new IllegalStateException("重复目标表");
            }
        } catch (IOException e) { throw new IllegalStateException("无法加载同步目标目录", e); }
    }
    public List<Table> tables() { return List.copyOf(tables.values()); }
    public Table required(String name) {
        var table = tables.get(name);
        if (table == null) throw new IllegalArgumentException("目标表未开放同步: " + name);
        return table;
    }
    public static String identifier(String value) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_]{0,63}"))
            throw new IllegalArgumentException("字段或表标识无效");
        return "`" + value + "`";
    }
}
