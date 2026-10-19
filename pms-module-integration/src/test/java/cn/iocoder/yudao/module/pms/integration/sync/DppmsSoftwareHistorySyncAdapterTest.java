package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsSoftwareHistorySyncTemplate.*;
class DppmsSoftwareHistorySyncAdapterTest {
    SyncOwnerRunContext context=mock(SyncOwnerRunContext.class);DppmsPublishedProjectScopeApi published=mock(DppmsPublishedProjectScopeApi.class);
    ProjectMasterMigrationApi projects=mock(ProjectMasterMigrationApi.class);DppmsAssetSourceHistoryApi owner=mock(DppmsAssetSourceHistoryApi.class);
    DppmsSoftwareHistorySyncAdapter adapter=new DppmsSoftwareHistorySyncAdapter(context,published,projects,owner);
    SyncDefinition definition=create(new Request(7L,99L));
    @BeforeEach void setup(){
        when(context.require(any())).thenReturn(new SyncOwnerRunContext.Context(1L,2L,new SyncTaskDO().setId(35L).setConnectionId(7L),new SyncRunDO().setId(350L).setStartedAt(LocalDateTime.of(2020,1,1,0,0)),definition));
        var identity=new DppmsProjectBindingApi.ProjectBinding(7L,"101",10L,"10","P1","P1");
        when(published.resolve(any())).thenReturn(new DppmsPublishedProjectScopeApi.Scope(160L,List.of(identity)));
        // Frozen execution definitions intentionally contain no runtime publishedProjects list.
        when(projects.inspectBindings(any())).thenReturn(List.of(JsonUtils.parseObject("{\"id\":10}",ProjectMasterMigrationApi.Facts.class)));
        when(owner.importSourceHistory(any())).thenReturn(List.of(
            NativeEntityMigrationRow.mapped("software-changes","1",new ExternalTargetMapping("AST","DeviceSoftwareChangeBatch","ast_device_software_change_batch",700L,"SOFTWARE_BATCH",0)),
            NativeEntityMigrationRow.mapped("software-versions","2",new ExternalTargetMapping("AST","SoftwareVersionRecord","ast_device_software_version_record",900L,"SOFTWARE_VERSION_RECORD",0))));
    }
    Map<String,Object> raw(String object){var raw=new LinkedHashMap<String,Object>();(BATCH.equals(object)?BATCH_COLUMNS:VERSION_COLUMNS).forEach(n->raw.put(n,null));
        raw.put("id",BATCH.equals(object)?"1":"2");raw.put("projectId","101");raw.put("createTime","2020-01-01T00:00:00");if(VERSION.equals(object))raw.put("logId","1");return raw;}
    List<MysqlSyncReader.SourceRows> expand(Map<String,Object> parent,Map<String,Object> child){
        String payload=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(List.of(Map.of("object",BATCH,"fields",parent),Map.of("object",VERSION,"fields",child)));
        return SyncSourceProjection.expand(new SyncSourceProjection.Input(definition,definition.sources().getFirst(),List.of(Map.of("_groupKey","1","projectId","101","projectType","10","originalProjectCode","P1","_sourceRows",payload))));
    }
    List<Row> rows(){var sources=expand(raw(BATCH),raw(VERSION));return sources.stream().flatMap(s->s.rows().stream().map(r->new Row(s.object(),r.get("id").toString(),r,null))).toList();}
    Batch batch(List<Row> rows,List<Binding> bindings){return new Batch("integration:1:35",rows,bindings,false,"RETAIN",false,"UPSERT",false);}
    @Test void combinedProjectionPreservesBothOriginalsAndUsesOnlyLocalProjectScope(){
        var plain=create(new Request(7L,99L));new SyncDefinitionValidator(List.of(adapter)).validate(plain);
        assertFalse(plain.sources().getFirst().sql().contains("pm_project "));assertEquals(BATCH,plain.sources().getLast().queryRef());assertNull(plain.sources().getLast().sql());
        var result=adapter.apply(batch(rows(),List.of()));assertEquals(List.of(700L,900L),result.stream().map(Change::targetId).toList());
        verify(owner).importSourceHistory(argThat(c->c.batches().size()==1 && c.observations().size()==1 && c.observations().getFirst().logId()==1L
            && c.observations().getFirst().mappedBatchId()==null && c.observations().getFirst().extensionDefinitionId()==99L && c.observations().getFirst().sourceUpdatedBy()==null));
    }
    @Test void missingOrCrossProjectParentAndChangedRawSourceCannotWrite(){
        var child=raw(VERSION);child.put("projectId","102");assertThrows(IllegalArgumentException.class,()->expand(raw(BATCH),child));
        var onlyChild=rows().getLast();assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(onlyChild),List.of())));
        var full=rows();var changes=adapter.apply(batch(full,List.of()));var old=changes.stream().map(c->new Binding(c.object(),c.sourceKey(),c.targetId(),JsonUtils.parseMap(JsonUtils.toJsonString(c.after())))).toList();clearInvocations(owner);
        var changed=new LinkedHashMap<>(full.getLast().fields());changed.put("conpBak","changed");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(new Row(BATCH,"1",full.getFirst().fields(),700L),new Row(VERSION,"2",changed,900L)),old)));verifyNoInteractions(owner);
    }
    @Test void templateCannotBeChangedToReadUnapprovedIndependentSources() {
        var d=create(new Request(7L,99L));d.sources().getLast().queryRef(null).sql("SELECT * FROM pm_project_soft_version");
        assertThrows(IllegalArgumentException.class,()->new SyncDefinitionValidator(List.of(adapter)).validate(d));
    }
    @Test void missingDefinitionCannotBeFilledWithAFabricatedId(){
        definition.sources().getFirst().parameters(Map.of("extensionDefinitionId",""));assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(rows(),List.of())));verifyNoInteractions(owner);
    }
}
