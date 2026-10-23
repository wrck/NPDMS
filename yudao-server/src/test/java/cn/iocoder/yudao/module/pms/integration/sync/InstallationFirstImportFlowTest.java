package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import cn.iocoder.yudao.module.pms.engineering.api.installation.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.*;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.service.installation.InstallationHistoryInsertService;
import cn.iocoder.yudao.module.pms.asset.api.device.*;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.asset.api.location.*;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Offline source reader -> cross-chunk grouping -> INT adapter -> real IMP carry rules. */
class InstallationFirstImportFlowTest {
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    @Test void indexedSourceWithOutOfOrderDatesProducesOneCurrentAndReplayNeverReinserts() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var records=mock(InstallationMapper.class);var inserts=mock(InstallationHistoryInsertMapper.class);
        var devices=mock(ProjectDeviceSelectionApi.class);var locations=mock(AssetLocationApi.class);
        var codes=mock(EngineeringRecordCodeGenerator.class);var users=mock(SystemUserMigrationApi.class);
        var permissions=mock(PermissionApi.class);var admin=mock(AdminUserApi.class);
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);
        when(users.getHistoricalIdentitiesByUsername(anyLong(),anySet())).thenReturn(List.of());
        when(devices.validateSelection(any(ProjectDeviceSelectionApi.AuthorizedSelection.class))).thenReturn(List.of(new SelectedProjectDevice(50L,"SN",null,null,null,null)));
        var persisted=new ArrayList<InstallationDO>();
        when(records.selectCurrentRecordForUpdate(any())).thenAnswer(c->persisted.stream().filter(r->r.getEffectiveTo()==null).findFirst().orElse(null));
        when(inserts.insertHistorical(any())).thenAnswer(c->{persisted.add(c.getArgument(0));return 1;});
        when(inserts.selectBoundRecordForUpdate(any())).thenAnswer(c->{var q=(InstallationHistoryInsertMapper.BoundRecord)c.getArgument(0);return persisted.stream().filter(r->r.getId().equals(q.installationId())).findFirst().orElse(null);});
        when(codes.next(eq(10L),eq("AZ"),eq(records),any(String[].class))).thenAnswer(c->"P1-AZ-"+((String[])c.getRawArguments()[3]).length);
        when(locations.maintain(any())).thenReturn(new LocationReferenceDTO("RESOLVED",80L,0L,null,null,null,null,"原地址"));
        when(locations.getAddress(80L,0L)).thenReturn(new AddressRespDTO(80L,null,null,null,null,null,null,null,null,null,"原地址",null,null,0,0L));
        var owner=new InstallationHistoryInsertService(records,inserts,devices,locations,codes,users,permissions,admin);
        var definition=DppmsInstallationSyncTemplate.create(18L).toBuilder().chunkSize(1).build();
        var reference=new DppmsProjectBindingApi.ProjectBinding(18L,"101",10L,"10","P1","P1");
        definition.sources().getFirst().publishedProjects(List.of(reference));
        var context=mock(SyncOwnerRunContext.class);var scope=mock(DppmsPublishedProjectScopeApi.class);var projects=mock(ProjectMasterMigrationApi.class);var bindings=mock(SyncBindingMapper.class);
        when(context.require(any())).thenReturn(new SyncOwnerRunContext.Context(1L,2L,new SyncTaskDO().setId(12L).setConnectionId(18L),new SyncRunDO().setId(13L),definition));
        when(scope.resolve(any())).thenReturn(new DppmsPublishedProjectScopeApi.Scope(20L,List.of(reference)));
        when(projects.inspectBindings(any())).thenReturn(List.of(JsonUtils.parseObject("{\"id\":10,\"tenantId\":1,\"version\":4,\"projectCode\":\"P1\",\"projectType\":\"10\"}",ProjectMasterMigrationApi.Facts.class)));
        when(bindings.selectIdentitySources(any())).thenReturn(List.of(new SyncBindingDO().setSourceKey("SN").setTargetId(50L)));
        var adapter=new DppmsInstallationSyncAdapter(context,scope,projects,bindings,owner);
        String url="jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE";
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
            var columns=new ArrayList<String>();columns.add("id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY");
            for(String field:DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS)if(!field.equals("id"))columns.add(field+(Set.of("createTime","updateTime","packdate").contains(field)?" TIMESTAMP":" VARCHAR(255)"));
            statement.execute("CREATE TABLE pm_project_shipment("+String.join(",",columns)+")");
            for(int day:List.of(1,5,2))statement.execute("INSERT INTO pm_project_shipment(projectId,barcode,installAddress,transferFlag,createTime) VALUES('101','SN','原地址','-1','2020-01-0"+day+"T00:00:00')");
        }
        var api=new ExternalDataSourceApi() {
            public List<Info> list(){return List.of();}public Long save(Save c){throw new UnsupportedOperationException();}
            public Connection openReadOnly(Long id)throws SQLException {var connection=DriverManager.getConnection(url,"sa","");connection.setReadOnly(true);return connection;}
        };
        var chunks=new ArrayList<SpringJdbcStreamingReader.StreamChunk>();
        new SpringJdbcStreamingReader(api).stream(definition,null,true,null,chunks::add);
        assertEquals(List.of(1L,2L,3L),chunks.stream().flatMap(c->c.rows().stream()).map(r->((Number)r.get("id")).longValue()).toList());
        var grouped=new ArrayList<SpringJdbcStreamingReader.StreamChunk>();
        try(var stream=new DppmsInstallationStream(definition)){chunks.forEach(stream::capture);stream.replay(grouped::add);}
        assertEquals(2,grouped.size());assertEquals(3,grouped.getFirst().rows().size());
        var rows=grouped.getFirst().rows().stream().map(r->new Row(DppmsInstallationSyncAdapter.OBJECT,r.get("id").toString(),r,null)).toList();
        var batch=new Batch("integration:1:12",rows,List.of(),false,"RETAIN",false,"UPSERT",false);
        assertEquals(3,adapter.preview(batch).size());assertTrue(persisted.isEmpty());verifyNoInteractions(locations);
        var changes=adapter.apply(batch);assertEquals(3,persisted.size());
        var latest=LocalDateTime.of(2020,1,5,0,0);
        assertEquals(latest,persisted.getFirst().getEffectiveFrom());assertNull(persisted.getFirst().getEffectiveTo());
        assertEquals(2,persisted.stream().filter(r->latest.equals(r.getEffectiveTo())).count());
        verify(locations,times(1)).effectEquipmentLocationIfAbsent(any());
        var prior=changes.stream().map(c->new Binding(c.object(),c.sourceKey(),c.targetId(),JsonUtils.parseMap(JsonUtils.toJsonString(c.after())))).toList();
        var replayRows=rows.stream().map(r->new Row(r.object(),r.sourceKey(),r.fields(),prior.stream().filter(b->b.sourceKey().equals(r.sourceKey())).findFirst().orElseThrow().targetId())).toList();
        clearInvocations(inserts,locations);
        assertTrue(adapter.apply(new Batch(batch.owner(),replayRows,prior,false,"RETAIN",false,"UPSERT",false)).stream().allMatch(c->c.action().equals("UNCHANGED")));
        verify(inserts,never()).insertHistorical(any());verifyNoInteractions(locations);
        persisted.getFirst().setDeleted(true);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(new Batch(batch.owner(),replayRows,prior,false,"RETAIN",false,"UPSERT",false)));
    }
}
