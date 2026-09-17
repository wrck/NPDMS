package cn.iocoder.yudao.module.pms.integration.extsystem.service;

import cn.iocoder.yudao.module.pms.integration.extsystem.exception.IntegrationException;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.oa.OaTodoRequest;
import cn.iocoder.yudao.module.pms.workflow.spi.OaTodoPort;
import cn.iocoder.yudao.module.pms.workflow.spi.dto.OaTodoCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OaTodoPortAdapterTest {

    private OaTodoCommand command() {
        return OaTodoCommand.builder().title("审批").content("待审批")
                .handlerUserId("user-5").processInstanceId("process-9")
                .businessKey("project-42").processUrl("/workflow/process/9")
                .businessType("definition-2").build();
    }

    @Test
    void preservesEveryRequestFieldAndCompletionKey() {
        OaIntegrationService service = mock(OaIntegrationService.class);
        when(service.pushTodo(any())).thenReturn(true);
        when(service.completeTodo("project-42")).thenReturn(true);
        OaTodoPortAdapter adapter = new OaTodoPortAdapter(service);
        adapter.pushTodo(command());
        ArgumentCaptor<OaTodoRequest> request = ArgumentCaptor.forClass(OaTodoRequest.class);
        verify(service).pushTodo(request.capture());
        OaTodoRequest actual = request.getValue();
        assertEquals("审批", actual.getTitle());
        assertEquals("待审批", actual.getContent());
        assertEquals("user-5", actual.getHandlerUserId());
        assertEquals("process-9", actual.getProcessInstanceId());
        assertEquals("project-42", actual.getBusinessKey());
        assertEquals("/workflow/process/9", actual.getProcessUrl());
        assertEquals("definition-2", actual.getBusinessType());
        adapter.completeTodo("project-42");
        verify(service).completeTodo("project-42");
    }

    @Test
    void doesNotTurnFalseServiceResultsIntoSuccessfulVoidCalls() {
        OaTodoPortAdapter adapter = new OaTodoPortAdapter(mock(OaIntegrationService.class));
        assertThrows(IntegrationException.class, () -> adapter.pushTodo(command()));
        assertThrows(IntegrationException.class, () -> adapter.completeTodo("project-42"));
    }

    @Test
    void configuresIndependentTransactionsAndKeepsOnlyControlledFailureLogs() throws Exception {
        AnnotationTransactionAttributeSource source = new AnnotationTransactionAttributeSource();
        for (var method : OaTodoPort.class.getMethods()) {
            TransactionAttribute attribute = source.getTransactionAttribute(method, OaTodoPortAdapter.class);
            assertNotNull(attribute);
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, attribute.getPropagationBehavior());
            assertFalse(attribute.rollbackOn(new IntegrationException("oa", "remote failure")));
            assertTrue(attribute.rollbackOn(new IllegalStateException("persistence failure")));
        }
    }

    @Test
    void controlledFailureCommitsTheAdapterScopeBeforePropagating() {
        OaIntegrationService service = mock(OaIntegrationService.class);
        IntegrationException failure = new IntegrationException("oa", "remote failure");
        when(service.pushTodo(any())).thenThrow(failure);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        OaTodoPort proxy = proxy(service, manager);
        assertSame(failure, assertThrows(IntegrationException.class, () -> proxy.pushTodo(command())));
        verify(manager).commit(status);
        verify(manager, never()).rollback(any());
    }

    @Test
    void commitFailuresRemainVisibleToTheListenerCallingOutsideTheProxy() {
        OaIntegrationService service = mock(OaIntegrationService.class);
        when(service.pushTodo(any())).thenReturn(true);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        doThrow(new TransactionSystemException("database commit failed")).when(manager).commit(status);
        OaTodoPort proxy = proxy(service, manager);
        assertThrows(TransactionSystemException.class, () -> proxy.pushTodo(command()));
    }

    private OaTodoPort proxy(OaIntegrationService service, PlatformTransactionManager manager) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(new OaTodoPortAdapter(service));
        factory.addAdvice(interceptor);
        return (OaTodoPort) factory.getProxy();
    }
}
