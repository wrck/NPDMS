package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only source acceptance. No schema changes, fixtures, or writes on dppms. */
@EnabledIfEnvironmentVariable(named="SYNC_SOURCE_PASSWORD",matches=".+")
class MysqlSyncReaderIntegrationTest {
    private MysqlSyncReader reader() {
        return new MysqlSyncReader(new ExternalDataSourceApi() {
            public List<Info> list(){return List.of();}
            public Long save(Save command){throw new UnsupportedOperationException("read-only test");}
            public Connection openReadOnly(Long id)throws SQLException {
                var c=DriverManager.getConnection("jdbc:mysql://10.210.0.11:3306/dppms?connectTimeout=10000&socketTimeout=60000&serverTimezone=Asia/Shanghai",
                        "root",System.getenv("SYNC_SOURCE_PASSWORD"));
                c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);c.setAutoCommit(false);
                return c;
            }
        });
    }
    @Test void readsBothEhrTablesAsOneSnapshotAndConvertsFields()throws Exception {
        var definition=EhrSyncTemplate.create(1L);
        var snapshot=reader().read(definition,null,true);
        assertEquals(2,snapshot.objects().size());
        assertTrue(snapshot.objects().stream().allMatch(s->!s.rows().isEmpty()));
        var rows=new SyncFieldMapper().transform(definition,snapshot,List.of());
        assertEquals(snapshot.objects().stream().mapToInt(s->s.rows().size()).sum(),rows.size());
        assertTrue(rows.stream().allMatch(r->r.fields().get("code")!=null && r.fields().get("status")!=null));
        System.out.println("EHR_SOURCE_READ companies="+snapshot.objects().get(0).rows().size()
                +" departments="+snapshot.objects().get(1).rows().size());
    }
    @Test void boundedSourceReadFailsInsteadOfReturningTruncatedSnapshot() {
        assertThrows(IllegalArgumentException.class,()->reader().read(EhrSyncTemplate.create(1L).toBuilder().maxRows(1).build(),null,true));
    }
    @Test void customSqlReadsAndBindsWithoutChangingTheSource()throws Exception {
        var d=EhrSyncTemplate.create(1L);
        var companies=d.sources().get(0).toBuilder().readMode("SQL")
                .sql("SELECT compID,compCode,compName,isDisabled FROM ehr_company WHERE compCode=:code")
                .parameters(Map.of("code","001")).build();
        var snapshot=reader().read(d.toBuilder().sources(List.of(companies)).build(),null,true);
        assertEquals(1,snapshot.objects().getFirst().rows().size());
    }
    @Test void completeEmptySelectionIsDifferentFromAReadFailure()throws Exception {
        var d=EhrSyncTemplate.create(1L);
        var companies=d.sources().getFirst().toBuilder()
                .filters(List.of(new SyncDefinition.Filter("compID","IN",List.of()))).build();
        assertTrue(reader().read(d.toBuilder().sources(List.of(companies)).build(),null,true).objects().getFirst().rows().isEmpty());
    }
    @Test void incrementalWindowIsAppliedOutsideConfiguredSqlAndUsesStableKeyOrdering()throws Exception {
        var d=EhrSyncTemplate.create(1L);
        var source=d.sources().getFirst().toBuilder().readMode("SQL").sourceKey("id").updatedAt("changed_at")
                .sql("SELECT 2 AS id, CAST('2000-01-01 00:00:00' AS DATETIME) AS changed_at UNION ALL SELECT 1, CAST('2000-01-01 00:00:00' AS DATETIME)")
                .parameters(Map.of()).build();
        var definition=d.toBuilder().mode("INCREMENTAL").sources(List.of(source)).build();
        var snapshot=reader().read(definition,java.time.LocalDateTime.of(2000,1,1,0,0),false);
        var rows=snapshot.objects().getFirst().rows();
        assertEquals(List.of("1","2"),rows.stream().map(r->r.get("id").toString()).toList());
        assertTrue(reader().read(definition,java.time.LocalDateTime.now().minusDays(1),false)
                .objects().getFirst().rows().isEmpty());
    }
    @Test void regressingSourceClockCannotMoveCheckpointBackwards() {
        assertThrows(IllegalArgumentException.class,()->reader().read(EhrSyncTemplate.create(1L),
                java.time.LocalDateTime.now().plusYears(1),false));
    }
}
