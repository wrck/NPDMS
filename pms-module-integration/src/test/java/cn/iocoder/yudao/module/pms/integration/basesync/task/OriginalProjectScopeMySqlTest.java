package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncPublishedProjects;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the actual source scope XML on connection-local copies of source tables. */
class OriginalProjectScopeMySqlTest extends RemainingTaskMySqlSupport {
    void sourceScope() throws Exception {
        jdbc.update("INSERT INTO pm_project(projectId,projectType,projectCode,disabled) VALUES(1,'10','P1',0),(2,'10','P1',0),(3,'afss','P3',0),(4,'10','P4',1),(5,'10','P5',0)");
        var resource="base-sync-source/BaseSyncPublishedProjectsSourceMapper.xml";
        String xml;try(var input=new ClassPathResource(resource).getInputStream()){xml=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
        // MySQL cannot reopen the same TEMPORARY table. Only fixture table names are substituted.
        var matcher=java.util.regex.Pattern.compile("(?i)(FROM|JOIN)\\s+pm_project\\b").matcher(xml);
        var output=new StringBuffer();int index=0;
        while(matcher.find()) {
            String table="pm_project";
            if(++index>1){table="scope_project_"+index;jdbc.execute("CREATE TEMPORARY TABLE "+table+" LIKE pm_project");jdbc.update("INSERT INTO "+table+" SELECT * FROM pm_project");}
            matcher.appendReplacement(output,matcher.group(1)+" "+table);
        }
        matcher.appendTail(output);var config=session.getConfiguration();
        new XMLMapperBuilder(new java.io.StringReader(output.toString()),config,resource,config.getSqlFragments()).parse();
    }
    @Test void previewSourceSnapshotUsesOriginalCanonicalIdsAndNeverFakesACompletionReceipt() throws Exception {
        sourceScope();var service=new BaseSyncPublishedProjects(session.getMapper(BaseSyncPublishedProjects.ReferenceMapper.class));
        var preview=service.load(new BaseSyncPublishedProjects.Query(1,3,100,source,true));
        assertEquals(0,preview.publicationId());assertEquals(Set.of("1","2","5"),preview.members().keySet());assertEquals(Set.of(2L,5L),preview.canonical().keySet());
        assertEquals(2L,preview.members().get("1").projectId());assertEquals(2L,preview.members().get("2").projectId());
        assertThrows(UnsupportedOperationException.class,()->preview.members().clear());
        assertThrows(IllegalStateException.class,()->service.load(new BaseSyncPublishedProjects.Query(1,3,2,source,true)));
        assertThrows(IllegalStateException.class,()->transaction.execute(status->service.load(new BaseSyncPublishedProjects.Query(1,3,100))));
        assertTrue(saved.isEmpty());
    }
}
