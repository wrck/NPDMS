package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only verification of the configured SQL and the existing no-key collector identity. */
@EnabledIfSystemProperty(named="npdms.associated-products.source.mysql",matches="true")
class DppmsProjectAssociatedProductsSourceMySqlTest {
    MysqlSyncReader reader() {
        return new MysqlSyncReader(new ExternalDataSourceApi() {
            public List<Info> list(){return List.of();}
            public Long save(Save command){throw new UnsupportedOperationException("read-only");}
            public Connection openReadOnly(Long id)throws SQLException {
                var c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/"+System.getenv("DPPMS_SOURCE_DB")+"?connectTimeout=10000&socketTimeout=60000&serverTimezone=Asia/Shanghai",
                        System.getenv("DPPMS_SOURCE_USER"),System.getenv("DPPMS_SOURCE_PASSWORD"));
                c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);c.setAutoCommit(false);return c;
            }
        });
    }
    @Test void wholeSqlReadRetainsEveryOriginalColumnAndAllDuplicateOccurrences()throws Exception {
        var definition=DppmsProjectAssociatedProductSyncTemplate.create(1L);var snapshot=reader().read(definition,null,true);
        assertEquals(2,snapshot.objects().size());
        for(int i=0;i<2;i++) {
            var rows=snapshot.objects().get(i).rows();var source=definition.sources().get(i);assertFalse(rows.isEmpty());
            assertEquals(rows.size(),rows.stream().map(row->row.get("rowKey")).distinct().count());
            assertTrue(rows.stream().allMatch(row->row.keySet().containsAll(source.sourceKeyColumns()) && row.get("rowKey").toString().matches("[0-9a-f]{64}:[1-9][0-9]*")));
        }
        var leases=snapshot.objects().getFirst().rows();
        assertTrue(leases.stream().anyMatch(row->row.get("rowKey").toString().endsWith(":2")),"Original duplicate rows must not be removed");
    }
    @Test void approvedTenProjectParameterIsAnEmptySelectionWithoutInventedRows()throws Exception {
        var original=DppmsProjectAssociatedProductSyncTemplate.create(1L);
        var sources=original.sources().stream().map(source->source.toBuilder().parameters(Map.of("projectTypes","10",
                "projectIds","50370,51304,51922,52443,52564,53487,53546,53614,53649,69300")).build()).toList();
        var snapshot=reader().read(original.toBuilder().sources(sources).build(),null,true);
        assertEquals(2,snapshot.objects().size());assertTrue(snapshot.objects().stream().allMatch(object->object.rows().isEmpty()));
    }
}
