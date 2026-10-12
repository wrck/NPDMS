package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsContractCollectionPlanSyncTemplate.*;

/** Only SELECTs on the designated legacy source; source rows and original columns must remain intact. */
@EnabledIfSystemProperty(named="npdms.collection-plan.source.mysql",matches="true")
class DppmsContractCollectionPlanSourceMySqlTest {
    @Test void actualSourceTemplatePreservesEveryRowAndOriginalColumn() throws Exception {
        String database=System.getenv("DPPMS_SOURCE_DB");
        assertEquals("dppms",database);
        try (var db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/"+database+"?serverTimezone=Asia/Shanghai&useSSL=false",
                System.getenv("DPPMS_SOURCE_USER"),System.getenv("DPPMS_SOURCE_PASSWORD"))) {
            db.setReadOnly(true);
            var source=create(18L).sources().getFirst();var bound=MysqlSyncReader.compile(source);String template=bound.sql();
            long originalCount=count(db,"SELECT COUNT(*) FROM pm_pb_plan_from_sms",List.of());
            assertEquals(originalCount,count(db,"SELECT COUNT(*) FROM ("+template+") completePlans",bound.values()));
            var sampled=new ArrayList<String>();
            try(var statement=prepared(db,template+" ORDER BY p.id LIMIT 10",bound.values());var rows=statement.executeQuery()) {
                var names=new HashSet<String>();
                for(int i=1;i<=rows.getMetaData().getColumnCount();i++) names.add(rows.getMetaData().getColumnLabel(i));
                var expected=new HashSet<>(ORIGINAL_COLUMNS);expected.addAll(ACTOR_COLUMNS);expected.addAll(ACTOR_REFERENCE_COLUMNS);assertEquals(expected,names);
                int checked=0;
                while(rows.next()) {
                    try(var direct=db.prepareStatement("SELECT * FROM pm_pb_plan_from_sms WHERE id=?")) {
                        direct.setLong(1,rows.getLong("id"));
                        try(var raw=direct.executeQuery()) {
                            assertTrue(raw.next());
                            for(String name:ORIGINAL_COLUMNS) assertEquals(raw.getObject(name),rows.getObject(name),"original column="+name);
                            assertFalse(raw.next());
                        }
                    }
                    checked++;sampled.add(rows.getString("id"));
                }
                assertEquals(Math.min(10,originalCount),checked);
            }
            var selected=MysqlSyncReader.compile(source.toBuilder().parameters(Map.of("planIds",String.join(",",sampled))).build());
            assertEquals(sampled.size(),count(db,"SELECT COUNT(*) FROM ("+selected.sql()+") sampledPlans",selected.values()));
            System.out.println("Read-only source verification: "+originalCount+" plan rows; complete original columns preserved.");
        }
    }
    private long count(Connection db,String sql,List<Object> values) throws SQLException {
        try(var statement=prepared(db,sql,values);var row=statement.executeQuery()) {assertTrue(row.next());return row.getLong(1);}
    }
    private PreparedStatement prepared(Connection db,String sql,List<Object> values) throws SQLException {
        var result=db.prepareStatement(sql);result.setQueryTimeout(60);
        for(int i=0;i<values.size();i++)result.setObject(i+1,values.get(i));return result;
    }
}
