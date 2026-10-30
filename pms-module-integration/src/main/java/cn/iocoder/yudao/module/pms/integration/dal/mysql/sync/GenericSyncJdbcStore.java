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
    /** 行内列形状一致的键集合一次往返取回全部现存行（不过滤删除行）；键元组较多时分段执行。
     * 组内恒定列提升为等值、仅一列变化时改写为 IN；多列变化用派生表 JOIN 逐键索引探测，避免 OR 析取链的劣化计划。 */
    public List<Map<String,Object>> findByKeys(GenericTargetCatalog.Table table, List<Map<String,Object>> keys, boolean lock) {
        if (keys.isEmpty()) return List.of();
        var sample = keys.getFirst();
        sample.keySet().forEach(column -> requireColumn(table,column));
        Map<String,Object> fixed = new LinkedHashMap<>();
        List<String> varying = new ArrayList<>();
        for (var column : sample.keySet()) {
            Object first = sample.get(column);
            boolean constant = keys.stream().allMatch(key -> Objects.equals(key.get(column),first));
            if (constant) fixed.put(column,first); else varying.add(column);
        }
        List<Map<String,Object>> all = new ArrayList<>();
        for (int start = 0; start < keys.size(); start += KEY_FETCH_BATCH) {
            var page = keys.subList(start, Math.min(start + KEY_FETCH_BATCH, keys.size()));
            for (var key : page)
                if (!key.keySet().equals(sample.keySet())) throw new IllegalArgumentException("批量取数键形状不一致");
            List<Object> args = new ArrayList<>();
            StringJoiner where = new StringJoiner(" AND ");
            if (table.tenantColumn() != null) { where.add(identifier(table.tenantColumn()) + " = ?"); args.add(TenantContextHolder.getRequiredTenantId()); }
            fixed.forEach((column,value) -> {
                requireColumn(table,column); where.add(identifier(column) + " = ?"); args.add(value);
            });
            if (varying.size() == 1) {
                String column = varying.getFirst();
                requireColumn(table,column);
                where.add(identifier(column) + " IN (" + String.join(",", Collections.nCopies(page.size(), "?")) + ")");
                for (var key : page) args.add(key.get(column));
                all.addAll(jdbc.queryForList("SELECT * FROM " + identifier(table.table()) + " WHERE " + where
                        + (lock ? " FOR UPDATE" : ""), args.toArray()));
            } else if (varying.size() > 1) {
                StringJoiner branches = new StringJoiner(" UNION ALL ");
                List<Object> branchArgs = new ArrayList<>();
                for (int index = 0; index < page.size(); index++) {
                    var key = page.get(index);
                    for (var column : varying) branchArgs.add(key.get(column));
                    branches.add("SELECT " + String.join(",", index == 0
                            ? varying.stream().map(GenericTargetCatalog::identifier).map(alias -> "? AS " + alias).toList()
                            : Collections.nCopies(varying.size(), "?")) + " FROM dual");
                }
                StringJoiner on = new StringJoiner(" AND ");
                for (var column : varying) on.add("t." + identifier(column) + " = req." + identifier(column));
                StringJoiner joinWhere = new StringJoiner(" AND ");
                if (table.tenantColumn() != null) { joinWhere.add("t." + identifier(table.tenantColumn()) + " = ?"); branchArgs.add(TenantContextHolder.getRequiredTenantId()); }
                fixed.forEach((column,value) -> { joinWhere.add("t." + identifier(column) + " = ?"); branchArgs.add(value); });
                all.addAll(jdbc.queryForList("SELECT t.* FROM " + identifier(table.table()) + " t JOIN ("
                        + branches + ") req ON " + on + " WHERE " + joinWhere
                        + (lock ? " FOR UPDATE" : ""), branchArgs.toArray()));
            } else {
                all.addAll(jdbc.queryForList("SELECT * FROM " + identifier(table.table()) + " WHERE " + where
                        + (lock ? " FOR UPDATE" : ""), args.toArray()));
            }
        }
        return all;
    }
    private static final int KEY_FETCH_BATCH = 500;
    /** 行内列顺序与首行完全一致的批次合并为多值语句，减少全量装载时的逐行往返；语句行数取任务配置的 chunkSize。 */
    public void insertBatch(GenericTargetCatalog.Table table, List<Map<String,Object>> rows, int chunkSize) {
        if (rows.isEmpty()) return;
        int size = chunkSize > 0 ? chunkSize : INSERT_BATCH_SIZE;
        rows.getFirst().keySet().forEach(column -> requireWriteColumn(table,column));
        List<Object[]> args = rows.stream().map(row -> {
            if (!row.keySet().equals(rows.getFirst().keySet())) throw new IllegalArgumentException("批量插入列签名不一致");
            return row.values().toArray();
        }).toList();
        for (int start = 0; start < args.size(); start += size) {
            var page = args.subList(start, Math.min(start + size, args.size()));
            jdbc.update(insertSql(table, rows.getFirst().keySet(), page.size()),
                    page.stream().flatMap(java.util.Arrays::stream).toArray());
        }
    }
    private static final int INSERT_BATCH_SIZE = 1000;
    private String insertSql(GenericTargetCatalog.Table table, Set<String> columns, int rowCount) {
        return "INSERT INTO " + identifier(table.table()) + " ("
                + String.join(",", columns.stream().map(GenericTargetCatalog::identifier).toList())
                + ") VALUES " + String.join(",", Collections.nCopies(rowCount,
                        "(" + String.join(",", Collections.nCopies(columns.size(), "?")) + ")"));
    }
    public record BatchUpdate(GenericTargetCatalog.Table table, Map<String,Object> before, Map<String,Object> values, int bumps) {}
    /** 同行更新链已合并（before 为链首锚点）且目标行互不重叠的批量更新：按列签名分页，
     * 逐行锚定 (id,version) 的 CASE 更新保证任一行版本不符即匹配数不足并回滚整块。 */
    public void updateBatch(GenericTargetCatalog.Table table, List<BatchUpdate> ops) {
        if (ops.isEmpty()) return;
        Map<String,List<BatchUpdate>> groups = new LinkedHashMap<>();
        for (var op : ops) {
            op.values().keySet().forEach(column -> requireWriteColumn(table,column));
            groups.computeIfAbsent(String.join(",", op.values().keySet()), key -> new ArrayList<>()).add(op);
        }
        for (var entry : groups.entrySet()) {
            var list = entry.getValue();
            List<String> columns = new ArrayList<>(list.getFirst().values().keySet());
            for (int start = 0; start < list.size(); start += UPDATE_BATCH_SIZE) {
                var page = list.subList(start, Math.min(start + UPDATE_BATCH_SIZE, list.size()));
                List<Object> setArgs = new ArrayList<>();
                StringBuilder set = new StringBuilder();
                for (var column : columns) {
                    if (set.length() > 0) set.append(',');
                    set.append(identifier(column)).append(" = CASE `id`");
                    for (var op : page) {
                        set.append(" WHEN ? THEN ?");
                        setArgs.add(op.before().get("id"));
                        setArgs.add(op.values().get(column));
                    }
                    set.append(" ELSE ").append(identifier(column)).append(" END");
                }
                if (table.versioned()) {
                    set.append(", version = CASE `id`");
                    for (var op : page) {
                        set.append(" WHEN ? THEN ?");
                        setArgs.add(op.before().get("id"));
                        setArgs.add(((Number) op.before().get("version")).intValue() + op.bumps());
                    }
                    set.append(" ELSE version END");
                }
                // 目标行在预取时已 FOR UPDATE 锁定（或为本分块插入），版本不可能被并发改变；
                // 匹配数不足即说明行缺失或被删除，回滚整块。
                List<Object> whereArgs = new ArrayList<>();
                whereArgs.add(TenantContextHolder.getRequiredTenantId());
                StringBuilder where = new StringBuilder(identifier(table.tenantColumn()) + " = ?");
                if (table.deletedColumn() != null) where.append(" AND ").append(identifier(table.deletedColumn())).append(" = 0");
                where.append(" AND ").append(identifier("id")).append(" IN (")
                        .append(String.join(",", Collections.nCopies(page.size(), "?"))).append(")");
                for (var op : page) whereArgs.add(op.before().get("id"));
                if (jdbc.update("UPDATE " + identifier(table.table()) + " SET " + set + " WHERE " + where,
                        concat(setArgs, whereArgs)) != page.size())
                    throw new IllegalStateException("目标记录版本变化，当前分块已回滚");
            }
        }
    }
    private static final int UPDATE_BATCH_SIZE = 100;
    private static Object[] concat(List<Object> left, List<Object> right) {
        List<Object> all = new ArrayList<>(left.size() + right.size());
        all.addAll(left);all.addAll(right);
        return all.toArray();
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
