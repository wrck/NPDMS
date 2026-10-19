package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryDataMigrationApi.*;
import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryTypeMigrationApi.Type;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectDictionaryDataMapper;
import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DirectDictionaryDataPlanTest {
    final DirectDictionaryDataMapper mapper=mock(DirectDictionaryDataMapper.class);
    final PermissionApi permissions=mock(PermissionApi.class);final DataSource source=mock(DataSource.class);
    final DirectDictionaryData service=new DirectDictionaryData(mapper,permissions,source,List.of());
    final LocalDateTime time=LocalDateTime.parse("2015-01-02T03:04:05");
    @BeforeEach void setup() throws Exception{
        TenantContextHolder.setTenantId(1L);org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);var connection=mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
        when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(true);
        when(permissions.hasAnyPermissions(7L,"system:dict:create")).thenReturn(true);
        when(mapper.selectTypesForUpdate(any())).thenAnswer(c->{DirectDictionaryDataMapper.TypeQuery q=c.getArgument(0);
            return List.of(new Type(20L,"类型",q.type(),0,null,null,time,null,time,false,null));});
        when(mapper.selectConflictingValuesForUpdate(any())).thenReturn(List.of());when(mapper.insertHistorical(any())).thenReturn(1);
    }
    @AfterEach void clear(){TenantContextHolder.clear();org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
    Item incoming(){return new Item(null,0,"快递","1","01",0,null,null,null,null,time,null,time,false);}
    Request request(Entry... rows){return new Request(1L,7L,"integration:1:12",List.of(rows));}
    @Test void previewPreservesNullAuditorsAndWritesNothing(){
        var result=service.preview(request(new Entry("30",null,null,incoming()))).getFirst();
        assertEquals("CREATED",result.action());assertNull(result.after().id());assertNull(result.after().creator());assertNull(result.after().updater());
        verify(mapper,never()).insertHistorical(any());
    }
    @Test void unchangedReplayCannotRewriteOriginalAudit(){
        var first=service.apply(request(new Entry("30",null,null,incoming()))).getFirst().after();
        when(mapper.selectConflictingValuesForUpdate(any())).thenReturn(List.of(first));
        assertEquals("UNCHANGED",service.apply(request(new Entry("30",first.id(),first,incoming()))).getFirst().action());
        verify(mapper,times(1)).insertHistorical(any());
        var changed=new Item(first.id(),first.sort(),first.label(),first.value(),first.dictType(),first.status(),null,null,"人工修改",
                first.creator(),first.createTime(),first.updater(),first.updateTime(),false);
        when(mapper.selectConflictingValuesForUpdate(any())).thenReturn(List.of(changed));
        assertThrows(IllegalArgumentException.class,()->service.apply(request(new Entry("30",first.id(),first,incoming()))));
        verify(mapper,times(1)).insertHistorical(any());
    }
    @Test void wholeBatchIsPreflightedBeforeFirstInsertAndExistingValuesAreNotAdopted(){
        var second=new Item(null,2,"第二项","2","01",0,null,null,null,null,time,null,time,false);
        when(mapper.selectConflictingValuesForUpdate(argThat(q->q!=null && "2".equals(q.value())))).thenReturn(List.of(
                new Item(40L,2,"第二项","2","01",0,null,null,null,null,time,null,time,false)));
        assertThrows(IllegalArgumentException.class,()->service.apply(request(new Entry("30",null,null,incoming()),new Entry("31",null,null,second))));
        verify(mapper,never()).insertHistorical(any());
    }
    @Test void missingDisabledOrNonExactTypeBlocksMigration(){
        doReturn(List.of()).when(mapper).selectTypesForUpdate(any());
        assertThrows(IllegalArgumentException.class,()->service.apply(request(new Entry("30",null,null,incoming()))));
        doReturn(List.of(new Type(20L,"类型","01",1,null,null,time,null,time,false,null))).when(mapper).selectTypesForUpdate(any());
        assertThrows(IllegalArgumentException.class,()->service.apply(request(new Entry("30",null,null,incoming()))));
        verify(mapper,never()).insertHistorical(any());
    }
    @Test void tenantPermissionDuplicatesExcludedRecordAndIsolationRemainMandatory() throws Exception{
        var entry=new Entry("30",null,null,incoming());
        assertThrows(IllegalArgumentException.class,()->service.preview(new Request(2L,7L,"integration:2:12",List.of(entry))));
        assertThrows(IllegalArgumentException.class,()->service.preview(request(entry,entry)));
        assertThrows(IllegalArgumentException.class,()->service.preview(request(new Entry("114",null,null,incoming()))));
        when(permissions.hasAnyPermissions(7L,"system:dict:create")).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->service.preview(request(entry)));
        when(permissions.hasAnyPermissions(7L,"system:dict:create")).thenReturn(true);
        when(source.getConnection().getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        assertThrows(IllegalArgumentException.class,()->service.apply(request(entry)));
        verify(mapper,never()).insertHistorical(any());
    }
}
