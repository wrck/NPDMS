package cn.iocoder.yudao.server.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.sync.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated test orchestration only. Never initializes or clears the existing target. */
@EnabledIfSystemProperty(named="pms.sync.mysql.interruptedRetry",matches="true")
class BaselineInterruptedRetryMySqlTest {
    @Test void recoverAndRetryExistingRun() throws Exception {
        assertEquals("npdms_sync_full_chain_20261009_baselineempty20261014",ShipmentFullChainMySqlTest.DB);
        assertEquals(4_000_000,ShipmentFullChainMySqlTest.ROWS);
        assertEquals(81522,ShipmentFullChainMySqlTest.CONTRACTS);
        assertEquals(120639,ShipmentFullChainMySqlTest.SHIPMENT_CONTRACTS);
        assertEquals(400000,ShipmentFullChainMySqlTest.PACKAGES);
        assertEquals("npdms_sync_full_chain_20261009_full4mdensity",ShipmentFullChainMySqlTest.REUSE_SOURCE_DB);
        assertEquals("CONTINUE_WITH_PERFORMANCE_RECORDED",System.getProperty("pms.sync.mysql.retryDecision"),
                "Explicit operator decision required before any recovery or retry");
        long priorId=2110405569336590338L,taskId=2110405565377167361L;
        String requestKey=System.getProperty("pms.sync.mysql.retryRequestKey","");
        assertTrue(requestKey.matches("baseline-retry-[A-Za-z0-9_-]{1,40}"));
        var fixture=new ShipmentFullChainMySqlTest();
        try {
            fixture.suiteStart=System.nanoTime();
            fixture.root=Path.of("").toAbsolutePath();
            while(!Files.isDirectory(fixture.root.resolve("sql/migrations"))) {
                fixture.root=fixture.root.getParent();assertNotNull(fixture.root);
            }
            fixture.jdbc=new JdbcTemplate(new DriverManagerDataSource(ShipmentFullChainMySqlTest.URL,"root",""));
            assertEquals(ShipmentFullChainMySqlTest.DB,fixture.jdbc.queryForObject("SELECT DATABASE()",String.class));
            TenantContextHolder.setTenantId(1L);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    new cn.iocoder.yudao.framework.security.core.LoginUser().setId(1L).setTenantId(1L),null,List.of()));
            fixture.configureSqlParserProfile("beforeContext");
            fixture.context=new AnnotationConfigApplicationContext(ShipmentFullChainMySqlTest.Config.class);
            fixture.configureSqlParserProfile("afterContext");
            fixture.verifyStorageSession(new JdbcTemplate(fixture.context.getBean(DataSource.class)),"businessPool");
            fixture.tasks=fixture.context.getBean(SyncTaskService.class);
            fixture.runs=fixture.context.getBean(SyncRunService.class);
            var prior=fixture.runs.required(priorId);
            assertEquals(Long.valueOf(taskId),prior.getTaskId());assertEquals(Integer.valueOf(120639),prior.getReadCount());
            assertTrue(Set.of("APPLYING","FAILED").contains(prior.getStatus()));
            var definition=JsonUtils.parseObject(prior.getConfigSnapshot(),SyncDefinition.class);
            assertEquals("RESTART_ALL",definition.effectiveRestartPolicy());
            fixture.print("INTERRUPTED_RETRY_PRIOR",prior);
            assertEquals(1L,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_task WHERE tenant_id=1",Long.class));
            fixture.runs.maintain();
            assertEquals("FAILED",fixture.runs.required(priorId).getStatus());
            assertNull(fixture.tasks.required(taskId).getActiveRunId());
            ShipmentFullChainMySqlTest.activePhase="INTERRUPTED_INITIAL_RETRY";
            long begin=System.nanoTime();String started=Instant.now().toString();
            long id=fixture.runs.start(new SyncRunService.Start(taskId,fixture.tasks.required(taskId).getVersion(),
                    requestKey,false,true,false,priorId,false));
            fixture.print("INTERRUPTED_RETRY_STARTED",Map.of("runId",id,"retryOf",priorId,"startedAt",started,
                    "restartPolicy","RESTART_ALL","freshEmptyTarget",false));
            long last=0;
            while(true) {
                var run=fixture.runs.required(id);String status=run.getStatus();
                if(Set.of("SUCCESS","FAILED","CANCELLED","PREVIEW_READY").contains(status)) {
                    assertEquals("SUCCESS",status,()->"Retry failed: "+JsonUtils.toJsonString(run));
                    long batchDeadline=System.nanoTime()+60_000_000_000L;
                    while(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM int_batch_JOB_EXECUTION WHERE STATUS IN ('STARTING','STARTED','STOPPING')",Long.class)>0) {
                        assertTrue(System.nanoTime()<batchDeadline,"Batch metadata did not finish");Thread.sleep(100);
                    }
                    fixture.print("FULL_CHAIN_TASK",Map.of("phase",ShipmentFullChainMySqlTest.activePhase,"runId",id,
                            "retryOf",priorId,"startedAt",started,"endedAt",Instant.now().toString(),
                            "seconds",(System.nanoTime()-begin)/1e9,"rows",run.getReadCount(),"summary",run.getSummaryJson()));
                    break;
                }
                if(System.nanoTime()-last>30_000_000_000L) {
                    fixture.print("FULL_CHAIN_PROGRESS",Map.of("phase",ShipmentFullChainMySqlTest.activePhase,"runId",id,
                            "rows",run.getReadCount(),"seconds",(System.nanoTime()-begin)/1e9));last=System.nanoTime();
                }
                Thread.sleep(250);
            }
            long verifyStart=System.nanoTime();fixture.verify(ShipmentFullChainMySqlTest.ROWS,ShipmentFullChainMySqlTest.PACKAGES);
            fixture.print("INTERRUPTED_RETRY_RESULT",Map.of("status","PASS_FUNCTIONAL_RETRY","runId",id,"retryOf",priorId,
                    "freshEmptyTarget",false,"counts",fixture.counts(),"verificationSeconds",(System.nanoTime()-verifyStart)/1e9,
                    "performanceAssessment","SEPARATE_RETRY_MEASUREMENT_NOT_FRESH_INITIAL_ACCEPTANCE"));
        } finally {fixture.close();}
    }
}
