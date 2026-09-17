package cn.iocoder.yudao.module.pms.workflow.listener;

import cn.iocoder.yudao.module.pms.workflow.spi.OaTodoPort;
import cn.iocoder.yudao.module.pms.workflow.spi.dto.OaTodoCommand;
import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.transaction.TransactionSystemException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OaTaskListenerTest {

    private OaTaskListener listener(OaTodoPort port) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        if (port != null) beans.registerSingleton("oaTodoPort", port);
        return new OaTaskListener(beans.getBeanProvider(OaTodoPort.class));
    }

    private DelegateTask task(String event) {
        DelegateTask task = mock(DelegateTask.class);
        when(task.getEventName()).thenReturn(event);
        when(task.getId()).thenReturn("task-17");
        return task;
    }

    @Test
    void missingIntegrationDoesNotPreventWorkflowEvents() {
        OaTaskListener listener = listener(null);
        assertDoesNotThrow(() -> listener.notify(task("create")));
        assertDoesNotThrow(() -> listener.notify(task("complete")));
    }

    @Test
    void mapsCreationAndCompletesUsingTheSameLegacyBusinessKey() {
        OaTodoPort port = mock(OaTodoPort.class);
        OaTaskListener listener = listener(port);
        DelegateTask task = task("create");
        when(task.getVariable("businessKey")).thenReturn("project-42");
        when(task.getVariable("processUrl")).thenReturn("/workflow/process/9");
        when(task.getName()).thenReturn("审批");
        when(task.getAssignee()).thenReturn("user-5");
        when(task.getProcessInstanceId()).thenReturn("process-9");
        when(task.getProcessDefinitionId()).thenReturn("definition-2");
        listener.notify(task);
        ArgumentCaptor<OaTodoCommand> command = ArgumentCaptor.forClass(OaTodoCommand.class);
        verify(port).pushTodo(command.capture());
        assertEquals("project-42", command.getValue().getBusinessKey());
        assertEquals("审批", command.getValue().getTitle());
        assertEquals("待办任务：审批", command.getValue().getContent());
        assertEquals("user-5", command.getValue().getHandlerUserId());
        assertEquals("process-9", command.getValue().getProcessInstanceId());
        assertEquals("definition-2", command.getValue().getBusinessType());
        assertEquals("/workflow/process/9", command.getValue().getProcessUrl());
        when(task.getEventName()).thenReturn("complete");
        listener.notify(task);
        verify(port).completeTodo("project-42");
        verify(port, never()).completeTodo("task-17");
    }

    @Test
    void fallsBackToTaskIdWhenTheProcessDoesNotProvideABusinessKey() {
        OaTodoPort port = mock(OaTodoPort.class);
        OaTaskListener listener = listener(port);
        DelegateTask task = task("create");
        listener.notify(task);
        ArgumentCaptor<OaTodoCommand> command = ArgumentCaptor.forClass(OaTodoCommand.class);
        verify(port).pushTodo(command.capture());
        assertEquals("task-17", command.getValue().getBusinessKey());
        when(task.getEventName()).thenReturn("complete");
        listener.notify(task);
        verify(port).completeTodo("task-17");
    }

    @Test
    void catchesCallAndTransactionCompletionFailures() {
        OaTodoPort port = mock(OaTodoPort.class);
        doThrow(new TransactionSystemException("commit failed")).when(port).pushTodo(any());
        doThrow(new IllegalStateException("OA unavailable")).when(port).completeTodo("task-17");
        OaTaskListener listener = listener(port);
        assertDoesNotThrow(() -> listener.notify(task("create")));
        assertDoesNotThrow(() -> listener.notify(task("complete")));
        verify(port).pushTodo(any());
        verify(port).completeTodo("task-17");
    }

    @Test
    void ignoresEventsThatDoNotCreateOrCompleteAnOaTodo() {
        OaTodoPort port = mock(OaTodoPort.class);
        listener(port).notify(task("assignment"));
        verifyNoInteractions(port);
    }
}
