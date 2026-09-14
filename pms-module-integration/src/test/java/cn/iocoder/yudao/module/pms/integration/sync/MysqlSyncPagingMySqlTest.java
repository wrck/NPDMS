package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL SQL execution against synthetic SELECT literals; no source tables or data writes. */
@EnabledIfSystemProperty(named="skipITs", matches="false")
class MysqlSyncPagingMySqlTest {
    MysqlSyncReader reader() {
        assertEquals("npdms_test",System.getenv("NPDMS_DB_NAME"));
        assertEquals("23316",System.getenv("NPDMS_MYSQL_PORT"));
        return new MysqlSyncReader(new ExternalDataSourceApi() {
            public List<Info> list(){return List.of();}
            public Long save(Save command){throw new UnsupportedOperationException();}
            public Connection openReadOnly(Long id)throws SQLException {
                var c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                        System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
                c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);c.setAutoCommit(false);return c;
            }
        });
    }
    SyncDefinition definition(String sql) {
        var d=DppmsOrderSyncTemplate.create(1L).maxRows(2);
        return d.sources(List.of(d.sources().getFirst().toBuilder().sql(sql).parameters(Map.of()).build()));
    }
    @Test void sparseIdsUseConfiguredPageSizeAndExcludeRowsAboveCapturedUpperBound() throws Exception {
        var reader=reader();var d=definition("SELECT 1 AS id UNION ALL SELECT 9 UNION ALL SELECT 9007199254740993");
        var state=reader.pagingBounds(d);
        assertEquals(List.of(9007199254740993L),state.upperIds());
        d.sources().getFirst().sql(d.sources().getFirst().sql()+" UNION ALL SELECT 9007199254740994");
        var first=reader.readPage(d,state).objects().getFirst().rows();
        assertEquals(List.of("1","9"),first.stream().map(r->r.get("id").toString()).toList());
        state=state.advance(9,first.size(),d.maxRows());
        var last=reader.readPage(d,state).objects().getFirst().rows();
        assertEquals(List.of("9007199254740993"),last.stream().map(r->r.get("id").toString()).toList());
        assertTrue(state.advance(9007199254740993L,last.size(),d.maxRows()).finished());
    }
    @Test void emptySourceCompletesAndUnsupportedKeysOrByteOverflowFail() throws Exception {
        var reader=reader();var empty=definition("SELECT 1 AS id WHERE 1=0");
        var state=reader.pagingBounds(empty);
        assertEquals(List.of(0L),state.upperIds());
        assertTrue(reader.readPage(empty,state).objects().getFirst().rows().isEmpty());
        assertTrue(state.advance(0,0,2).finished());
        assertThrows(IllegalArgumentException.class,()->reader.pagingBounds(definition("SELECT 'abc' AS id")));
        assertThrows(IllegalArgumentException.class,()->reader.pagingBounds(definition("SELECT 1 AS id UNION ALL SELECT 1").maxRows(1)));
        assertThrows(IllegalArgumentException.class,()->reader.pagingBounds(definition("SELECT CAST(NULL AS SIGNED) AS id")));
        var d=definition("SELECT 1 AS id, REPEAT('x',100) AS payload").maxBytes(10);
        var bound=reader.pagingBounds(d);
        assertThrows(IllegalArgumentException.class,()->reader.readPage(d,bound));
    }
    @Test void exactPageBoundaryDoesNotRequireAnExtraEmptyRead() throws Exception {
        var reader=reader();var d=definition("SELECT 2 AS id UNION ALL SELECT 4");
        var state=reader.pagingBounds(d);var page=reader.readPage(d,state);
        assertEquals(2,page.objects().getFirst().rows().size());
        assertTrue(state.advance(4,2,2).finished());
    }
    @Test void actualDppmsTemplatePagesSyntheticTablesAndVerifiesSourceParents() throws Exception {
        var reader=reader();
        String suffix=UUID.randomUUID().toString().replace("-","");
        String head="test_dppms_head_"+suffix,line="test_dppms_line_"+suffix;
        try(var c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&allowPublicKeyRetrieval=true",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));var sql=c.createStatement()) {
            try {
                sql.execute("CREATE TABLE "+head+" (id BIGINT PRIMARY KEY,source VARCHAR(10),compCode VARCHAR(10),orderType VARCHAR(10),orderNumber VARCHAR(30),"
                        +"syncTime DATETIME,orderCreateTime DATETIME,customerRequireTime DATETIME,customerCode VARCHAR(30),customerName VARCHAR(30),"
                        +"projectName VARCHAR(30),orderComment VARCHAR(30),salesType VARCHAR(20)) ENGINE=InnoDB");
                sql.execute("CREATE TABLE "+line+" (id BIGINT PRIMARY KEY,source VARCHAR(10),compCode VARCHAR(10),lineType VARCHAR(10),orderNumber VARCHAR(30),"
                        +"lineNum VARCHAR(10),syncTime DATETIME,itemCode VARCHAR(30),itemDesc VARCHAR(30),orderQuantity DECIMAL(18,6),openQuantity DECIMAL(18,6),"
                        +"bundleCode VARCHAR(30),warrantyMonth INT,profitCenter VARCHAR(30),realOrderExecNumber VARCHAR(30),customInfo JSON) ENGINE=InnoDB");
                sql.execute("INSERT INTO "+head+" (id,source,compCode,orderType,orderNumber,syncTime,customerName) VALUES "
                        +"(1,'SMS','01','0','SALE','2026-09-14 10:00:00','C'),(4,'SMS','01','0','SALE','2026-09-14 10:00:00','C'),"
                        +"(9,'SMS','01','1','RETURN','2026-09-14 10:00:00','C')");
                sql.execute("INSERT INTO "+line+" (id,source,compCode,lineType,orderNumber,lineNum,syncTime,orderQuantity,openQuantity) VALUES "
                        +"(2,'SMS','01','0','SALE','10','2026-09-14 10:00:00',4,1),(8,'SMS','01','1','RETURN','10','2026-09-14 10:00:00',-4,-1),"
                        +"(11,'SMS','01','0','MISSING','10','2026-09-14 10:00:00',1,1)");
                var d=DppmsOrderSyncTemplate.create(1L).maxRows(2);
                d.sources().forEach(s->s.sql(s.sql().replace("pm_order_data_from_erp",head).replace("pm_order_line_from_erp",line)));
                var state=reader.pagingBounds(d);List<Integer> sizes=new ArrayList<>();List<String> parentFlags=new ArrayList<>();
                while(!state.finished()) {
                    var page=reader.readPage(d,state).objects().getFirst();sizes.add(page.rows().size());
                    for(var row:page.rows()) {
                        assertNull(row.get("migrationIssue"));
                        if("LINE".equals(page.object()))parentFlags.add(row.get("migrationParentExists").toString());
                    }
                    state=state.advance(MysqlSyncReader.pagingKey(page.rows().getLast().get("id")),page.rows().size(),d.maxRows());
                }
                assertEquals(List.of(2,1,2,1),sizes);assertEquals(List.of("1","1","0"),parentFlags);
            } finally {
                sql.execute("DROP TABLE IF EXISTS "+line);sql.execute("DROP TABLE IF EXISTS "+head);
            }
        }
    }
}
