package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsMemberHistorySyncTemplate.*;
class DppmsMemberHistorySyncAdapterTest {
    SyncOwnerRunContext context=mock(SyncOwnerRunContext.class);DppmsPublishedProjectScopeApi published=mock(DppmsPublishedProjectScopeApi.class);
    ProjectMasterMigrationApi projects=mock(ProjectMasterMigrationApi.class);ProjectMemberSourceHistoryApi owner=mock(ProjectMemberSourceHistoryApi.class);
    DppmsMemberHistorySyncAdapter adapter=new DppmsMemberHistorySyncAdapter(context,published,projects,owner);
    @BeforeEach void setup() {
        when(context.require(any())).thenReturn(new SyncOwnerRunContext.Context(1L,2L,new SyncTaskDO().setId(31L).setConnectionId(7L),new SyncRunDO().setId(310L),create(7L)));
        when(published.resolve(any())).thenReturn(new DppmsPublishedProjectScopeApi.Scope(160L,List.of(new DppmsProjectBindingApi.ProjectBinding(7L,"101",10L,"10","P1","P1"))));
        when(projects.inspectBindings(any())).thenReturn(List.of(JsonUtils.parseObject("{\"id\":10}",ProjectMasterMigrationApi.Facts.class)));
        when(owner.apply(any())).thenAnswer(c->c.<ProjectMemberSourceHistoryApi.Command>getArgument(0).entries().stream().map(e->
            new ProjectMemberSourceHistoryApi.Result(e.sourceKey(),"60".equals(e.originalFields().get("memberRole"))?80L:70L,
                "60".equals(e.originalFields().get("memberRole"))?"cus_project_customer_contact_relation":"proj_project_member_assignment",e.targetId()==null?"CREATED":"UNCHANGED","baseline")).toList());
    }
    Map<String,Object> raw(String id,String role) {
        var map=new LinkedHashMap<String,Object>();columns().forEach(n->map.put(n,null));
        map.put("id",id);map.put("projectId","101");map.put("memberRole",role);map.put("originalProjectType","10");map.put("projectType","10");map.put("originalProjectCode","P1");return map;
    }
    Batch batch(List<Row> rows,List<Binding> bindings){return new Batch("integration:1:31",rows,bindings,false,"RETAIN",false,"UPSERT",false);}
    @Test void oneSourceRoutesDisjointHistoryBranchesAndRetainsNulls() {
        var d=create(7L);new SyncDefinitionValidator(List.of(adapter)).validate(d);
        var root=d.sources().getFirst();assertNull(d.sources().getLast().sql());assertEquals(MEMBERS,d.sources().getLast().queryRef());
        var expanded=SyncSourceProjection.expand(new SyncSourceProjection.Input(d,root,List.of(raw("1","30"),raw("2","60"))));
        assertEquals(List.of("1"),expanded.getFirst().rows().stream().map(r->r.get("id")).toList());assertEquals("2",expanded.getLast().rows().getFirst().get("id"));
        var changes=adapter.apply(batch(List.of(new Row(MEMBERS,"1",expanded.getFirst().rows().getFirst(),null),new Row(CONTACTS,"2",expanded.getLast().rows().getFirst(),null)),List.of()));
        assertEquals(List.of(70L,80L),changes.stream().map(Change::targetId).toList());
        verify(owner).apply(argThat(c->c.entries().size()==2 && c.entries().getFirst().originalFields().containsKey("createBy") && c.entries().getFirst().originalFields().get("createBy")==null));
        assertEquals("cus_project_customer_contact_relation",adapter.descriptor().objects().getLast().targetTable());
    }
    @Test void changedOriginalAndCurrentPublishedScopeCannotWrite() {
        var row=new Row(MEMBERS,"1",raw("1","30"),null);var result=adapter.apply(batch(List.of(row),List.of())).getFirst();
        var binding=new Binding(MEMBERS,"1",70L,JsonUtils.parseMap(JsonUtils.toJsonString(result.after())));clearInvocations(owner);
        var changed=raw("1","30");changed.put("phoneNum","changed");assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(new Row(MEMBERS,"1",changed,70L)),List.of(binding))));
        when(published.resolve(any())).thenReturn(new DppmsPublishedProjectScopeApi.Scope(160L,List.of()));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(row),List.of())));verifyNoInteractions(owner);
    }
    @Test void duplicateIndependentMemberReadCannotReplaceSharedBranches() {
        var definition=create(7L);definition.sources().getLast().queryRef(null).sql(definition.sources().getFirst().sql());
        assertThrows(IllegalArgumentException.class,()->new SyncDefinitionValidator(List.of(adapter)).validate(definition));
    }
    @Test void originalProjectTypeMismatchCannotBeHiddenByEnrichment() {
        var fields=raw("1","30");fields.put("originalProjectType","afss");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(new Row(MEMBERS,"1",fields,null)),List.of())));verifyNoInteractions(owner);
    }
}
