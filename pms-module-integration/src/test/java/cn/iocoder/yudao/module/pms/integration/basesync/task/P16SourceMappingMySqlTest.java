package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.AbstractSyncTask.SourceQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Native source result mapping; every fixture is connection-local. */
class P16SourceMappingMySqlTest extends RemainingTaskMySqlSupport {
    @Test void retainsReadonlyValuesWhenThePrimaryImplementationValueIsNullOrEmpty() throws Exception {
        jdbc.update("INSERT INTO pm_project(projectId,projectType,projectCode,column012,columno12_readonly,disabled) VALUES(1,'10','A',NULL,1,0),(2,'10','B','',0,0),(3,'10','C','3',-1,0)");
        String resource="base-sync-source/P16SourceMapper.xml";
        String xml;
        try(var input=new ClassPathResource(resource).getInputStream()) {
            xml=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        // Native MySQL cannot reopen a temporary table in one statement.
        // Only fixture table names change; the production query and result mapping stay intact.
        var matcher=java.util.regex.Pattern.compile("(?i)(FROM|JOIN)\\s+pm_project\\b").matcher(xml);
        var output=new StringBuffer();int index=0;
        while(matcher.find()) {
            String table="pm_project";
            if(++index>1) {
                table="p16_source_project_"+index;
                jdbc.execute("CREATE TEMPORARY TABLE "+table+" LIKE pm_project");
                jdbc.update("INSERT INTO "+table+" SELECT * FROM pm_project");
            }
            matcher.appendReplacement(output,matcher.group(1)+" "+table);
        }
        matcher.appendTail(output);
        var configuration=session.getConfiguration();
        new XMLMapperBuilder(new java.io.StringReader(output.toString()),configuration,resource,configuration.getSqlFragments()).parse();
        var rows=new ArrayList<P16ProjectSyncTask.Source>();
        session.getMapper(P16ProjectSyncTask.SourceMapper.class).selectProjectMaster(
                new SourceQuery<>(100,1,true,null),result->rows.add(result.getResultObject()));
        assertEquals(3,rows.size());
        assertNull(rows.get(0).getColumn012());assertEquals(1L,rows.get(0).getColumno12_readonly());
        assertEquals("",rows.get(1).getColumn012());assertEquals(0L,rows.get(1).getColumno12_readonly());
        assertEquals("3",rows.get(2).getColumn012());assertEquals(-1L,rows.get(2).getColumno12_readonly());
        assertTrue(saved.isEmpty());
    }
}
