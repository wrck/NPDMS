package cn.iocoder.yudao.server.sync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Test-only entry point. Calls the byte-identical V7 fixture without changing its implementation. */
@EnabledIfSystemProperty(named="pms.sync.mysql.fullChain",matches="true")
class BaselineInitialMigrationMySqlTest {
    @Test void emptyTargetInitialMigrationOnly() throws Exception {
        assertEquals(4_000_000, ShipmentFullChainMySqlTest.ROWS);
        assertEquals(81522, ShipmentFullChainMySqlTest.CONTRACTS);
        assertEquals(120639, ShipmentFullChainMySqlTest.SHIPMENT_CONTRACTS);
        assertEquals(400000, ShipmentFullChainMySqlTest.PACKAGES);
        assertFalse(ShipmentFullChainMySqlTest.REUSE_SOURCE_DB.isBlank());
        String reviewName=System.getProperty("pms.sync.mysql.fullChain.initialReviewFile","");
        assertFalse(reviewName.isBlank());
        Path review=Path.of(reviewName);
        assertTrue(review.isAbsolute());
        assertFalse(Files.exists(review),"No stale decision may authorize verification");
        var fixture=new ShipmentFullChainMySqlTest();
        try {
            fixture.setup();
            var all=new ArrayList<Map<String,Object>>();
            long start=System.nanoTime();
            for(String key:ShipmentFullChainMySqlTest.KEYS) all.add(fixture.run("INITIAL_MIGRATION",key));
            fixture.metrics.put("initialMigrationSeconds",(System.nanoTime()-start)/1e9);
            fixture.print("FULL_CHAIN_INITIAL_REVIEW_REQUIRED",Map.of("run",all.getLast(),"reviewFile",review.toString()));
            long deadline=System.nanoTime()+60_000_000_000L;
            while(!Files.exists(review)) {
                assertTrue(System.nanoTime()<deadline,"No performance approval; correctness scans withheld");
                Thread.sleep(100);
            }
            assertEquals("CONTINUE_INITIAL_VERIFICATION",Files.readString(review).trim(),"Performance gate denied verification");
            start=System.nanoTime();
            fixture.verify(ShipmentFullChainMySqlTest.ROWS,ShipmentFullChainMySqlTest.PACKAGES);
            fixture.metrics.put("initialVerificationSeconds",(System.nanoTime()-start)/1e9);
            fixture.print("FULL_CHAIN_INITIAL_ONLY_RESULT",Map.of("status","PASS_INITIAL_ONLY","runs",all,
                    "counts",fixture.counts(),"timing",fixture.metrics,"laterPhasesExecuted",false,
                    "performanceAssessment","OPERATOR_REVIEWED_EQUIVALENT_WORK"));
        } finally {
            fixture.close();
        }
    }
}
