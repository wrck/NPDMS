package cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArrivalAcceptanceStateMachineTest {

    private final ArrivalAcceptanceStateMachine machine = new ArrivalAcceptanceStateMachine();

    @Test
    void followsTheLockedPositiveBatchPath() {
        assertEquals(ArrivalAcceptanceStateMachine.DIFFERENCE_PENDING, machine.submit(true, false));
        assertEquals(ArrivalAcceptanceStateMachine.PARTIALLY_ACCEPTED, machine.submit(false, false));
        assertEquals(ArrivalAcceptanceStateMachine.ACCEPTED, machine.submit(false, true));
        assertEquals(ArrivalAcceptanceStateMachine.CONFIRMED,
                machine.confirm(ArrivalAcceptanceStateMachine.ACCEPTED));
    }
}
