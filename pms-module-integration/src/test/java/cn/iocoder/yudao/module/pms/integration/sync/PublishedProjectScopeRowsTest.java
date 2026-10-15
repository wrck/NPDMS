package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi.ProjectBinding;
import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PublishedProjectScopeRowsTest {
    private ProjectBinding project(String key,long target,String code) {
        return new ProjectBinding(1L,key,target,"10",code,"NEW-"+target);
    }
    private SyncDefinition.Source source(String mode) {
        return SyncDefinition.Source.builder().object("RELATION").sourceObject("relations").readMode("SQL")
                .sql("SELECT id, project_id AS \"projectId\" FROM relations").sourceKey("id")
                .publishedProjectMatch(mode).parameters(Map.of("projectTypes","10,afss,afxx","projectIds",""))
                .publishedProjects(List.of(project("1",100,"OLD"),project("2",100,"CURRENT"))).build();
    }
    @Test void membershipIncludesAliasesButCanonicalStateUsesMaximumOriginalId() {
        var ids=new PublishedProjectScopeRows(source("ID"));
        assertEquals("OLD",ids.apply(Map.of("projectId",1)).get("originalProjectCode"));
        assertNull(ids.apply(Map.of("projectId",3)));
        var canonical=new PublishedProjectScopeRows(source("CANONICAL_ID"));
        assertNull(canonical.apply(Map.of("projectId",1)));
        assertEquals("CURRENT",canonical.apply(Map.of("projectId",2)).get("originalProjectCode"));
        var restricted=new PublishedProjectScopeRows(source("ID").toBuilder().parameters(Map.of("projectIds","2")).build());
        assertNull(restricted.apply(Map.of("projectId",1)));
        assertNotNull(restricted.apply(Map.of("projectId",2)));
    }
    @Test void contractCodeMatchingIsExactAndRejectsAmbiguousTargets() {
        var scope=new PublishedProjectScopeRows(source("CODE"));
        var row=scope.apply(Map.of("member_projectCode","CURRENT-his","group_projectType","10"));
        assertEquals("2",row.get("originalProjectId"));assertEquals("2",row.get("canonical_originalProjectId"));
        assertNull(scope.apply(Map.of("member_projectCode","current","group_projectType","10")));
        var ambiguous=new PublishedProjectScopeRows(source("CODE").toBuilder().publishedProjects(
                List.of(project("2",100,"CURRENT"),project("3",200,"CURRENT"))).build());
        assertThrows(IllegalArgumentException.class,()->ambiguous.apply(Map.of("member_projectCode","CURRENT","group_projectType","10")));
    }
    @Test void absentAndEmptyPublicationFailClosedAndRuntimeRowsCannotBeSavedOrInjected() {
        assertThrows(IllegalStateException.class,()->new PublishedProjectScopeRows(source("ID").toBuilder().publishedProjects(null).build()));
        assertNull(new PublishedProjectScopeRows(source("ID").toBuilder().publishedProjects(List.of()).build()).apply(Map.of("projectId",1)));
        var saved=JsonUtils.toJsonString(source("ID"));assertFalse(saved.contains("publishedProjects"));
        var restored=JsonUtils.parseObject(saved,SyncDefinition.Source.class);assertNull(restored.publishedProjects());
    }
    @Test void streamingSkipsOutsideRowsAcrossChunksWithoutLosingSelectedRows() throws Exception {
        String url="jdbc:h2:mem:published_scope_rows;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE relations(id BIGINT PRIMARY KEY,project_id BIGINT)");
            statement.execute("INSERT INTO relations VALUES(1,3),(2,1),(3,3),(4,2),(5,3)");
        }
        var api=new ExternalDataSourceApi() {
            public List<Info> list(){return List.of();}
            public Long save(Save command){throw new UnsupportedOperationException();}
            public Connection openReadOnly(Long id)throws SQLException{return DriverManager.getConnection(url,"sa","");}
        };
        var definition=SyncDefinition.builder().sourceSystem("DPPMS").connectionId(1L).mode("ONCE")
                .readStrategy("STREAMING_CURSOR").chunkSize(1).maxRows(1).maxBytes(10000).sources(List.of(source("ID"))).build();
        var rows=new ArrayList<Map<String,Object>>();
        var result=new SpringJdbcStreamingReader(api).stream(definition,null,true,null,c->{if(!c.sourceComplete())rows.addAll(c.rows());});
        assertEquals(2,result.rows());assertEquals(2,result.chunks());
        assertEquals(List.of(2L,4L),rows.stream().map(r->r.get("id")).toList());
        assertEquals(List.of("OLD","CURRENT"),rows.stream().map(r->r.get("originalProjectCode")).toList());
        assertThrows(IllegalArgumentException.class,()->PublishedProjectScopeRows.validate(definition.toBuilder().restartPolicy("CHECKPOINT_KEY").build()));
    }
}
