package cn.iocoder.yudao.module.pms.cutover.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverApprovalProjectOperationAdapterTest {
    private final CutoverProjectOperationAdapter tasks = mock(CutoverProjectOperationAdapter.class);
    private final CutoverApprovalProjectOperationAdapter approvals = new CutoverApprovalProjectOperationAdapter(tasks);

    @Test void retainsNativeTaskIdentityAndReplayWhileReturningApprovalProjection() {
        var command = new ProjectOperationCommand(9L, "TASK", 11L, null, null, null, null, null, "same-intent");
        var nativeResult = new ProjectOperationResult("CUT", "CUTOVER_TASK", "21", null, 1L, "fact:21", "CUTOVER_CREATED", null, true);
        when(tasks.invoke(CutoverProjectOperationAdapter.CREATE, command)).thenReturn(nativeResult);
        var result = approvals.invoke(CutoverApprovalProjectOperationAdapter.CREATE, command);
        assertEquals("CUTOVER_APPROVAL", result.objectType()); assertEquals("21", result.objectId());
        assertTrue(result.replayed()); assertEquals("fact:21", result.businessFactVersion());
        verify(tasks).invoke(CutoverProjectOperationAdapter.CREATE, command);
    }

    @Test void doesNotGrantCreateWhenNativeOwnerDeniedIt() {
        var context = new ProjectBusinessOperationAccessProvider.Context(7L, 2L, 9L, null);
        when(tasks.inspect(context)).thenReturn(new ProjectBusinessOperationAccessProvider.Access(Set.of(), null));
        assertTrue(approvals.inspect(context).permittedOperations().isEmpty());
        when(tasks.inspect(context)).thenReturn(new ProjectBusinessOperationAccessProvider.Access(Set.of(CutoverProjectOperationAdapter.CREATE), null));
        assertEquals(Set.of(CutoverApprovalProjectOperationAdapter.CREATE), approvals.inspect(context).permittedOperations());
        when(tasks.inspect(context)).thenThrow(new SecurityException("denied"));
        assertThrows(SecurityException.class, () -> approvals.inspect(context));
    }

    @Test void rejectsForeignOperationAndPropagatesNativeReplayAuthorizationFailure() {
        assertThrows(SecurityException.class, () -> approvals.invoke(CutoverProjectOperationAdapter.CREATE, null));
        verifyNoInteractions(tasks);
        doThrow(new SecurityException("denied")).when(tasks).authorizeReplay(CutoverProjectOperationAdapter.CREATE, null);
        assertThrows(SecurityException.class, () -> approvals.authorizeReplay(CutoverApprovalProjectOperationAdapter.CREATE, null));
    }
}
