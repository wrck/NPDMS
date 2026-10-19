package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MysqlCacheNativeKeyTest {
    record Read(MysqlSyncReader.Snapshot snapshot,String sourceSql) {}
    Read read(String taskKey,int sourceIndex,List<Map<String,Object>> values) throws Exception {
        var definition=DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request(taskKey,1L));
        var source=definition.sources().get(sourceIndex);definition=definition.toBuilder().sources(List.of(source)).maxRows(1).build();
        var connection=mock(Connection.class);var statement=mock(Statement.class);var now=mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);when(connection.getCatalog()).thenReturn("source");
        when(statement.executeQuery("SELECT CURRENT_TIMESTAMP(6)")).thenReturn(now);when(now.next()).thenReturn(true,false);
        when(now.getTimestamp(1)).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026,10,16,0,0)));
        var tableStatement=mock(PreparedStatement.class);var table=mock(ResultSet.class);
        when(tableStatement.executeQuery()).thenReturn(table);when(table.next()).thenReturn(true,false);
        when(table.getString(1)).thenReturn("BASE TABLE");when(table.getString(2)).thenReturn("InnoDB");
        var query=mock(PreparedStatement.class);var rows=mock(ResultSet.class);var columns=mock(ResultSetMetaData.class);
        when(query.executeQuery()).thenReturn(rows);when(rows.getMetaData()).thenReturn(columns);when(columns.getColumnCount()).thenReturn(source.columns().size());
        for(int i=0;i<source.columns().size();i++)when(columns.getColumnLabel(i+1)).thenReturn(source.columns().get(i));
        var index=new AtomicInteger(-1);when(rows.next()).thenAnswer(call->index.incrementAndGet()<values.size());
        when(rows.getObject(anyInt())).thenAnswer(call->values.get(index.get()).get(source.columns().get((Integer)call.getArgument(0)-1)));
        List<String> captured=new ArrayList<>();
        when(connection.prepareStatement(anyString())).thenAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.contains("information_schema.TABLES"))return tableStatement;
            captured.add(sql);return query;
        });
        var api=mock(ExternalDataSourceApi.class);when(api.openReadOnly(1L)).thenReturn(connection);
        var result=new MysqlSyncReader(api).read(definition,null,true);
        verify(connection).rollback();verify(query,never()).setMaxRows(anyInt());
        return new Read(result,captured.getFirst());
    }
    @Test void nativePlanIdsAreNotReplacedByOrdinalsAndWholeSnapshotIsNotTruncated() throws Exception {
        var result=read("P23",0,List.of(Map.of("id",101L,"contractNo","C-01"),Map.of("id",305L,"contractNo","C-01")));
        var rows=result.snapshot().objects().getFirst().rows();assertEquals(List.of(101L,305L),rows.stream().map(row->row.get("id")).toList());
        assertEquals(2,rows.size());assertTrue(rows.stream().allMatch(row->row.containsKey("id") && !row.containsKey("cacheRowOrdinal")));
        assertTrue(result.sourceSql().endsWith("ORDER BY `id`"));
    }
    @Test void unkeyedProjectCacheKeepsDuplicateRowsWithSnapshotLocalOrdinals() throws Exception {
        var result=read("P21",0,List.of(Map.of("projectCode","P001"),Map.of("projectCode","P001")));
        var rows=result.snapshot().objects().getFirst().rows();assertEquals(2,rows.size());
        assertEquals(List.of("1","2"),rows.stream().map(row->row.get("cacheRowOrdinal")).toList());
        assertFalse(result.sourceSql().contains("ORDER BY"));
    }
}
