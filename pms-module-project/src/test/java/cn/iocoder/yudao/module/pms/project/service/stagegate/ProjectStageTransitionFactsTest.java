package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectStageTransitionFactsTest {
    @Test
    void completionIsLimitedToTheFrozenTargetsEntryAndTheCurrentStageReference() {
        var current = new ProjectStageInstanceDO().setId(1L).setStageCode("S0").setStatus("ACTIVE").setVersion(2);
        var target = new ProjectStageInstanceDO().setId(2L).setStageCode("S4").setStatus("PENDING");
        var gate = new ProjectGateInstanceDO().setStageCode("S4").setGateType("ENTRY");
        var ref = new ProjectGateReferenceInstanceDO().setRefType("STATE").setRefCode("S0_COMPLETED");
        assertNotNull(ProjectStageTransitionFacts.completionForEntry(current, target, gate, ref, true));
        assertNull(ProjectStageTransitionFacts.completionForEntry(current, target, gate, ref, false));
        gate.setGateType("EXIT");
        assertNull(ProjectStageTransitionFacts.completionForEntry(current, target, gate, ref, true));
        gate.setGateType("ENTRY").setStageCode("S6");
        assertNull(ProjectStageTransitionFacts.completionForEntry(current, target, gate, ref, true));
        gate.setStageCode("S4");
        for (String code : new String[] {"S4_COMPLETED", "S6_COMPLETED", "UNKNOWN"}) {
            ref.setRefCode(code);
            assertNull(ProjectStageTransitionFacts.completionForEntry(current, target, gate, ref, true));
        }
        ref.setRefCode("S0_COMPLETED").setRefType("PROCESS");
        assertNull(ProjectStageTransitionFacts.completionForEntry(current, target, gate, ref, true));
        assertEquals("ACTIVE", current.getStatus());
        assertEquals(2, current.getVersion());
    }
}
