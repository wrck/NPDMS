package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only CTE fixtures on the designated MySQL 8 target; no tables or business data are written. */
@EnabledIfSystemProperty(named="npdms.project-contract-formal.mysql",matches="true")
class DppmsProjectContractFormalSourceMySqlTest {
    @Test void sqlSelectsCurrentEarliestCreationAndLatestUpdateAcrossOldGroups() throws Exception {
        try(var db=fixture()) {
            var rows=read(db,"default");
            assertEquals(3,rows.size());
            var decisions=DppmsProjectContractFormalDecision.resolve(rows);
            for(var row:rows) {
                var selected=decisions.get(row.sourceKey());
                assertEquals("2:12",selected.sourceKey());assertEquals("ACTIVE",selected.status());assertEquals(3,selected.count());
                assertEquals("2021-01-01T00:00:00.123",selected.createdAt().toString());
                assertEquals("2025-01-01T00:00:00.456",selected.updatedAt().toString());
                assertEquals("sys",selected.creator());assertEquals("updater",selected.updater());
            }
            assertEquals(2,((Number)rows.getFirst().fields().get("id")).intValue());
        }
    }
    @Test void equalCreationTimesWithDifferentOrNullPeopleAreRejectedBySql() throws Exception {
        try(var db=fixture()) {
            var rows=read(db,"create-tie");
            assertTrue(rows.stream().allMatch(row->((Number)row.fields().get("formalCreateConflicts")).longValue()>0));
            assertThrows(IllegalArgumentException.class,()->DppmsProjectContractFormalDecision.resolve(rows));
        }
    }
    @Test void equalUpdateTimesWithCaseDifferentPeopleAreRejectedBySql() throws Exception {
        try(var db=fixture()) {
            var rows=read(db,"update-tie");
            assertTrue(rows.stream().allMatch(row->((Number)row.fields().get("formalUpdateConflicts")).longValue()>0));
            assertThrows(IllegalArgumentException.class,()->DppmsProjectContractFormalDecision.resolve(rows));
        }
    }
    @Test void historicalOnlyUsesMemberFallbackAndDoesNotInventAnEndDate() throws Exception {
        try(var db=fixture()) {
            var rows=read(db,"history");var decisions=DppmsProjectContractFormalDecision.resolve(rows);
            for(var row:rows) {
                var selected=decisions.get(row.sourceKey());
                assertEquals("HISTORICAL",selected.status());assertEquals("sys",selected.creator());
                assertEquals(selected.createdAt(),selected.updatedAt());assertNull(selected.updater());
            }
        }
    }
    private Connection fixture() throws Exception {
        assertEquals("24306",System.getenv("MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("DB_NAME"));
        var db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&serverTimezone=Asia/Shanghai",
                System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        db.setReadOnly(true);
        return db;
    }
    private List<Row> read(Connection db,String scenario) throws Exception {
        var source=DppmsProjectContractRelationSyncTemplate.create(18L).sources().getFirst();
        source.parameters(Map.of("projectTypes","10","projectIds","2"));
        var bound=MysqlSyncReader.compile(source);var rows=new ArrayList<Row>();
        try(var query=db.prepareStatement(fixtureSql(scenario)+bound.sql()+" ORDER BY relationSourceKey")) {
            for(int i=0;i<bound.values().size();i++) query.setObject(i+1,bound.values().get(i));
            try(var result=query.executeQuery()) {
                while(result.next()) {
                    var fields=new LinkedHashMap<String,Object>();
                    for(int i=1;i<=result.getMetaData().getColumnCount();i++) fields.put(result.getMetaData().getColumnLabel(i),result.getObject(i));
                    rows.add(new Row(DppmsProjectContractRelationSyncTemplate.OBJECT,result.getString("relationSourceKey"),fields,null));
                }
            }
        }
        return rows;
    }
    private String fixtureSql(String scenario) {
        boolean history="history".equals(scenario);
        String first=history?"NULL,NULL,NULL,NULL":"'2020-01-01 00:00:00','history','2030-01-01 00:00:00','history'";
        String second=history?"NULL,NULL,NULL,NULL":"'2021-01-01 00:00:00.123','sys',"+
                ("update-tie".equals(scenario)?"'2025-01-01 00:00:00.456','Updater'":"NULL,NULL");
        String third=history?"NULL,NULL,NULL,NULL":("create-tie".equals(scenario)?
                "'2021-01-01 00:00:00.123',NULL":"'2022-01-01 00:00:00','other'")+",'2025-01-01 00:00:00.456','updater'";
        String memberAudit=history?"'2019-01-01 00:00:00','sys',NULL,NULL":"NULL,NULL,NULL,NULL";
        String code=history?"P1-his":"P1";
        return "WITH pm_project(projectId,projectType,projectCode) AS (SELECT 1,'10','P1' UNION ALL SELECT 2,'10','P1'),"+
                "pm_project_contract(id,contractNo,projectGroupCode,createTime,createBy,updateTime,updateBy) AS ("+
                "SELECT 1,'C1','g1',"+first+" UNION ALL SELECT 2,'C1','g2',"+second+" UNION ALL SELECT 3,'C1','g3',"+third+"),"+
                "pm_project_group(id,projectGroupCode,projectGroupName,projectType,createTime,createBy,updateTime,updateBy) AS ("+
                "SELECT 21,'g1',NULL,'10',NULL,NULL,NULL,NULL UNION ALL SELECT 22,'g2',NULL,'10',NULL,NULL,NULL,NULL UNION ALL SELECT 23,'g3',NULL,'10',NULL,NULL,NULL,NULL),"+
                "pm_project_group_relationship(id,projectGroupCode,projectCode,mergeBranchMark,smsProjectCode,createTime,createBy,updateTime,updateBy) AS ("+
                "SELECT 11,'g1','P1-his',NULL,NULL,"+memberAudit+" UNION ALL SELECT 12,'g2','"+code+"',NULL,NULL,"+memberAudit+
                " UNION ALL SELECT 13,'g3','"+code+"',NULL,NULL,"+memberAudit+") ";
    }

}
