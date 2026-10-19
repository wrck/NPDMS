package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionHolder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.*;
import java.io.StringReader;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Independent reference-style subclass; no business task, full source list or custom row-handler engine. */
class AbstractSyncTaskStreamingTest {
    public static class Row {
        // 10,000 live rows would require >625 MiB; this suite runs with -Xmx256m.
        private final byte[] payload=new byte[64*1024];
        private long id;
        public long getId(){return id;}
        public void setId(long id){this.id=id;payload[0]=(byte)id;}
    }
    static class Task extends AbstractSyncTask<Row,Void> {
        record References(long first,long last) {}
        int prepared,finished,maxBatch;
        long failAt;
        List<Row> lastBatch;
        public String taskCode(){return "Rows";}
        public String taskName(){return "reference streaming";}
        protected void syncDataInsertBefore(SyncBatch<Row,Void> batch){
            lastBatch=batch.rows();prepared++;maxBatch=Math.max(maxBatch,batch.rows().size());
            var query=new References(batch.rows().getFirst().id,batch.rows().getLast().id);
            List<Long> ids=batch.context().target().selectList(targetMapperNamespace()+".references",query);
            assertEquals(batch.rows().size(),ids.size());
            for(var row:batch.rows()){
                assertEquals((byte)row.id,row.payload[0]);
                if(row.id==failAt)throw new IllegalArgumentException("last batch rejected");
            }
        }
        protected void syncDataInsertAfter(SyncBatch<Row,Void> batch){finished++;}
    }
    static class Fixture implements AutoCloseable {
        final int total=10_000;
        final Task task=new Task();
        final AtomicInteger consumed=new AtomicInteger();
        final Connection sourceConnection=mock(Connection.class);
        final PreparedStatement statement=mock(PreparedStatement.class);
        final ResultSet resultSet=mock(ResultSet.class);
        final SqlSessionFactory targetFactory;
        final SqlSessionTemplate target;
        final JdbcTemplate jdbc;
        final TransactionTemplate transaction;
        final SyncSource source;
        final int batchSize;
        final SyncContext<Void> context;
        Fixture(int batchSize,boolean preview) throws Exception {
            this.batchSize=batchSize;
            var data=new DriverManagerDataSource("jdbc:h2:mem:stream-"+UUID.randomUUID()+";MODE=MySQL","sa","");
            // Keep this isolated test database alive until close().
            keeper=data.getConnection();
            jdbc=new JdbcTemplate(data);
            jdbc.execute("CREATE TABLE target_row(id BIGINT PRIMARY KEY)");
            jdbc.update("INSERT INTO target_row VALUES(-1)");
            var configuration=new Configuration();configuration.setCacheEnabled(false);
            configuration.setEnvironment(new Environment("stream-target",new SpringManagedTransactionFactory(),data));
            parse(configuration,"""
                <mapper namespace="cn.iocoder.yudao.module.pms.integration.basesync.AbstractSyncTaskStreamingTest$Task$TargetMapper">
                  <select id="countRows" resultType="long">SELECT COUNT(*) FROM target_row</select>
                  <delete id="deleteRows">DELETE FROM target_row</delete>
                  <select id="references" resultType="long">
                    SELECT X FROM SYSTEM_RANGE(#{first},#{last}) ORDER BY X
                  </select>
                  <insert id="insertRows">INSERT INTO target_row VALUES
                    <foreach collection="rows" item="row" separator=",">(#{row.id})</foreach>
                  </insert>
                </mapper>
                """);
            targetFactory=new SqlSessionFactoryBuilder().build(configuration);
            target=new SqlSessionTemplate(targetFactory);
            transaction=new TransactionTemplate(new DataSourceTransactionManager(data));
            var sourceConfig=new Configuration();sourceConfig.setCacheEnabled(false);
            sourceConfig.setDefaultResultSetType(org.apache.ibatis.mapping.ResultSetType.FORWARD_ONLY);
            sourceConfig.setDefaultFetchSize(Integer.MIN_VALUE);
            parse(sourceConfig,"""
                <mapper namespace="cn.iocoder.yudao.module.pms.integration.basesync.AbstractSyncTaskStreamingTest$Task$SourceMapper">
                  <resultMap id="row" type="cn.iocoder.yudao.module.pms.integration.basesync.AbstractSyncTaskStreamingTest$Row">
                    <id column="id" property="id"/>
                  </resultMap>
                  <select id="selectRows" resultMap="row">SELECT id FROM source_row ORDER BY id</select>
                </mapper>
                """);
            var columns=mock(ResultSetMetaData.class);
            when(columns.getColumnCount()).thenReturn(1);
            when(columns.getColumnLabel(1)).thenReturn("id");
            when(columns.getColumnType(1)).thenReturn(Types.BIGINT);
            when(columns.getColumnClassName(1)).thenReturn(Long.class.getName());
            when(resultSet.getMetaData()).thenReturn(columns);
            when(sourceConnection.prepareStatement(anyString(),eq(ResultSet.TYPE_FORWARD_ONLY),eq(ResultSet.CONCUR_READ_ONLY))).thenReturn(statement);
            when(statement.execute()).thenReturn(true);
            when(statement.getResultSet()).thenReturn(resultSet);
            when(statement.getUpdateCount()).thenReturn(-1);
            when(resultSet.getLong("id")).thenAnswer(call->(long)consumed.get());
            when(resultSet.next()).thenAnswer(call->{
                int previous=consumed.get();
                if(previous>0 && previous%batchSize==0){
                    // Assert at the JDBC next() boundary, before MyBatis maps another row.
                    assertNotNull(task.lastBatch);assertTrue(task.lastBatch.isEmpty());
                    var holder=(SqlSessionHolder)TransactionSynchronizationManager.getResource(targetFactory);
                    assertNotNull(holder);
                    var cache=(Cache)targetFactory.getConfiguration().newMetaObject(holder.getSqlSession()).getValue("executor.localCache");
                    assertEquals(0,cache.getSize(),"preview lookups must not survive a completed batch");
                    assertEquals(previous/batchSize,task.prepared,"no read-ahead past the current batch");
                }
                return consumed.incrementAndGet()<=total;
            });
            var sourceFactory=new SqlSessionFactoryBuilder().build(sourceConfig);
            source=connectionId->new SyncSource.Session(){
                final SqlSession session=sourceFactory.openSession(sourceConnection);
                public SqlSession sqlSession(){return session;}
                public <M> M mapper(Class<M> type){return session.getMapper(type);}
                public void close(){session.close();}
            };
            context=new SyncContext<>(1,2,3,preview,batchSize,total,LocalDateTime.now(),source,
                    mock(SyncIdentityStore.class),mock(SyncReferences.class),"Rows",target,true,true);
        }
        final Connection keeper;
        SyncCounts run(){return transaction.execute(status->task.execute(context));}
        public void close() throws Exception {keeper.close();}
    }
    static void parse(Configuration configuration,String xml){
        var header="<?xml version=\"1.0\"?><!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"https://mybatis.org/dtd/mybatis-3-mapper.dtd\">";
        new XMLMapperBuilder(new StringReader(header+xml),configuration,UUID.randomUUID().toString(),configuration.getSqlFragments()).parse();
    }
    @Test void tenThousandRowPreviewReleasesEachBatchAndSessionCacheIncludingTail() throws Exception {
        try(var f=new Fixture(128,true)){
            assertEquals(new SyncCounts(10_000,10_000,0,0,1),f.run());
            assertEquals(79,f.task.prepared);assertEquals(128,f.task.maxBatch);assertEquals(0,f.task.finished);
            assertTrue(f.task.lastBatch.isEmpty());
            assertEquals(List.of(-1L),f.jdbc.queryForList("SELECT id FROM target_row",Long.class));
            verify(f.resultSet).close();verify(f.statement).close();verify(f.sourceConnection).close();
        }
    }
    @Test void tenThousandRowsWriteFiveBatchesWithoutReturningOrRetainingSourceRows() throws Exception {
        try(var f=new Fixture(2000,false)){
            assertEquals(new SyncCounts(10_000,10_000,0,0,1),f.run());
            assertEquals(5,f.task.finished);assertEquals(2000,f.task.maxBatch);assertTrue(f.task.lastBatch.isEmpty());
            assertEquals(10_000,f.jdbc.queryForObject("SELECT COUNT(*) FROM target_row",Integer.class));
            verify(f.statement).setFetchSize(Integer.MIN_VALUE);verify(f.sourceConnection).close();
        }
    }
    @Test void failureInLastBatchReleasesRowsAndRollsBackClearAndAllEarlierBatches() throws Exception {
        try(var f=new Fixture(128,false)){
            f.task.failAt=9999;
            assertEquals("last batch rejected",assertThrows(IllegalArgumentException.class,f::run).getMessage());
            assertEquals(78,f.task.finished);assertTrue(f.task.lastBatch.isEmpty());
            assertEquals(List.of(-1L),f.jdbc.queryForList("SELECT id FROM target_row",Long.class));
            verify(f.resultSet).close();verify(f.statement).close();verify(f.sourceConnection).close();
        }
    }
}
