package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectMasterSyncTemplate.*;

/** Full source-count and raw-column verification, using SELECT only and no credential fields. */
@EnabledIfSystemProperty(named="npdms.collection-plan.source.mysql",matches="true")
class DppmsProjectMasterSourceMySqlTest {
    @Test void originalThirtySevenColumnsFromMaximumProjectIdPerTypeAndCodeArePreserved() throws Exception {
        assertEquals("dppms",System.getenv("DPPMS_SOURCE_DB"));
        try(var db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/dppms?useSSL=false&serverTimezone=Asia/Shanghai",
                System.getenv("DPPMS_SOURCE_USER"),System.getenv("DPPMS_SOURCE_PASSWORD"))) {
            db.setReadOnly(true);var bound=MysqlSyncReader.compile(create(18L).sources().getFirst());String sql=bound.sql();
            assertEquals(count(db,"SELECT COUNT(*) FROM (SELECT MAX(projectId) FROM pm_project WHERE projectType='10' GROUP BY BINARY projectType,BINARY projectCode) canonical"),
                    count(db,"SELECT COUNT(*) FROM ("+sql+") allHeaders",bound.values()));
            assertEquals(0,count(db,"SELECT COUNT(*) FROM ("+sql+") selected JOIN pm_project newer ON newer.projectId>selected.projectId "
                    +"AND newer.projectType=selected.projectType AND newer.projectCode=selected.projectCode "
                    +"AND BINARY newer.projectType=BINARY selected.projectType AND BINARY newer.projectCode=BINARY selected.projectCode",bound.values()));
            assertEquals(count(db,"SELECT COUNT(*) FROM pm_project WHERE projectType='10'"),count(db,"SELECT COUNT(*) FROM pm_project original "
                    +"JOIN ("+sql+") selected ON original.projectType=selected.projectType AND original.projectCode <=> selected.projectCode "
                    +"AND BINARY original.projectType=BINARY selected.projectType "
                    +"AND BINARY original.projectCode <=> BINARY selected.projectCode",bound.values()));
            var identities=MysqlSyncReader.compile(SyncDefinition.Source.builder().object("pm_project")
                    .sourceObject("pm_project").readMode("SQL").sourceKey("originalProjectId")
                    .sql(DppmsMigrationSql.initial("project-identities"))
                    .parameters(Map.of("projectTypes","10","projectIds","")).build());
            assertEquals(count(db,"SELECT COUNT(*) FROM pm_project WHERE projectType='10'"),count(db,
                    "SELECT COUNT(DISTINCT originalProjectId) FROM ("+identities.sql()+") identities",identities.values()));
            assertEquals(count(db,"SELECT COUNT(*) FROM ("+sql+") selected",bound.values()),count(db,
                    "SELECT COUNT(DISTINCT canonicalProjectId) FROM ("+identities.sql()+") identities",identities.values()));
            try(var statement=prepared(db,"SELECT * FROM ("+sql+") sampled ORDER BY projectId LIMIT 10",bound.values());var result=statement.executeQuery()) {
                Set<String> names=new HashSet<>();for(int i=1;i<=result.getMetaData().getColumnCount();i++)names.add(result.getMetaData().getColumnLabel(i));
                Set<String> expected=new HashSet<>(ORIGINAL_COLUMNS);expected.addAll(ACTOR_COLUMNS);expected.addAll(ACTOR_REFERENCE_COLUMNS);expected.add("migrationCompanyCode");
                assertEquals(expected,names);int verified=0;
                while(result.next()) try(var direct=db.prepareStatement("SELECT * FROM pm_project WHERE projectId=?")) {
                    direct.setLong(1,result.getLong("projectId"));try(var raw=direct.executeQuery()) {
                        assertTrue(raw.next());for(String column:ORIGINAL_COLUMNS)assertTrue(Objects.equals(raw.getObject(column),result.getObject(column)),"原字段不同: "+column);
                        assertFalse(raw.next());verified++;
                    }
                }
                assertEquals(10,verified);
            }
        }
    }
    private long count(Connection db,String sql) throws Exception {
        try(var statement=db.createStatement();var result=statement.executeQuery(sql)) {assertTrue(result.next());return result.getLong(1);}
    }
    private long count(Connection db,String sql,List<Object> values) throws Exception {
        try(var statement=prepared(db,sql,values);var result=statement.executeQuery()) {assertTrue(result.next());return result.getLong(1);}
    }
    private PreparedStatement prepared(Connection db,String sql,List<Object> values) throws Exception {
        var statement=db.prepareStatement(sql);statement.setQueryTimeout(60);for(int i=0;i<values.size();i++)statement.setObject(i+1,values.get(i));return statement;
    }
}
