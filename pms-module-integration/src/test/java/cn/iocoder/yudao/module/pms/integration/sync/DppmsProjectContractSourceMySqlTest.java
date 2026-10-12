package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real source SELECTs verify whole-table coverage and the canonical metadata used across chunks. */
@EnabledIfSystemProperty(named="npdms.collection-plan.source.mysql",matches="true")
class DppmsProjectContractSourceMySqlTest {
    @Test void completeOriginalContractTableIsRetainedWithItsFullBridgeAndCanonicalColumns() throws Exception {
        assertEquals("dppms",System.getenv("DPPMS_SOURCE_DB"));
        try(var db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/dppms?useSSL=false&serverTimezone=Asia/Shanghai",
                System.getenv("DPPMS_SOURCE_USER"),System.getenv("DPPMS_SOURCE_PASSWORD"))) {
            db.setReadOnly(true);
            var definition=DppmsProjectContractRelationSyncTemplate.create(18L);
            assertEquals("STREAMING_CURSOR",definition.effectiveReadStrategy());
            var bound=MysqlSyncReader.compile(definition.sources().getFirst());
            try(var query=prepared(db,"SELECT COUNT(*) FROM ("+bound.sql()+") selected WHERE projectCandidateCount>1",bound.values());var result=query.executeQuery()) {
                assertTrue(result.next());assertEquals(0,result.getLong(1));
            }
            try(var raw=db.createStatement();var total=raw.executeQuery("SELECT COUNT(*) FROM pm_project_contract c LEFT JOIN pm_project_group g "
                    +"ON g.projectGroupCode=c.projectGroupCode AND BINARY g.projectGroupCode=BINARY c.projectGroupCode "
                    +"WHERE g.id IS NULL OR g.projectType='10'")) {
                assertTrue(total.next());long count=total.getLong(1);
                try(var query=prepared(db,"SELECT COUNT(DISTINCT id) FROM ("+bound.sql()+") complete_source",bound.values());var result=query.executeQuery()) {
                    assertTrue(result.next());assertEquals(count,result.getLong(1));
                }
            }
            try(var query=prepared(db,"SELECT * FROM ("+bound.sql()+") sample WHERE projectCandidateCount=1 ORDER BY relationSourceKey LIMIT 100",bound.values());
                    var rows=query.executeQuery()) {
                var names=new HashSet<String>();for(int i=1;i<=rows.getMetaData().getColumnCount();i++)names.add(rows.getMetaData().getColumnLabel(i));
                assertTrue(names.containsAll(DppmsProjectContractRelationSyncTemplate.columns()));
                assertTrue(names.containsAll(DppmsProjectContractRelationSyncTemplate.STREAM_COLUMNS));
                int verified=0;
                while(rows.next()) {
                    var fields=new HashMap<String,Object>();
                    for(String name:List.of("canonicalOriginalId","id","member_id"))fields.put(name,rows.getObject(name));
                    assertEquals(DppmsProjectContractDuplicateDecision.orderedSourceKey(fields),rows.getString("relationSourceKey"));
                    try(var direct=db.prepareStatement("SELECT * FROM pm_project_contract WHERE id=?")) {
                        direct.setLong(1,rows.getLong("id"));try(var original=direct.executeQuery()) {
                            assertTrue(original.next());for(String name:DppmsProjectContractRelationSyncTemplate.ORIGINAL_COLUMNS)
                                assertTrue(Objects.equals(original.getObject(name),rows.getObject(name)),"原合同字段不同: "+name);
                        }
                    }
                    try(var direct=db.prepareStatement("SELECT id,createTime FROM pm_project_contract WHERE contractNo <=> ? AND projectGroupCode <=> ? AND BINARY contractNo <=> BINARY ? AND BINARY projectGroupCode <=> BINARY ? AND BINARY createBy <=> BINARY ? AND updateTime <=> ? AND BINARY updateBy <=> BINARY ? ORDER BY createTime,id LIMIT 1")) {
                        int position=1;for(String name:List.of("contractNo","projectGroupCode","contractNo","projectGroupCode","createBy","updateTime","updateBy"))direct.setObject(position++,rows.getObject(name));
                        try(var canonical=direct.executeQuery()) {
                            assertTrue(canonical.next());assertEquals(canonical.getLong("id"),rows.getLong("canonicalOriginalId"));
                            assertEquals(canonical.getTimestamp("createTime"),rows.getTimestamp("canonicalCreatedAt"));
                        }
                    }
                    verified++;
                }
                assertEquals(100,verified);
            }
            try(var query=prepared(db,"SELECT COUNT(*) FROM ("+bound.sql()+") selected JOIN pm_project newer "
                    +"ON newer.projectId>selected.originalProjectId AND newer.projectCode=selected.originalProjectCode "
                    +"AND newer.projectType=selected.group_projectType AND BINARY newer.projectCode=BINARY selected.originalProjectCode "
                    +"AND BINARY newer.projectType=BINARY selected.group_projectType",bound.values());var result=query.executeQuery()) {
                assertTrue(result.next());assertEquals(0,result.getLong(1));
            }
        }
    }
    private PreparedStatement prepared(Connection db,String sql,List<Object> values) throws Exception {
        var statement=db.prepareStatement(sql);statement.setQueryTimeout(60);for(int i=0;i<values.size();i++)statement.setObject(i+1,values.get(i));return statement;
    }
}
