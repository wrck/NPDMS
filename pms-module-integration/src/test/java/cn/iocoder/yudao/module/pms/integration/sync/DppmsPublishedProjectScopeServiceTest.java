package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncRunDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.PublishedProjectScopeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DppmsPublishedProjectScopeServiceTest {
    @Test void onlySuccessfulFullPublicationReplacesScopeAndFailedMembersStayInvisible() throws Exception {
        var fixture=new SyncRunTransactionTest();fixture.setup();
        try {
            var root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("sql/migrations")))root=root.getParent();
            var ddl=Files.readString(root.resolve("sql/migrations/V482__sync_published_project_scope.sql"))
                    .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci","");
            new ResourceDatabasePopulator(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8)))
                    .execute(fixture.context.getBean(DataSource.class));
            var service=new DppmsPublishedProjectScopeService(fixture.context.getBean(PublishedProjectScopeMapper.class));
            var tx=new TransactionTemplate(fixture.context.getBean(PlatformTransactionManager.class));
            var definition=full();var query=new DppmsPublishedProjectScopeApi.Query(1L,1L,List.of("1","2"));
            assertThrows(IllegalStateException.class,()->service.resolve(query));
            var first=run(100L,false);
            tx.executeWithoutResult(s->{service.capture(new DppmsPublishedProjectScopeService.Capture(first,definition,List.of(project("1"))));
                first.setStatus("SUCCESS");service.publish(new DppmsPublishedProjectScopeService.Completion(first,definition));});
            assertEquals(List.of("1"),service.resolve(query).projects().stream().map(DppmsProjectBindingApi.ProjectBinding::sourceProjectKey).toList());
            var consumer=DppmsProjectRealProductSyncTemplate.create(1L);
            var frozen=service.sourceScope(consumer);
            assertFalse(frozen.sources().getFirst().sql().contains("FROM pm_project "));
            assertEquals(List.of("1"),frozen.sources().getFirst().publishedProjects().stream().map(DppmsProjectBindingApi.ProjectBinding::sourceProjectKey).toList());
            assertNull(consumer.sources().getFirst().publishedProjects());
            var partial=run(200L,false);
            tx.executeWithoutResult(s->service.capture(new DppmsPublishedProjectScopeService.Capture(partial,definition,List.of(project("2")))));
            assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->service.publish(new DppmsPublishedProjectScopeService.Completion(partial,definition))));
            assertEquals(100L,service.resolve(query).runId());
            var preview=run(300L,true);preview.setStatus("PREVIEW_READY");
            tx.executeWithoutResult(s->{service.capture(new DppmsPublishedProjectScopeService.Capture(preview,definition,List.of(project("2"))));
                service.publish(new DppmsPublishedProjectScopeService.Completion(preview,definition));});
            assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_project_scope_publication",Integer.class));
            var last=run(400L,false);
            tx.executeWithoutResult(s->{service.capture(new DppmsPublishedProjectScopeService.Capture(last,definition,List.of(project("2"))));
                last.setStatus("SUCCESS");service.publish(new DppmsPublishedProjectScopeService.Completion(last,definition));});
            assertEquals(List.of("2"),service.resolve(query).projects().stream().map(DppmsProjectBindingApi.ProjectBinding::sourceProjectKey).toList());
            assertEquals(List.of("1"),frozen.sources().getFirst().publishedProjects().stream().map(DppmsProjectBindingApi.ProjectBinding::sourceProjectKey).toList());
            assertEquals(List.of("2"),service.sourceScope(consumer).sources().getFirst().publishedProjects().stream().map(DppmsProjectBindingApi.ProjectBinding::sourceProjectKey).toList());
            assertEquals(3,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_project_scope_member",Integer.class));
            assertTrue(service.resolve(new DppmsPublishedProjectScopeApi.Query(1L,1L,List.of())).projects().isEmpty());
            assertThrows(IllegalArgumentException.class,()->service.resolve(new DppmsPublishedProjectScopeApi.Query(2L,1L,List.of("2"))));
            assertThrows(IllegalStateException.class,()->service.resolve(new DppmsPublishedProjectScopeApi.Query(1L,2L,List.of("2"))));
            var invalid=run(500L,false);
            var issue=new DataSyncAdapter.Change("pm_project_header","3",null,"ISSUE",Map.of(),Map.of(),"invalid");
            assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(s->service.capture(
                    new DppmsPublishedProjectScopeService.Capture(invalid,definition,List.of(project("3"),issue)))));
            assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_project_scope_member WHERE run_id=500",Integer.class));
            var empty=run(600L,false);empty.setStatus("SUCCESS");
            tx.executeWithoutResult(s->service.publish(new DppmsPublishedProjectScopeService.Completion(empty,definition)));
            assertTrue(service.resolve(query).projects().isEmpty());
            assertTrue(service.sourceScope(consumer).sources().getFirst().publishedProjects().isEmpty());
        } finally {fixture.close();}
    }
    @Test void restrictedOrCheckpointRunsCannotPublishFullScope() {
        var restricted=DppmsProjectMasterSyncTemplate.create(1L);var sources=new ArrayList<>(restricted.sources());
        sources.set(0,sources.getFirst().toBuilder().parameters(Map.of("projectTypes","10","projectIds","")).build());
        assertFalse(DppmsPublishedProjectScopeService.eligible(restricted.toBuilder().sources(sources).build()));
        assertTrue(DppmsPublishedProjectScopeService.eligible(full()));
        assertFalse(DppmsPublishedProjectScopeService.eligible(full().toBuilder().restartPolicy("CHECKPOINT_KEY").build()));
        assertFalse(DppmsPublishedProjectScopeService.eligible(full().toBuilder().readStrategy("KEYSET_PAGING").build()));
    }
    private static SyncDefinition full() {
        var d=DppmsProjectMasterSyncTemplate.create(1L);var sources=new ArrayList<>(d.sources());
        var params=new HashMap<>(sources.getFirst().parameters());params.put("projectTypes","10,afss,afxx");
        sources.set(0,sources.getFirst().toBuilder().parameters(params).build());return d.toBuilder().sources(sources).build();
    }
    private static SyncRunDO run(long id,boolean preview) {
        var r=new SyncRunDO().setId(id).setTaskId(1L).setFullSnapshot(true).setPreview(preview).setStatus("APPLYING");
        r.setTenantId(1L);return r;
    }
    private static DataSyncAdapter.Change project(String id) {
        var original=new LinkedHashMap<String,Object>();DppmsProjectMasterSyncTemplate.ORIGINAL_COLUMNS.forEach(c->original.put(c,tools.jackson.databind.node.NullNode.getInstance()));
        original.put("projectId",id);original.put("projectType","10");original.put("projectCode","P"+id);
        return new DataSyncAdapter.Change("pm_project_header",id,Long.valueOf(id),"UNCHANGED",Map.of(),
                Map.of("originalFields",original,"project",Map.of("id",Long.valueOf(id),"tenantId",1L,"projectType","10","projectCode","P"+id)),null);
    }
}
