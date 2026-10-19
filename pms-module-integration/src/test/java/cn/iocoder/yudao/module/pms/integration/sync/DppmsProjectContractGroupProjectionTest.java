package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Row;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DppmsProjectContractGroupProjectionTest {
    private SyncSourceProjection.Input input(Row... original) {
        var definition=DppmsGroupedProjectContractSyncTemplate.create(9L);var group=new LinkedHashMap<String,Object>();
        group.put("projectType","10");group.put("originalProjectId",50370);group.put("originalProjectCode","P1");group.put("contractNo","C1");group.put("migrationKey","identity");
        var rows=new ArrayList<Map<String,Object>>();
        for(var source:original) {var row=new LinkedHashMap<>(source.fields());row.remove("originalProjectId");row.remove("originalProjectCode");row.remove("projectCandidateCount");rows.add(row);}
        group.put("sourceRelations",new tools.jackson.databind.json.JsonMapper().writeValueAsString(rows));return new SyncSourceProjection.Input(definition,definition.sources().getFirst(),List.of(group));
    }
    private Row row(int id,boolean history,String created,String creator,String updated,String updater) {
        return DppmsProjectContractFormalDecisionTest.source(id,id+1,"g"+id,history,created,creator,updated,updater);
    }
    @Test void currentWinsAndEveryOriginalAliasHasTheSameCompleteAuditProof() {
        var data=input(row(1,true,"2010-01-01 00:00:00","discarded",null,null),row(10,false,"2020-01-01 00:00:00","sys",null,null),row(20,false,"2021-01-01 00:00:00","sys","2025-01-01 00:00:00","other"));
        var rows=SyncSourceProjection.expand(data).getFirst().rows();assertEquals(3,rows.size());
        assertEquals("10",rows.getFirst().get("id").toString());assertEquals("ACTIVE",rows.getFirst().get("formalStatus"));
        assertEquals("other",rows.getFirst().get("formalUpdater"));assertEquals(3,rows.getFirst().get("formalGroupSize"));
        assertEquals(1,rows.stream().map(row->row.get("canonical_id")).distinct().count());
        assertTrue(DppmsProjectContractRelationSyncTemplate.create(9L).sources().getFirst().sql().contains("createdPick"));
        assertFalse(data.source().sql().contains("createdPick"));assertFalse(data.source().sql().contains("FROM pm_project "));
    }
    @Test void tiedAuditorsAndMismatchedGroupAreRejectedWithoutSelectingByTechnicalId() {
        var first=row(1,false,"2020-01-01 00:00:00","A",null,null);var second=row(2,false,"2020-01-01 00:00:00","B",null,null);
        assertThrows(IllegalArgumentException.class,()->SyncSourceProjection.expand(input(first,second)));
        var next=row(2,false,"2021-01-01 00:00:00","A","2025-01-01 00:00:00","B");
        var old=row(1,false,"2020-01-01 00:00:00","A","2025-01-01 00:00:00","C");
        assertThrows(IllegalArgumentException.class,()->SyncSourceProjection.expand(input(old,next)));
        next.fields().put("contractNo","other");assertThrows(IllegalArgumentException.class,()->SyncSourceProjection.expand(input(next)));
    }
    @Test void historicalOnlyAndMissingScopeKeepOriginalProtection() {
        var data=input(row(1,true,"2020-01-01 00:00:00","A",null,null));
        assertEquals("HISTORICAL",SyncSourceProjection.expand(data).getFirst().rows().getFirst().get("formalStatus"));
        data.rows().getFirst().remove("originalProjectId");assertThrows(IllegalArgumentException.class,()->SyncSourceProjection.expand(data));
    }
    @Test void completeGroupMatchesThePublishedProjectBeforeAuditExpansion() {
        var data=input(row(1,false,"2020-01-01 00:00:00","A",null,null));
        data.source().publishedProjects(List.of(new cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi.ProjectBinding(9L,"50370",99L,"10","P1","P1")));
        data.rows().getFirst().remove("originalProjectId");
        var scoped=new PublishedProjectScopeRows(data.source()).apply(data.rows().getFirst());
        assertEquals("50370",scoped.get("originalProjectId"));
        assertEquals(1,SyncSourceProjection.expand(new SyncSourceProjection.Input(data.definition(),data.source(),List.of(scoped))).getFirst().rows().size());
        data.rows().getFirst().put("originalProjectCode","OUTSIDE");
        assertNull(new PublishedProjectScopeRows(data.source()).apply(data.rows().getFirst()));
    }
}
