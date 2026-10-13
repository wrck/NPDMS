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
            // Frozen pre-Q018 task SQL remains supported without rewriting its history.
            definition.sources().getFirst().sql(DppmsMigrationSql.initial("project-contract-relations"));
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
    @Test void formalIdentityQueryReconcilesAllRepeatedSourceGroupsBeforeChunking() throws Exception {
        assertEquals("dppms",System.getenv("DPPMS_SOURCE_DB"));
        try(var db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/dppms?useSSL=false&serverTimezone=Asia/Shanghai",
                System.getenv("DPPMS_SOURCE_USER"),System.getenv("DPPMS_SOURCE_PASSWORD"))) {
            db.setReadOnly(true);
            var source=DppmsProjectContractRelationSyncTemplate.create(18L).sources().getFirst();
            var bound=MysqlSyncReader.compile(source);
            var rows=new ArrayList<cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Row>();
            try(var query=prepared(db,"SELECT * FROM ("+bound.sql()+") repeated WHERE originalProjectId IS NOT NULL AND formalGroupSize>1 ORDER BY relationSourceKey",bound.values());
                    var result=query.executeQuery()) {
                while(result.next()) {
                    var values=new LinkedHashMap<String,Object>();
                    for(int i=1;i<=result.getMetaData().getColumnCount();i++) values.put(result.getMetaData().getColumnLabel(i),result.getObject(i));
                    rows.add(new cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Row(
                            DppmsProjectContractRelationSyncTemplate.OBJECT,result.getString("relationSourceKey"),values,null));
                }
            }
            assertFalse(rows.isEmpty());
            var groups=rows.stream().collect(java.util.stream.Collectors.groupingBy(row->List.of(
                    row.fields().get("originalProjectId"),row.fields().get("contractNo"))));
            var decisions=DppmsProjectContractFormalDecision.resolve(rows);
            for(var group:groups.values()) {
                var selected=group.stream().filter(row->!row.fields().get("member_projectCode").toString().endsWith("-his")).toList();
                boolean current=!selected.isEmpty();if(!current) selected=group;
                var earliest=selected.stream().map(row->sourceDate(row.fields(),"createTime")).min(Comparator.naturalOrder()).orElseThrow();
                var latest=selected.stream().map(row->sourceDate(row.fields(),"updateTime")).filter(Objects::nonNull).max(Comparator.naturalOrder());
                for(var row:group) {
                    var decision=decisions.get(row.sourceKey());
                    assertEquals(group.size(),decision.count());assertEquals(current?"ACTIVE":"HISTORICAL",decision.status());
                    assertEquals(earliest,decision.createdAt());assertEquals(latest.orElse(earliest),decision.updatedAt());
                    var canonical=group.stream().filter(candidate->(candidate.fields().get("id")+":"+candidate.fields().get("member_id")).equals(decision.sourceKey())).findFirst().orElseThrow();
                    assertEquals(DppmsProjectContractRelationSyncAdapter.original(canonical.fields()),decision.canonical());
                    assertEquals(sourceActor(canonical.fields(),"createBy"),decision.creator());
                    var finalLatest=latest.orElse(null);
                    assertTrue(selected.stream().filter(candidate->Objects.equals(sourceDate(candidate.fields(),"updateTime"),finalLatest))
                            .allMatch(candidate->Objects.equals(sourceActor(candidate.fields(),"updateBy"),decision.updater())));
                }
            }
        }
    }
    private java.time.LocalDateTime sourceDate(Map<String,Object> fields,String name) {
        Object value=fields.get(name)==null?fields.get("member_"+name):fields.get(name);
        return value==null?null:SyncFieldMapper.sourceTime(value);
    }
    private String sourceActor(Map<String,Object> fields,String name) {
        Object value=fields.get(name);
        if(value==null || value.toString().isEmpty()) value=fields.get("member_"+name);
        return value==null || value.toString().isEmpty()?null:value.toString();
    }
    private PreparedStatement prepared(Connection db,String sql,List<Object> values) throws Exception {
        var statement=db.prepareStatement(sql);statement.setQueryTimeout(60);for(int i=0;i<values.size();i++)statement.setObject(i+1,values.get(i));return statement;
    }
}
