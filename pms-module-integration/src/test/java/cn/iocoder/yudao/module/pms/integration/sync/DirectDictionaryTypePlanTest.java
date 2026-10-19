package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryTypeMigrationApi.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectDictionaryTypeMapper;
import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DirectDictionaryTypePlanTest {
    final DirectDictionaryTypeMapper mapper=mock(DirectDictionaryTypeMapper.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final DataSource dataSource=mock(DataSource.class);
    final DirectDictionaryTypes service=new DirectDictionaryTypes(mapper,permissions,dataSource);
    final LocalDateTime time=LocalDateTime.parse("2015-01-02T03:04:05");
    @BeforeEach void setup() throws Exception{
        TenantContextHolder.setTenantId(1L);org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);var connection=mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
        when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(true);
        when(permissions.hasAnyPermissions(7L,"system:dict:create")).thenReturn(true);
        when(mapper.selectConflictingDefinitionsForUpdate(any())).thenReturn(List.of());when(mapper.insertHistorical(any())).thenReturn(1);
    }
    @AfterEach void clear(){TenantContextHolder.clear();org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
    Type incoming(){return new Type(null,"项目状态","02",0,null,null,time,null,time,false,null);}
    Request request(Entry entry){return new Request(1L,7L,"integration:1:12",List.of(entry));}
    @Test void previewKeepsNullActorsAndOriginalTimeWithoutWriting(){
        var result=service.preview(request(new Entry("02",null,null,incoming()))).getFirst();
        assertEquals("CREATED",result.action());assertNull(result.after().id());assertNull(result.after().creator());assertNull(result.after().updater());
        assertEquals(time,result.after().createTime());verify(mapper,never()).insertHistorical(any());
    }
    @Test void applyUsesExplicitFullElevenColumnRecordAndReplayDoesNotRewriteAudit(){
        var created=service.apply(request(new Entry("02",null,null,incoming()))).getFirst().after();
        assertNotNull(created.id());verify(mapper).insertHistorical(argThat(insert->insert.row().equals(created)));
        when(mapper.selectConflictingDefinitionsForUpdate(any())).thenReturn(List.of(created));
        assertEquals("UNCHANGED",service.apply(request(new Entry("02",created.id(),created,incoming()))).getFirst().action());
        verify(mapper,times(1)).insertHistorical(any());
    }
    @Test void sameNameCodeDeletedOrManuallyChangedDefinitionCannotBeTakenOver(){
        var actual=new Type(40L,"项目状态","02",0,null,null,time,null,time,false,null);
        when(mapper.selectConflictingDefinitionsForUpdate(any())).thenReturn(List.of(actual));
        assertThrows(IllegalArgumentException.class,()->service.apply(request(new Entry("02",null,null,incoming()))));
        var changed=new Type(40L,actual.name(),actual.type(),1,null,null,time,"9",time,false,null);
        when(mapper.selectConflictingDefinitionsForUpdate(any())).thenReturn(List.of(changed));
        assertThrows(IllegalArgumentException.class,()->service.apply(request(new Entry("02",40L,actual,incoming()))));
        verify(mapper,never()).insertHistorical(any());
    }
    @Test void invalidBatchTenantPermissionAndDatabaseIsolationCannotWrite() throws Exception{
        var entry=new Entry("02",null,null,incoming());
        assertThrows(IllegalArgumentException.class,()->service.preview(new Request(2L,7L,"integration:2:12",List.of(entry))));
        assertThrows(IllegalArgumentException.class,()->service.preview(new Request(1L,7L,"integration:1:12",List.of(entry,entry))));
        when(permissions.hasAnyPermissions(7L,"system:dict:create")).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->service.preview(request(entry)));
        when(permissions.hasAnyPermissions(7L,"system:dict:create")).thenReturn(true);
        when(dataSource.getConnection().getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        assertThrows(IllegalArgumentException.class,()->service.apply(request(entry)));
        verify(mapper,never()).insertHistorical(any());
    }
}
