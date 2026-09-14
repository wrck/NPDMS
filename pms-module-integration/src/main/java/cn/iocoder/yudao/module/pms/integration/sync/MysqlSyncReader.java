package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MysqlSyncReader {
    private final ExternalDataSourceApi sources;
    public record SourceRows(String object, String sourceObject, List<Map<String,Object>> rows) {}
    public record Snapshot(LocalDateTime upper, List<SourceRows> objects, long bytes) {}
    public record Column(String name, String type, boolean nullable) {}
    public record Table(String name, String type) {}
    public record Metadata(List<Table> tables) {}
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern UNSAFE = Pattern.compile(
            "(?i)\\b(INTO|OUTFILE|DUMPFILE|FOR\\s+UPDATE|FOR\\s+SHARE|LOCK\\s+IN|SLEEP|BENCHMARK|GET_LOCK|RELEASE_LOCK|LOAD_FILE)\\b|:=|@");

    public List<Table> tables(Long connectionId) throws SQLException {
        try (Connection c=sources.openReadOnly(connectionId)) {
            List<Table> out=new ArrayList<>();
            try(ResultSet r=c.getMetaData().getTables(c.getCatalog(),null,"%",new String[]{"TABLE","VIEW"})) {
                while(r.next()) out.add(new Table(r.getString("TABLE_NAME"),r.getString("TABLE_TYPE")));
            }
            return out;
        }
    }
    public List<Column> columns(Long connectionId,String table) throws SQLException {
        identifier(table);
        try(Connection c=sources.openReadOnly(connectionId)) {
            List<Column> out=new ArrayList<>();
            try(ResultSet r=c.getMetaData().getColumns(c.getCatalog(),null,table,"%")) {
                while(r.next()) out.add(new Column(r.getString("COLUMN_NAME"),r.getString("TYPE_NAME"),
                        r.getInt("NULLABLE")!=DatabaseMetaData.columnNoNulls));
            }
            return out;
        }
    }
    public Snapshot read(SyncDefinition definition, LocalDateTime lower, boolean full) throws SQLException {
        return read(definition,lower,full,null);
    }
    public Snapshot readPage(SyncDefinition definition, SyncPagingState page) throws SQLException {
        return read(definition,null,true,page);
    }
    public SyncPagingState pagingBounds(SyncDefinition definition) throws SQLException {
        try(Connection c=sources.openReadOnly(definition.connectionId())) {
            try(Statement s=c.createStatement()) { s.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY"); }
            LocalDateTime upper;
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT CURRENT_TIMESTAMP(6)")) {
                r.next();upper=r.getTimestamp(1).toLocalDateTime();
            }
            List<Long> bounds=new ArrayList<>();
            for(var source:definition.sources()) {
                var base=compile(source);
                verifyTransactionalTables(c,base.sql(),new HashSet<>());
                String key=identifier(source.sourceKey());
                try(var s=prepare(c,new BoundSql("SELECT MIN("+key+"),MAX("+key+"),COUNT(*),COUNT(DISTINCT "+key+") FROM ("+base.sql()+") sync_bounds",base.values()))) {
                    s.setQueryTimeout(60);
                    try(var r=s.executeQuery()) {
                        r.next();Object min=r.getObject(1),max=r.getObject(2);
                        if(r.getLong(3)!=r.getLong(4))throw new IllegalArgumentException("自动分页来源主键缺失或重复: "+source.object());
                        if(min!=null) pagingKey(min);
                        bounds.add(max==null?0L:pagingKey(max));
                    }
                }
            }
            c.rollback();return new SyncPagingState(0,0,List.copyOf(bounds),0,upper);
        }
    }
    static long pagingKey(Object value) {
        if(!(value instanceof Number)) throw new IllegalArgumentException("自动分页要求正整数来源主键");
        try {
            long key=new java.math.BigDecimal(value.toString()).longValueExact();
            if(key<=0)throw new ArithmeticException();
            return key;
        }catch(ArithmeticException ex){throw new IllegalArgumentException("自动分页要求正整数 Long 来源主键");}
    }
    static BoundSql pageSql(SyncDefinition.Source source, SyncPagingState page, int maxRows) {
        BoundSql base=compile(source);
        List<Object> values=new ArrayList<>(base.values());
        values.add(page.afterId());values.add(page.upperIds().get(page.sourceIndex()));values.add(maxRows);
        String key=identifier(source.sourceKey());
        return new BoundSql("SELECT * FROM ("+base.sql()+") sync_source WHERE "+key+" > ? AND "+key
                +" <= ? ORDER BY "+key+" LIMIT ?",values);
    }
    private Snapshot read(SyncDefinition definition, LocalDateTime lower, boolean full, SyncPagingState page) throws SQLException {
        try(Connection c=sources.openReadOnly(definition.connectionId())) {
            try(Statement s=c.createStatement()) { s.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY"); }
            LocalDateTime upper;
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT CURRENT_TIMESTAMP(6)")) {
                r.next(); upper=r.getTimestamp(1).toLocalDateTime();
            }
            if(lower!=null && upper.isBefore(lower)) throw new IllegalArgumentException("来源时间早于已提交检查点，禁止游标倒退");
            List<SourceRows> result=new ArrayList<>();
            int count=0; long bytes=0;
            var selected=page==null?definition.sources():List.of(definition.sources().get(page.sourceIndex()));
            for(var source:selected) {
                BoundSql base=compile(source);
                verifyTransactionalTables(c,base.sql(),new HashSet<>());
                String sql="SELECT * FROM ("+base.sql()+") sync_source";
                List<Object> values=new ArrayList<>(base.values());
                if(!full) {
                    String timestamp=identifier(source.updatedAt());
                    sql+=" WHERE "+timestamp+" >= ? AND "+timestamp+" < ?";
                    values.add(Timestamp.valueOf(lower.minusSeconds(definition.overlapSeconds())));
                    values.add(Timestamp.valueOf(upper));
                }
                sql+=" ORDER BY "+(source.updatedAt()!=null&&!source.updatedAt().isBlank()
                        ?identifier(source.updatedAt())+", ":"")+identifier(source.sourceKey());
                if(page!=null) { var paged=pageSql(source,page,definition.maxRows());sql=paged.sql();values=paged.values(); }
                List<Map<String,Object>> rows=new ArrayList<>();
                Set<String> keys=new HashSet<>();
                try(PreparedStatement s=prepare(c,new BoundSql(sql,values))) {
                    s.setQueryTimeout(60); s.setMaxRows(definition.maxRows()+1);
                    try(ResultSet r=s.executeQuery()) {
                        var metadata=r.getMetaData(); Set<String> names=new HashSet<>();
                        for(int i=1;i<=metadata.getColumnCount();i++)
                            if(!names.add(metadata.getColumnLabel(i))) throw new IllegalArgumentException("查询输出列名重复");
                        while(r.next()) {
                            Map<String,Object> row=new LinkedHashMap<>();
                            for(int i=1;i<=metadata.getColumnCount();i++) {
                                Object value=r.getObject(i);
                                if(value instanceof byte[] b) value=b.length==1?(int)b[0]:Base64.getEncoder().encodeToString(b);
                                if(value instanceof Timestamp t) value=t.toLocalDateTime().toString();
                                if(value instanceof java.sql.Date d) value=d.toString();
                                if(value instanceof java.sql.Time t) value=t.toString();
                                row.put(metadata.getColumnLabel(i),value);
                            }
                            Object key=row.get(source.sourceKey());
                            if(page!=null) pagingKey(key);
                            if(key==null||key.toString().isBlank()||key.toString().length()>128||!keys.add(key.toString()))
                                throw new IllegalArgumentException("来源主键缺失或重复: "+source.object());
                            if("INCREMENTAL".equals(definition.mode()) && row.get(source.updatedAt())==null)
                                throw new IllegalArgumentException("增量时间字段不可为空");
                            bytes+=JsonUtils.toJsonString(row).getBytes(StandardCharsets.UTF_8).length;
                            if(++count>definition.maxRows() || bytes>definition.maxBytes())
                                throw new IllegalArgumentException("来源数据超过整批容量限制，未写入目标");
                            rows.add(Collections.unmodifiableMap(row));
                        }
                    }
                }
                result.add(new SourceRows(source.object(),source.sourceObject(),List.copyOf(rows)));
            }
            c.rollback();
            return new Snapshot(upper,List.copyOf(result),bytes);
        }
    }

    public record BoundSql(String sql,List<Object> values) {}
    static BoundSql compile(SyncDefinition.Source source) {
        identifier(source.sourceKey());
        if("SQL".equals(source.readMode())) {
            String sql=source.sql()==null?"":source.sql().strip();
            // Lexical check is conservative; parsing additionally enforces a single SELECT.
            if(sql.isBlank() || UNSAFE.matcher(sql).find() || sql.contains(";"))
                throw new IllegalArgumentException("只允许单条无副作用 SELECT");
            var parsed=NamedParameterUtils.parseSqlStatement(sql);
            var parameters=new MapSqlParameterSource(source.parameters()==null?Map.of():source.parameters());
            String bound=NamedParameterUtils.substituteNamedParameters(parsed,parameters);
            Object[] values=NamedParameterUtils.buildValueArray(parsed,parameters,null);
            validateSelect(bound);
            return new BoundSql(bound,Arrays.asList(values));
        }
        if(!"TABLE".equals(source.readMode())) throw new IllegalArgumentException("来源读取模式无效");
        String sql="SELECT "+(source.columns()==null||source.columns().isEmpty()?"*":
                String.join(", ",source.columns().stream().map(MysqlSyncReader::identifier).toList()))
                +" FROM "+identifier(source.table());
        List<Object> parameters=new ArrayList<>();
        List<String> predicates=new ArrayList<>();
        for(var f:source.filters()==null?List.<SyncDefinition.Filter>of():source.filters()) {
            String col=identifier(f.column());
            if(Set.of("IS NULL","IS NOT NULL").contains(f.operator())) predicates.add(col+" "+f.operator());
            else if("IN".equals(f.operator())) {
                if(!(f.value() instanceof List<?> list)) throw new IllegalArgumentException("IN 筛选必须是数组");
                if(list.isEmpty()) predicates.add("1=0");
                else { predicates.add(col+" IN ("+String.join(",",Collections.nCopies(list.size(),"?"))+")"); parameters.addAll(list); }
            } else {
                if(!Set.of("=","<>",">",">=","<","<=","LIKE").contains(f.operator()))
                    throw new IllegalArgumentException("不支持的筛选操作符");
                predicates.add(col+" "+f.operator()+" ?"); parameters.add(f.value());
            }
        }
        if(!predicates.isEmpty()) sql+=" WHERE "+String.join(" AND ",predicates);
        return new BoundSql(sql,parameters);
    }
    static void validateSelect(String sql) {
        try {
            if(!(CCJSqlParserUtil.parse(sql) instanceof Select)) throw new IllegalArgumentException("仅支持 SELECT");
        } catch(Exception ex) { throw new IllegalArgumentException("只读 SQL 解析失败"); }
    }
    private void verifyTransactionalTables(Connection c,String sql,Set<String> visited) throws SQLException {
        List<String> tables;
        try { tables=new TablesNamesFinder().getTableList(CCJSqlParserUtil.parse(sql)); }
        catch(Exception ex) { throw new IllegalArgumentException("无法确认来源表范围"); }
        for(String raw:tables) {
            String table=raw.replace("`","");
            String schema=c.getCatalog();
            if(table.contains(".")) {
                String[] parts=table.split("\\.");
                if(parts.length!=2||!parts[0].equals(schema)) throw new IllegalArgumentException("SQL 不允许跨来源数据库");
                table=parts[1];
            }
            if(!visited.add(table)) continue;
            try(PreparedStatement p=c.prepareStatement(
                    "SELECT TABLE_TYPE, ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
                p.setString(1,schema);p.setString(2,table);
                try(ResultSet r=p.executeQuery()) {
                    if(!r.next()) throw new IllegalArgumentException("无法读取来源表元数据");
                    if("VIEW".equals(r.getString(1))) {
                        try(PreparedStatement v=c.prepareStatement(
                                "SELECT VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
                            v.setString(1,schema);v.setString(2,table);
                            try(ResultSet vr=v.executeQuery()) {
                                if(!vr.next()||vr.getString(1)==null) throw new IllegalArgumentException("无法验证来源视图");
                                verifyTransactionalTables(c,vr.getString(1),visited);
                            }
                        }
                    } else if(!"InnoDB".equalsIgnoreCase(r.getString(2)))
                        throw new IllegalArgumentException("来源表不支持一致性快照");
                }
            }
        }
    }
    static String identifier(String value) {
        if(value==null||!IDENTIFIER.matcher(value).matches()) throw new IllegalArgumentException("来源字段或表名无效");
        return "`"+value+"`";
    }
    static PreparedStatement prepare(Connection connection,BoundSql bound)throws SQLException {
        var factory=new org.springframework.jdbc.core.PreparedStatementCreatorFactory(bound.sql());
        for(int i=0;i<bound.values().size();i++)
            factory.addParameter(new org.springframework.jdbc.core.SqlParameter(org.springframework.jdbc.core.SqlTypeValue.TYPE_UNKNOWN));
        return factory.newPreparedStatementCreator(bound.values()).createPreparedStatement(connection);
    }
}
