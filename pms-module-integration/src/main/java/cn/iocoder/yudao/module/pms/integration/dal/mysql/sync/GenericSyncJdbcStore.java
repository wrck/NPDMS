package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.sync.generic.GenericTargetCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.generic.GenericTargetCatalog.identifier;

/** Infrastructure-only SQL compiler for the explicitly authorized configurable importer.
 * Identifiers are server-catalog capabilities; all values are bound. Ordinary business queries remain in Mapper XML. */
@Repository
public class GenericSyncJdbcStore {
    private final JdbcTemplate jdbc;
    public GenericSyncJdbcStore(javax.sql.DataSource dataSource) {this.jdbc=new JdbcTemplate(dataSource);}
    public record Column(String name, boolean nullable, String defaultValue, boolean generated, int length, int sqlType) {}
    public Map<String,Column> columns(GenericTargetCatalog.Table table) {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Map<String,Column>>) connection -> {
            Map<String,Column> columns=new LinkedHashMap<>();
            try(var result=connection.getMetaData().getColumns(connection.getCatalog(),null,table.table(),null)) {
                while(result.next()) {
                    if(!table.table().equalsIgnoreCase(result.getString("TABLE_NAME")))continue;
                    String name=result.getString("COLUMN_NAME");
                    columns.put(name,new Column(name,result.getInt("NULLABLE")!=java.sql.DatabaseMetaData.columnNoNulls,
                            result.getString("COLUMN_DEF"),"YES".equals(result.getString("IS_AUTOINCREMENT")),
                            result.getInt("COLUMN_SIZE"),result.getInt("DATA_TYPE")));
                }
            }
            if(columns.isEmpty())throw new IllegalArgumentException("目标表不存在: "+table.table());
            return columns;
        });
    }
    public void validateValues(Map<String,Column> columns, Map<String,Object> values, boolean insert) {
        for(var value:values.entrySet()) {
            var column=columns.get(value.getKey());
            if(column==null)throw new IllegalArgumentException("目标库缺少字段，请先应用前向迁移: "+value.getKey());
            if(value.getValue()==null && !column.nullable())throw new IllegalArgumentException("目标非空字段缺值: "+column.name());
            if(value.getValue() instanceof String text && Set.of(java.sql.Types.VARCHAR,java.sql.Types.CHAR).contains(column.sqlType()) && text.codePointCount(0,text.length())>column.length())throw new IllegalArgumentException("目标字段超长: "+column.name());
        }
        if(insert)for(var column:columns.values())if(!column.nullable()&&!column.generated()&&column.defaultValue()==null&&!values.containsKey(column.name()))throw new IllegalArgumentException("必填目标字段未映射: "+column.name());
    }
    public record Match(GenericTargetCatalog.Table table, Map<String,Object> values, boolean lock) {}
    public List<Map<String,Object>> find(Match query) {
        if (query.values().isEmpty() || query.values().values().stream().anyMatch(Objects::isNull)) return List.of();
        var table = query.table();
        List<Object> args = new ArrayList<>();
        StringJoiner where = new StringJoiner(" AND ");
        if (table.tenantColumn() != null) {
            where.add(identifier(table.tenantColumn()) + " = ?"); args.add(TenantContextHolder.getRequiredTenantId());
        }
        query.values().forEach((column,value) -> {
            requireColumn(table,column); where.add(identifier(column) + " = ?"); args.add(value);
        });
        // Do not filter deleted rows: encountering one must be a conflict, never a resurrection.
        return jdbc.queryForList("SELECT * FROM " + identifier(table.table()) + " WHERE " + where
                + " LIMIT 2" + (query.lock() ? " FOR UPDATE" : ""), args.toArray());
    }
    public void insert(GenericTargetCatalog.Table table, Map<String,Object> values) {
        values.keySet().forEach(column -> requireWriteColumn(table,column));
        String columns = String.join(",", values.keySet().stream().map(GenericTargetCatalog::identifier).toList());
        jdbc.update("INSERT INTO " + identifier(table.table()) + " (" + columns + ") VALUES ("
                + String.join(",", Collections.nCopies(values.size(), "?")) + ")", values.values().toArray());
    }
    public void update(GenericTargetCatalog.Table table, Map<String,Object> before, Map<String,Object> values) {
        if (values.isEmpty()) return;
        List<Object> args = new ArrayList<>(values.values());
        var set = new StringJoiner(",");
        values.keySet().forEach(column -> {requireWriteColumn(table,column);set.add(identifier(column) + " = ?");});
        if (table.versioned()) set.add("version = version + 1");
        String where = "id = ? AND " + identifier(table.tenantColumn()) + " = ?";
        args.add(before.get("id"));args.add(TenantContextHolder.getRequiredTenantId());
        if (table.deletedColumn() != null) where += " AND " + identifier(table.deletedColumn()) + " = 0";
        if (table.versioned()) {where += " AND version = ?";args.add(before.get("version"));}
        if (jdbc.update("UPDATE " + identifier(table.table()) + " SET " + set + " WHERE " + where,args.toArray()) != 1)
            throw new IllegalStateException("目标记录版本变化，当前分块已回滚");
    }
    private static void requireColumn(GenericTargetCatalog.Table table,String column) {
        if (!table.columns().contains(column) && !"id".equals(column)) throw new IllegalArgumentException("未开放字段: " + column);
    }
    private static void requireWriteColumn(GenericTargetCatalog.Table table,String column) {
        if (!table.writable()) throw new IllegalArgumentException("目标只读");
        if (!table.columns().contains(column) && !table.insertDefaults().containsKey(column) && !Set.of("id","tenant_id","creator","updater","create_time","update_time","version","deleted").contains(column))
            throw new IllegalArgumentException("未开放写入字段: " + column);
    }
}
