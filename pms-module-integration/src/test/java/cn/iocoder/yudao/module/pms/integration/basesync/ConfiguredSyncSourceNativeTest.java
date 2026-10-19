package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import cn.iocoder.yudao.module.pms.integration.basesync.task.P24ReceivableSyncTask.SourceMapper;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncConnectionDO;
import cn.iocoder.yudao.module.pms.integration.sync.SyncConnectionService;
import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfiguredSyncSourceNativeTest {
    @Test void mysqlUsesNativeForwardOnlyStreamingWithoutChangingSavedConnection() throws Exception {
        verifyDriverRead("MySQL",Integer.MIN_VALUE);
    }
    @Test void sqlServerUsesForwardOnlyFetchAndTheExistingReadOnlyConnection() throws Exception {
        verifyDriverRead("Microsoft SQL Server",500);
    }
    private void verifyDriverRead(String product,int fetchSize) throws Exception {
        var references=mock(SyncConnectionService.class);var api=mock(ExternalDataSourceApi.class);
        var ref=new SyncConnectionDO();ref.setDataSourceId(7L);when(references.required(3L)).thenReturn(ref);
        var connection=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);
        when(api.openReadOnly(7L)).thenReturn(connection);when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn(product);
        var statement=mock(PreparedStatement.class);var results=mock(ResultSet.class);var columns=mock(ResultSetMetaData.class);
        when(connection.prepareStatement(anyString(),eq(ResultSet.TYPE_FORWARD_ONLY),eq(ResultSet.CONCUR_READ_ONLY))).thenReturn(statement);
        when(statement.execute()).thenReturn(true);when(statement.getResultSet()).thenReturn(results);when(statement.getUpdateCount()).thenReturn(-1);
        when(results.getMetaData()).thenReturn(columns);when(results.next()).thenReturn(false);
        var source=new ConfiguredSyncSource(references,api);
        try(var session=source.open(3)){
            session.mapper(SourceMapper.class).selectReceivable(new cn.iocoder.yudao.module.pms.integration.api.basesync.AbstractSyncTask.SourceQuery<>(10,1,true,null),
                    result->fail("empty JDBC result should not invoke a row callback"));
        }
        verify(statement).setFetchSize(fetchSize);
        verify(statement).setQueryTimeout(60);
        verify(statement).close();verify(results).close();verify(connection).close();
        verify(references).required(3L);verify(api).openReadOnly(7L);
        verifyNoMoreInteractions(references,api);
    }
    @Test void connectionClosesWhenDriverMetadataFailsAndDetailsAreNotExposed() throws Exception {
        var references=mock(SyncConnectionService.class);var api=mock(ExternalDataSourceApi.class);
        var ref=new SyncConnectionDO();ref.setDataSourceId(7L);when(references.required(3L)).thenReturn(ref);
        var connection=mock(Connection.class);when(api.openReadOnly(7L)).thenReturn(connection);
        when(connection.getMetaData()).thenThrow(new SQLException("fixture connection details"));
        var source=new ConfiguredSyncSource(references,api);
        assertEquals("无法打开已配置的同步连接",assertThrows(IllegalStateException.class,()->source.open(3)).getMessage());
        verify(connection).close();
    }
}
