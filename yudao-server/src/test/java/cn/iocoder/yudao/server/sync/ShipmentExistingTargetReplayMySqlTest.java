package cn.iocoder.yudao.server.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.sync.SyncRunService;
import cn.iocoder.yudao.module.pms.integration.sync.SyncTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Test-only replay of the already verified isolated 4m target. Never creates,
 * clears, seeds or reconfigures databases. The ordinary synchronization service
 * may append run/checkpoint metadata; all business/evidence counts must stay fixed.
 * Original pre-reconciliation assertions remain selectable unchanged. The separate
 * reconciled entry requires a specific accepted run ID and fixed evidence counts. */
@EnabledIfSystemProperty(named="pms.sync.mysql.existingTargetReplay", matches="true")
class ShipmentExistingTargetReplayMySqlTest extends ShipmentFullChainMySqlTest {
    private static final String VERIFIED_RECONCILIATION_RUN = "2109020794133790721";
    private Map<String,Long> before;
    private boolean reconciled;


    @Override @BeforeEach void setup() throws Exception {
        assertEquals("npdms_sync_full_chain_20261009_reusev74m", DB);
        assertEquals("npdms_sync_full_chain_20261009_full4mdensity", REUSE_SOURCE_DB);
        assertEquals(4_000_000, ROWS);
        assertEquals(81522, CONTRACTS);
        assertEquals(120639, SHIPMENT_CONTRACTS);
        assertEquals(400000, PACKAGES);
        assertTrue(PRODUCTION_SQL_PARSER_CACHE);
        assertTrue(COMPRESS_BINLOG && COMPRESS_EVIDENCE_TABLES && COMPRESS_BINDING_TABLE);
        assertEquals(4, COMPRESSED_PAGE_KIB);
        jdbc = new JdbcTemplate(new DriverManagerDataSource(URL,"root",""));
        assertEquals(DB, jdbc.queryForObject("SELECT DATABASE()",String.class));
        assertEquals(1, jdbc.queryForObject("SELECT @@global.log_bin",Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT @@global.sync_binlog",Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT @@global.innodb_flush_log_at_trx_commit",Integer.class));
        assertEquals("ROW", jdbc.queryForObject("SELECT @@global.binlog_format",String.class));
        assertEquals("FULL", jdbc.queryForObject("SELECT @@global.binlog_row_image",String.class));
        long expectedPool = Long.parseLong(System.getProperty("pms.sync.mysql.existingTargetReplay.expectedBufferPoolBytes"));
        assertEquals(134217728L, expectedPool, "Only the restored and audited 128 MiB configuration is permitted");
        assertEquals(expectedPool, jdbc.queryForObject("SELECT @@global.innodb_buffer_pool_size",Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM int_batch_JOB_EXECUTION WHERE STATUS IN ('STARTING','STARTED','STOPPING')",Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_task WHERE active_run_id IS NOT NULL",Long.class));
        var taskIds = jdbc.queryForList("SELECT id FROM int_sync_task WHERE tenant_id=1 AND deleted=0 AND name='Full shipment chain'",Long.class);
        assertEquals(1, taskIds.size());
        String acceptedRun = System.getProperty("pms.sync.mysql.existingTargetReplay.acceptedReconciliationRun", "");
        assertTrue(acceptedRun.isEmpty() || VERIFIED_RECONCILIATION_RUN.equals(acceptedRun),
            "Only the independently audited reconciliation run may select the new baseline");
        reconciled = !acceptedRun.isEmpty();
        before = counts();
        assertEquals(Map.of("com_contract",81522L,"com_shipment_contract_reference",120639L,
            "com_shipment_package",400001L,"ast_device",4000000L,"ast_device_shipment",4001000L,
            "int_sync_binding",4723801L,"plt_migration_source_record",reconciled ? 4845442L : 4844442L,
            "plt_external_key_mapping",reconciled ? 8968082L : 8966082L,"plt_migration_issue",0L,"plt_outbox_event",0L), before);
        root=Path.of("").toAbsolutePath();
        while(root!=null && !Files.isDirectory(root.resolve("sql/migrations")))root=root.getParent();
        assertNotNull(root);
        TenantContextHolder.setTenantId(1L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new cn.iocoder.yudao.framework.security.core.LoginUser().setId(1L).setTenantId(1L),null,List.of()));
        configureSqlParserProfile("beforeExistingTargetContext");
        context=new AnnotationConfigApplicationContext(Config.class);
        configureSqlParserProfile("afterExistingTargetContext");
        tasks=context.getBean(SyncTaskService.class);
        runs=context.getBean(SyncRunService.class);
        ids.put("shipment-complete",taskIds.getFirst());
        print("EXISTING_REPLAY_PREPARATION",Map.of("database",DB,"bufferPoolBytes",expectedPool,"counts",before));
    }

    /** Prevent inherited full-migration test from ever running against this target. */
    @Override @Test void completeContractPackageDeviceShipmentMigrationAndSync() {
        fail("Forbidden on an existing target. Select an explicit replay-only method.");
    }

    @Test void replayExistingTargetWithoutGrowth() throws Exception {
        assertFalse(reconciled, "Original acceptance retains its original evidence baseline");
        verifyStableReplay("EXISTING_UNCHANGED_REPLAY");
    }

    @Test void replayReconciledTargetWithoutGrowth() throws Exception {
        assertTrue(reconciled, "Explicit acceptance of the audited reconciliation run is required");
        verifyStableReplay("RECONCILED_UNCHANGED_REPLAY");
    }

    private void verifyStableReplay(String phase) throws Exception {
        var result=run(phase,"shipment-complete");
        assertEquals(4723801L, ((Number)result.get("rows")).longValue());
        assertEquals(before, counts(), "Replay must not add business rows, bindings, evidence or issues");
        verify(4001000L,400001);
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_task WHERE active_run_id IS NOT NULL",Long.class));
        print("EXISTING_REPLAY_RESULT",Map.of("status","PASS","task",result,"counts",counts()));
    }
}
