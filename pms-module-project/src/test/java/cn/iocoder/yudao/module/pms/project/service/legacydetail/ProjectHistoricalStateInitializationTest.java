package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectHistoricalStateInitialization.*;

class ProjectHistoricalStateInitializationTest {
    private static final LocalDateTime CLOSE = LocalDateTime.of(2024, 12, 23, 9, 46, 31);
    private static final State ACTIVE = new State("ACTIVE", "S4", null);

    @Test void normalClosureUsesOriginalTimeAndAdjudicatedTerminalStage() {
        assertEquals(new State("NORMAL_CLOSED", "S6", CLOSE), resolve("100", CLOSE, ACTIVE, false));
    }
    @Test void exceptionClosurePreservesStageAndOriginalCloseTime() {
        assertEquals(new State("EXCEPTION_CLOSED", "S4", CLOSE), resolve("20", CLOSE, ACTIVE, false));
    }
    @Test void assignmentStatesDoNotPretendToAdvanceLifecycle() {
        assertEquals(ACTIVE, resolve("31", null, ACTIVE, false));
        assertEquals(ACTIVE, resolve("32", null, ACTIVE, false));
        assertEquals(ACTIVE, resolve("32", CLOSE, ACTIVE, false));
        assertEquals(ACTIVE, resolve("31", CLOSE, ACTIVE, false));
    }
    @Test void sourceCreatedStateInitializesS0WithoutClaimingClosure() {
        assertEquals(new State("ACTIVE", "S0", null), resolve("30", null, ACTIVE, false));
        assertEquals(new State("ACTIVE", "S0", null), resolve("30", CLOSE, ACTIVE, false));
    }
    @Test void missingHistoricalClosureTimeRemainsUnknownAndReplayStillProtectsIt() {
        var normal=new State("NORMAL_CLOSED", "S6", null);
        var exception=new State("EXCEPTION_CLOSED", "S4", null);
        assertEquals(normal,resolve("100", null, ACTIVE, false));
        assertEquals(exception,resolve("20", null, ACTIVE, false));
        assertEquals(normal,resolve("100", null, normal, true));
        assertEquals(exception,resolve("20", null, exception, true));
        assertThrows(IllegalArgumentException.class, () -> resolve("100", CLOSE, normal, true));
        assertThrows(IllegalArgumentException.class, () -> resolve("20", CLOSE, exception, true));
    }
    @Test void otherSourceStatesRequireAdjudication() {
        assertThrows(IllegalArgumentException.class, () -> resolve("40", CLOSE, ACTIVE, false));
        assertThrows(IllegalArgumentException.class, () -> resolve(null, CLOSE, ACTIVE, false));
    }
    @Test void securityServiceTypesUseTheirOwnConfirmedStateMeanings() {
        for (String type:new String[]{"afss","afxx"}) {
            for (String state:new String[]{"10","31"}) assertEquals(new State("ACTIVE","S0",null),resolve(type,state,CLOSE,ACTIVE,false));
            assertEquals(new State("ACTIVE","S4",null),resolve(type,"32",CLOSE,ACTIVE,false));
            assertEquals(new State("ACTIVE","S5",null),resolve(type,"40",CLOSE,ACTIVE,false));
            assertEquals(new State("ACTIVE","S6",null),resolve(type,"50",CLOSE,ACTIVE,false));
            assertEquals(new State("NORMAL_CLOSED","S6",null),resolve(type,"100",null,ACTIVE,false));
            assertEquals(new State("NORMAL_CLOSED","S6",CLOSE),resolve(type,"100",CLOSE,ACTIVE,false));
            assertThrows(IllegalArgumentException.class,()->resolve(type,"20",CLOSE,ACTIVE,false));
        }
        assertThrows(IllegalArgumentException.class,()->resolve("other","100",CLOSE,ACTIVE,false));
        assertThrows(IllegalArgumentException.class,()->resolve("10","50",CLOSE,ACTIVE,false));
    }
    @Test void initialMigrationCannotTakeOverCurrentClosure() {
        assertThrows(IllegalArgumentException.class, () -> resolve("100", CLOSE,
                new State("EXCEPTION_CLOSED", "S4", CLOSE), false));
        assertThrows(IllegalArgumentException.class, () -> resolve("100", CLOSE,
                new State("ACTIVE", "S4", CLOSE), false));
    }
    @Test void repeatRequiresUnchangedInitializedState() {
        var closed = new State("NORMAL_CLOSED", "S6", CLOSE);
        assertEquals(closed, resolve("100", CLOSE, closed, true));
        assertThrows(IllegalArgumentException.class, () -> resolve("32", null, closed, true));
        assertThrows(IllegalArgumentException.class, () -> resolve("100", CLOSE.plusSeconds(1), closed, true));
    }
    @Test void invalidCurrentStageCannotBeMigrated() {
        assertThrows(IllegalArgumentException.class, () -> resolve("100", CLOSE,
                new State("ACTIVE", "unknown", null), false));
        assertThrows(IllegalArgumentException.class, () -> resolve("100", CLOSE,
                new State("ACTIVE", null, null), false));
    }
}
