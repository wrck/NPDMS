package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.installation.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsInstallationSyncAdapterTest {
    final SyncOwnerRunContext context=mock(SyncOwnerRunContext.class);
    final DppmsPublishedProjectScopeApi published=mock(DppmsPublishedProjectScopeApi.class);
    final ProjectMasterMigrationApi projects=mock(ProjectMasterMigrationApi.class);
    final SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    final InstallationHistoryInsertApi owner=mock(InstallationHistoryInsertApi.class);
    final DppmsInstallationSyncAdapter adapter=new DppmsInstallationSyncAdapter(context,published,projects,bindings,owner);
    @BeforeEach void setup() {
        var definition=DppmsInstallationSyncTemplate.create(18L);
        var task=new SyncTaskDO().setId(12L).setConnectionId(18L);
        when(context.require(any())).thenReturn(new SyncOwnerRunContext.Context(1L,2L,task,new SyncRunDO().setId(13L),definition));
        when(published.resolve(any())).thenReturn(new DppmsPublishedProjectScopeApi.Scope(20L,List.of(new DppmsProjectBindingApi.ProjectBinding(18L,"101",10L,"10","P1","P1"))));
        when(projects.inspectBindings(any())).thenReturn(List.of(JsonUtils.parseObject("{\"id\":10,\"tenantId\":1,\"version\":4,\"projectCode\":\"P1\",\"projectType\":\"10\"}",ProjectMasterMigrationApi.Facts.class)));
        when(bindings.selectIdentitySources(any())).thenReturn(List.of(new SyncBindingDO().setSourceKey("SN").setTargetId(50L)));
        when(owner.insert(any())).thenAnswer(call->{var command=(InstallationHistoryInsertApi.Command)call.getArgument(0);return command.rows().stream()
                .map(r->new InstallationHistoryInsertApi.Result(r.sourceKey(),80L,"P1-AZ-001",null,"baseline")).toList();});
        when(owner.preview(any())).thenReturn(List.of(new InstallationHistoryInsertApi.Result("301",-1L,"P1-AZ-001",null,"preview")));
        when(owner.verify(any())).thenReturn(List.of(new InstallationHistoryInsertApi.Result("301",80L,"P1-AZ-001",null,"baseline")));
    }
    Row row(Long target) {
        var raw=new LinkedHashMap<String,Object>();
        DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS.forEach(n->raw.put(n,null));
        raw.put("id",301);raw.put("projectId",101);raw.put("barcode","SN");raw.put("projectType","10");raw.put("originalProjectCode","P1");
        raw.put("installAddress","原地址");raw.put("transferFlag","-1");raw.put("createTime","2020-01-01T00:00:00");
        return new Row(DppmsInstallationSyncAdapter.OBJECT,"301",raw,target);
    }
    Batch batch(Row row,List<Binding> prior){return new Batch("integration:1:12",List.of(row),prior,false,"RETAIN",false,"UPSERT",false);}
    @Test void fullRawFieldsAndFormalIdentitiesReachOwnerAndReplayOnlyVerifies() {
        var result=adapter.apply(batch(row(null),List.of())).getFirst();
        verify(owner).insert(argThat(c->c.tenantId()==1L&&c.actorId()==2L&&c.projectId()==10L&&c.rows().getFirst().equipmentId()==50L
                &&c.rows().getFirst().originalFields().size()==21&&c.rows().getFirst().originalFields().containsKey("updateBy")));
        verify(projects).lockBindings(any());assertEquals(80L,result.targetId());
        clearInvocations(owner);
        var prior=new Binding(DppmsInstallationSyncAdapter.OBJECT,"301",80L,JsonUtils.parseMap(JsonUtils.toJsonString(result.after())));
        assertEquals("UNCHANGED",adapter.apply(batch(row(80L),List.of(prior))).getFirst().action());
        verify(owner).verify(any());verify(owner,never()).insert(any());
        clearInvocations(owner);var changed=row(80L);changed.fields().put("installAddress","changed");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(changed,List.of(prior))));verifyNoInteractions(owner);
    }
    @Test void previewDoesNotExposeSyntheticTargetAsRealBindingAndMissingScopeCannotWrite() {
        assertNull(adapter.preview(batch(row(null),List.of())).getFirst().targetId());verify(owner,never()).insert(any());
        clearInvocations(owner);when(published.resolve(any())).thenReturn(new DppmsPublishedProjectScopeApi.Scope(20L,List.of()));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(null),List.of())));verifyNoInteractions(owner);
    }
    @Test void templateUsesOnlyRawPrimaryKeyAndRequiresCompletePublishedScope() {
        var definition=DppmsInstallationSyncTemplate.create(18L);new SyncDefinitionValidator(List.of(adapter)).validate(definition);
        var source=definition.sources().getFirst();assertEquals("id",source.sourceKey());assertEquals("TABLE",source.readMode());assertNull(source.updatedAt());
        source.sourceKey("createTime");assertThrows(IllegalArgumentException.class,()->new SyncDefinitionValidator(List.of(adapter)).validate(definition));
    }
}
