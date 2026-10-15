package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceBootstrapApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsLocalContractSourceApi;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SyncLocalOwnerReaderTest {
    final DppmsLocalContractSourceApi contracts=mock(DppmsLocalContractSourceApi.class);
    final DppmsDeviceBootstrapApi devices=mock(DppmsDeviceBootstrapApi.class);
    final ExternalDataSourceApi remote=mock(ExternalDataSourceApi.class);
    final SyncLocalOwnerReader owner=new SyncLocalOwnerReader(contracts,devices);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void close(){TenantContextHolder.clear();}
    SyncDefinition definition() {
        var d=DppmsDeviceBootstrapSyncTemplate.create(9L);d.executionActorId(2L);return d;
    }
    SpringJdbcStreamingReader reader() {
        var reader=new SpringJdbcStreamingReader(remote);
        @SuppressWarnings("unchecked") ObjectProvider<SyncLocalOwnerReader> provider=mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(owner);ReflectionTestUtils.setField(reader,"localOwners",provider);return reader;
    }
    @Test void localSnapshotCarriesNullFieldsAndActorWithoutOpeningRemoteConnection() throws Exception {
        when(devices.snapshot(any())).thenReturn(List.of(new DppmsDeviceBootstrapApi.Seed("SN",10L,"S10",null,null,null,null,null,null)));
        var rows=new ArrayList<Map<String,Object>>();var d=definition();
        var result=reader().stream(d,null,true,null,c->{if(!c.sourceComplete())rows.addAll(c.rows());});
        assertEquals(1,result.rows());assertEquals(1,rows.size());
        assertTrue(rows.getFirst().containsKey("contractNo"));assertNull(rows.getFirst().get("contractNo"));
        verify(devices).snapshot(new DppmsDeviceBootstrapApi.SnapshotQuery(1L,2L,List.of(),d.maxBytes()));
        verifyNoInteractions(remote,contracts);
    }
    @Test void localSourceRejectsInjectedSqlBeforeReadingOwner() {
        var d=definition();d.sources().getFirst().sql("SELECT * FROM ast_device");
        assertThrows(IllegalArgumentException.class,()->reader().stream(d,null,true,null,c->{}));
        verifyNoInteractions(devices,remote,contracts);
    }
    @Test void mixedLocalSourceCannotRunIncrementally() {
        var d=definition();var sources=new ArrayList<>(d.sources());
        sources.add(SyncDefinition.Source.builder().object("OTHER").readMode("SQL").sql("SELECT 1 AS id").sourceKey("id").build());d.sources(sources);
        assertThrows(IllegalArgumentException.class,()->reader().stream(d,null,false,null,c->{}));
        verifyNoInteractions(devices,remote,contracts);
    }
    @Test void contractIdentityUsesBothCompanyAndContractAndRetainsConflictEvidence() {
        var source=SyncDefinition.Source.builder().object("CONTRACT").sourceObject("com_sales_order")
                .readMode("LOCAL_OWNER").sourceKey("migrationKey").parameters(Map.of("contractNos","C1")).build();
        var d=definition();d.adapter("TABLE_MAPPING");d.taskKey("delivery-full-sync");d.sources(List.of(source));
        when(contracts.read(any())).thenReturn(List.of(new DppmsLocalContractSourceApi.Contract("A","C1",null,null,"CONTRACT_CUSTOMER_CONFLICT"),
                new DppmsLocalContractSourceApi.Contract("B","C1","C00001","Customer",null)));
        var rows=owner.read(new SyncLocalOwnerReader.Input(d,source));
        assertNotEquals(rows.getFirst().get("migrationKey"),rows.getLast().get("migrationKey"));
        assertEquals("CONTRACT_CUSTOMER_CONFLICT",rows.getFirst().get("migration_issue"));
        assertTrue(rows.getLast().containsKey("migration_issue"));assertNull(rows.getLast().get("migration_issue"));
        verify(contracts).read(new DppmsLocalContractSourceApi.Query(1L,2L,List.of("C1"),d.maxBytes()));
    }
}
