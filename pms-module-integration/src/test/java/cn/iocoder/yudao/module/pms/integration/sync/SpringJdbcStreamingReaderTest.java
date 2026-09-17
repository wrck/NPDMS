package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SpringJdbcStreamingReaderTest {
    @Test
    void streamsSingleResultSetIntoBoundedChunks() throws Exception {
        String url="jdbc:h2:mem:spring_jdbc_stream;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try(Connection connection=DriverManager.getConnection(url,"sa",""); Statement statement=connection.createStatement()) {
            statement.execute("CREATE TABLE stream_source(id BIGINT PRIMARY KEY, name VARCHAR(32))");
            for(int i=1;i<=5;i++)statement.execute("INSERT INTO stream_source VALUES("+i+", 'n"+i+"')");
        }
        var reader=new SpringJdbcStreamingReader(sourceApi(url));
        var source=SyncDefinition.Source.builder().object("ITEM").sourceObject("stream_source").readMode("SQL")
                .sql("SELECT id,name FROM stream_source ORDER BY id").parameters(Map.of()).sourceKey("id")
                .columns(List.of()).filters(List.of()).mappings(List.of()).build();
        var definition=SyncDefinition.builder().connectionId(1L).mode("ONCE").overlapSeconds(0)
                .fetchSize(2).chunkSize(2).restartPolicy("RESTART_ALL").maxRows(100).maxBytes(1024*1024)
                .sources(List.of(source)).build();
        List<Integer> chunks=new ArrayList<>();
        List<Long> ids=new ArrayList<>();
        AtomicInteger completed=new AtomicInteger();
        var result=reader.stream(definition,null,true,null,chunk->{
            if(chunk.sourceComplete()) {completed.incrementAndGet();return;}
            chunks.add(chunk.rows().size());
            chunk.rows().forEach(row->ids.add(((Number)row.get("id")).longValue()));
        });
        assertEquals(List.of(2,2,1),chunks);
        assertEquals(List.of(1L,2L,3L,4L,5L),ids);
        assertEquals(5,result.rows());
        assertEquals(1,completed.get());
    }

    @Test
    void checkpointKeyIsOnlyAppliedAsResumeBoundary() throws Exception {
        String url="jdbc:h2:mem:spring_jdbc_checkpoint;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try(Connection connection=DriverManager.getConnection(url,"sa",""); Statement statement=connection.createStatement()) {
            statement.execute("CREATE TABLE stream_source(id BIGINT PRIMARY KEY, name VARCHAR(32))");
            for(int i=1;i<=5;i++)statement.execute("INSERT INTO stream_source VALUES("+i+", 'n"+i+"')");
        }
        var reader=new SpringJdbcStreamingReader(sourceApi(url));
        var source=SyncDefinition.Source.builder().object("ITEM").sourceObject("stream_source").readMode("SQL")
                .sql("SELECT id,name FROM stream_source").parameters(Map.of()).sourceKey("id")
                .columns(List.of()).filters(List.of()).mappings(List.of()).build();
        var definition=SyncDefinition.builder().connectionId(1L).mode("ONCE").overlapSeconds(0)
                .fetchSize(10).chunkSize(10).restartPolicy("CHECKPOINT_KEY").maxRows(100).maxBytes(1024*1024)
                .sources(List.of(source)).build();
        List<Long> ids=new ArrayList<>();
        var resume=new SyncStreamingState(0,2L,2,1,null);
        reader.stream(definition,null,true,resume,chunk->{
            if(!chunk.sourceComplete())chunk.rows().forEach(row->ids.add(((Number)row.get("id")).longValue()));
        });
        assertEquals(List.of(3L,4L,5L),ids);
    }

    private static ExternalDataSourceApi sourceApi(String url) {
        return new ExternalDataSourceApi() {
            @Override public List<Info> list(){return List.of();}
            @Override public Long save(Save command){throw new UnsupportedOperationException();}
            @Override public Connection openReadOnly(Long id)throws SQLException {
                Connection connection=DriverManager.getConnection(url,"sa","");
                connection.setReadOnly(true);return connection;
            }
        };
    }
}
