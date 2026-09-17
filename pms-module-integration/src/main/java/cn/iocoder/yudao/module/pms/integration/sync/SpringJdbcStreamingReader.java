package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/** Executes one source SQL cursor and emits bounded chunks without materializing the full result set. */
public class SpringJdbcStreamingReader {
    private final Optional<ExternalDataSourceApi> sources;

    public SpringJdbcStreamingReader(Optional<ExternalDataSourceApi> sources) {
        this.sources=sources;
    }
    SpringJdbcStreamingReader(ExternalDataSourceApi source) {
        this(Optional.of(source));
    }

    public record StreamChunk(LocalDateTime upper, int sourceIndex, String object, String sourceObject,
                              List<Map<String,Object>> rows, Object lastKey, long bytes,
                              boolean sourceComplete, int sequence) {}
    public record StreamResult(LocalDateTime upper, long rows, int chunks) {}

    public StreamResult stream(SyncDefinition definition, LocalDateTime lower, boolean full,
                               SyncStreamingState resume, Consumer<StreamChunk> consumer) throws SQLException {
        var sourceApi=sources.orElseThrow(()->new IllegalStateException("外部数据源服务不可用"));
        try (Connection connection=sourceApi.openReadOnly(definition.connectionId())) {
            SingleConnectionDataSource dataSource=new SingleConnectionDataSource(connection,true);
            JdbcTemplate jdbc=new JdbcTemplate(dataSource);
            // Capture the incremental upper bound before opening the repeatable snapshot. Any later commit may be
            // visible in the snapshot, but the upper-bound predicate leaves it for the next run instead of losing it.
            LocalDateTime now=currentTime(jdbc);
            if(lower!=null && now.isBefore(lower))
                throw new IllegalArgumentException("来源时间早于已提交检查点，禁止游标倒退");
            LocalDateTime upper=resume!=null&&resume.upper()!=null?resume.upper():now;
            if(now.isBefore(upper)) throw new IllegalArgumentException("来源时间早于流式断点上界，禁止恢复游标倒退");
            connection.setAutoCommit(false);
            startSnapshot(connection);
            int startSource=resume==null?0:resume.sourceIndex();
            long[] totalRows={0};
            int[] sequence={resume==null?0:resume.committedChunks()};
            for(int sourceIndex=startSource;sourceIndex<definition.sources().size();sourceIndex++) {
                var source=definition.sources().get(sourceIndex);
                Object checkpoint=resume!=null&&sourceIndex==resume.sourceIndex()?resume.lastKey():null;
                streamSource(jdbc,connection,definition,source,sourceIndex,lower,upper,full,checkpoint,sequence,totalRows,consumer);
                consumer.accept(new StreamChunk(upper,sourceIndex,source.object(),source.sourceObject(),List.of(),checkpoint,0,true,sequence[0]));
                resume=null;
            }
            connection.rollback();
            return new StreamResult(upper,totalRows[0],sequence[0]);
        }
    }

    private void streamSource(JdbcTemplate jdbc,Connection connection,SyncDefinition definition,SyncDefinition.Source source,
                              int sourceIndex,LocalDateTime lower,LocalDateTime upper,boolean full,Object checkpoint,
                              int[] sequence,long[] totalRows,Consumer<StreamChunk> consumer)throws SQLException {
        var base=MysqlSyncReader.compile(source);
        verifyTransactionalTables(connection,base.sql(),new HashSet<>());
        List<Object> values=new ArrayList<>(base.values());
        List<String> predicates=new ArrayList<>();
        if(!full) {
            String updatedAt=MysqlSyncReader.identifier(source.updatedAt());
            predicates.add(updatedAt+" >= ?");
            predicates.add(updatedAt+" < ?");
            values.add(Timestamp.valueOf(lower.minusSeconds(definition.overlapSeconds())));
            values.add(Timestamp.valueOf(upper));
        }
        boolean checkpointed="CHECKPOINT_KEY".equals(definition.effectiveRestartPolicy());
        String key=MysqlSyncReader.identifier(source.sourceKey());
        if(checkpointed&&checkpoint!=null) {
            predicates.add(key+" > ?");
            values.add(checkpoint);
        }
        String sql="SELECT * FROM ("+base.sql()+") sync_source";
        if(!predicates.isEmpty())sql+=" WHERE "+String.join(" AND ",predicates);
        // Stable source-key ordering keeps duplicate detection O(1) and makes chunk boundaries deterministic.
        sql+=" ORDER BY "+key;
        final String query=sql;
        final List<Object> args=List.copyOf(values);
        final int fetchSize=jdbcFetchSize(connection,definition.effectiveFetchSize());
        final int chunkSize=definition.effectiveChunkSize();
        final long maxBytes=definition.maxBytes();
        final String[] previous={checkpoint==null?null:checkpoint.toString()};
        jdbc.execute(con->{
            PreparedStatement ps=con.prepareStatement(query,ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(fetchSize);
            if(definition.queryTimeoutSeconds()>0)ps.setQueryTimeout(definition.queryTimeoutSeconds());
            for(int i=0;i<args.size();i++)ps.setObject(i+1,args.get(i));
            return ps;
        },ps->{
            try(ResultSet rs=ps.executeQuery()) {
                ResultSetMetaData metadata=rs.getMetaData();
                List<String> names=new ArrayList<>();
                Set<String> uniqueColumns=new HashSet<>();
                for(int i=1;i<=metadata.getColumnCount();i++) {
                    String name=metadata.getColumnLabel(i);
                    if(!uniqueColumns.add(name))throw new IllegalArgumentException("查询输出列名重复");
                    names.add(name);
                }
                List<Map<String,Object>> chunk=new ArrayList<>(chunkSize);
                long chunkBytes=0;
                Object lastKey=checkpoint;
                while(rs.next()) {
                    Map<String,Object> row=new LinkedHashMap<>();
                    for(int i=1;i<=metadata.getColumnCount();i++)row.put(names.get(i-1),normalize(rs.getObject(i)));
                    Object rawKey=row.get(source.sourceKey());
                    if(rawKey==null||rawKey.toString().isBlank()||rawKey.toString().length()>128)
                        throw new IllegalArgumentException("来源主键缺失或无效: "+source.object());
                    String sourceKey=rawKey.toString();
                    if(Objects.equals(previous[0],sourceKey))throw new IllegalArgumentException("来源主键重复: "+source.object());
                    previous[0]=sourceKey;
                    if("INCREMENTAL".equals(definition.mode()) && row.get(source.updatedAt())==null)
                        throw new IllegalArgumentException("增量时间字段不可为空");
                    long rowBytes=estimateBytes(row);
                    if(rowBytes>maxBytes)throw new IllegalArgumentException("单行来源数据超过单批容量限制");
                    if(!chunk.isEmpty()&&(chunk.size()>=chunkSize||chunkBytes+rowBytes>maxBytes)) {
                        emit(upper,sourceIndex,source,chunk,lastKey,chunkBytes,sequence,totalRows,consumer);
                        chunk=new ArrayList<>(chunkSize);chunkBytes=0;
                    }
                    chunk.add(Collections.unmodifiableMap(row));chunkBytes+=rowBytes;lastKey=rawKey;
                    if(chunk.size()>=chunkSize||chunkBytes>=maxBytes) {
                        emit(upper,sourceIndex,source,chunk,lastKey,chunkBytes,sequence,totalRows,consumer);
                        chunk=new ArrayList<>(chunkSize);chunkBytes=0;
                    }
                }
                if(!chunk.isEmpty())emit(upper,sourceIndex,source,chunk,lastKey,chunkBytes,sequence,totalRows,consumer);
            }
            return null;
        });
    }

    private static void emit(LocalDateTime upper,int sourceIndex,SyncDefinition.Source source,List<Map<String,Object>> rows,
                             Object lastKey,long bytes,int[] sequence,long[] totalRows,Consumer<StreamChunk> consumer) {
        totalRows[0]+=rows.size();
        consumer.accept(new StreamChunk(upper,sourceIndex,source.object(),source.sourceObject(),List.copyOf(rows),lastKey,
                bytes,false,++sequence[0]));
    }

    static int jdbcFetchSize(Connection connection,int requested)throws SQLException {
        String driver=Objects.toString(connection.getMetaData().getDriverName(),"").toLowerCase(Locale.ROOT);
        String url=Objects.toString(connection.getMetaData().getURL(),"").toLowerCase(Locale.ROOT);
        // MySQL only honours positive cursor fetch sizes when useCursorFetch=true. Fall back to its documented
        // streaming sentinel so large result sets are never materialized in the application heap.
        if(driver.contains("mysql")&&!url.contains("usecursorfetch=true"))return Integer.MIN_VALUE;
        return requested;
    }

    private static Object normalize(Object value) {
        if(value instanceof byte[] bytes)return bytes.length==1?(int)bytes[0]:Base64.getEncoder().encodeToString(bytes);
        if(value instanceof Timestamp timestamp)return timestamp.toLocalDateTime().toString();
        if(value instanceof java.sql.Date date)return date.toString();
        if(value instanceof java.sql.Time time)return time.toString();
        return value;
    }

    private static long estimateBytes(Map<String,Object> row) {
        long bytes=16;
        for(var entry:row.entrySet()) {
            bytes+=entry.getKey().getBytes(StandardCharsets.UTF_8).length+4;
            Object value=entry.getValue();
            if(value instanceof byte[] array)bytes+=array.length;
            else if(value!=null)bytes+=value.toString().getBytes(StandardCharsets.UTF_8).length;
        }
        return bytes;
    }

    private static void startSnapshot(Connection connection)throws SQLException {
        String product=Objects.toString(connection.getMetaData().getDatabaseProductName(),"").toLowerCase(Locale.ROOT);
        if(product.contains("mysql")) {
            try(Statement statement=connection.createStatement()) {
                statement.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY");
            }
        }
    }
    private static LocalDateTime currentTime(JdbcTemplate jdbc) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)",(rs,rowNum)->rs.getTimestamp(1).toLocalDateTime()));
    }

    private static void verifyTransactionalTables(Connection connection,String sql,Set<String> visited)throws SQLException {
        List<String> tables;
        try { tables=new TablesNamesFinder().getTableList(CCJSqlParserUtil.parse(sql)); }
        catch(Exception ex) { throw new IllegalArgumentException("无法确认来源表范围"); }
        String product=Objects.toString(connection.getMetaData().getDatabaseProductName(),"").toLowerCase(Locale.ROOT);
        if(!product.contains("mysql"))return;
        for(String raw:tables) {
            String table=raw.replace("`","");
            String schema=connection.getCatalog();
            if(table.contains(".")) {
                String[] parts=table.split("\\.");
                if(parts.length!=2||!parts[0].equals(schema))throw new IllegalArgumentException("SQL 不允许跨来源数据库");
                table=parts[1];
            }
            if(!visited.add(table))continue;
            try(PreparedStatement p=connection.prepareStatement(
                    "SELECT TABLE_TYPE, ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
                p.setString(1,schema);p.setString(2,table);
                try(ResultSet rs=p.executeQuery()) {
                    if(!rs.next())throw new IllegalArgumentException("无法读取来源表元数据");
                    if("VIEW".equals(rs.getString(1))) {
                        try(PreparedStatement v=connection.prepareStatement(
                                "SELECT VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
                            v.setString(1,schema);v.setString(2,table);
                            try(ResultSet vr=v.executeQuery()) {
                                if(!vr.next()||vr.getString(1)==null)throw new IllegalArgumentException("无法验证来源视图");
                                verifyTransactionalTables(connection,vr.getString(1),visited);
                            }
                        }
                    } else if(!"InnoDB".equalsIgnoreCase(rs.getString(2)))
                        throw new IllegalArgumentException("来源表不支持一致性快照");
                }
            }
        }
    }
}
