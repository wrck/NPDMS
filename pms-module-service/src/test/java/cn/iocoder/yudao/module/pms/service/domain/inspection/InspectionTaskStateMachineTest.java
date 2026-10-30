package cn.iocoder.yudao.module.pms.service.domain.inspection;

import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine.*;

class InspectionTaskStateMachineTest {
    @Test void offlineAndOnlineKeepTheirDifferentExecutionPrerequisites() {
        assertEquals("EXECUTING", next(new Transition("PREPARING", Action.START_OFFLINE, false, false, false, 0, false, false)));
        assertEquals("PRECHECK_PENDING", next(new Transition("PREPARING", Action.START_ONLINE, false, false, false, 0, false, false)));
        assertThrows(InspectionException.class, () -> next(new Transition("PRECHECK_PENDING", Action.EXECUTION_COMPLETE,
                true, false, false, 0, false, false)));
    }
    @Test void incompleteResultsCannotBecomeAUsableReport() {
        assertThrows(InspectionException.class, () -> next(new Transition("EXECUTING", Action.EXECUTION_COMPLETE,
                false, false, false, 0, false, false)));
        assertThrows(InspectionException.class, () -> next(new Transition("REPORT_PENDING", Action.REPORT_READY,
                false, true, true, 0, false, false)));
    }
    @Test void noFindingsStillRequireExplicitAnnotationConfirmation() {
        assertThrows(InspectionException.class, () -> next(new Transition("ANNOTATION_PENDING", Action.ANNOTATIONS_COMPLETE,
                true, true, false, 0, false, false)));
        assertEquals("CLOSED", next(new Transition("ANNOTATION_PENDING", Action.ANNOTATIONS_COMPLETE,
                true, true, true, 0, false, false)));
    }
    @Test void openTrackingCannotBeReplacedByAnArchiveExplanation() {
        assertEquals("TRACKING", next(new Transition("ANNOTATION_PENDING", Action.ANNOTATIONS_COMPLETE,
                true, true, true, 1, false, false)));
        assertThrows(InspectionException.class, () -> next(new Transition("TRACKING", Action.ISSUES_CLOSED,
                true, true, true, 1, true, true)));
    }
    @Test void archiveRequiresBothApplicationAndCustomerReferenceSuccess() {
        assertThrows(InspectionException.class, () -> next(new Transition("CLOSED", Action.ARCHIVE,
                true, true, true, 0, false, true)));
        assertThrows(InspectionException.class, () -> next(new Transition("CLOSED", Action.ARCHIVE,
                true, true, true, 0, true, false)));
        assertEquals("ARCHIVED", next(new Transition("CLOSED", Action.ARCHIVE,
                true, true, true, 0, true, true)));
    }
    @Test void archivedHistoryCannotBeReexecutedOrCancelled() {
        for (Action action : Action.values()) assertThrows(InspectionException.class, () -> next(new Transition(
                "ARCHIVED", action, true, true, true, 0, true, true)));
    }
}
