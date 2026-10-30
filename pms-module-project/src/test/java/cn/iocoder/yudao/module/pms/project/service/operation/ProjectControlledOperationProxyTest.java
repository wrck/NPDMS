package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectControlledOperationProxyTest {
    static class Boundary {
        final AtomicInteger calls = new AtomicInteger();
        @ProjectExecutionControlled(operation = "SOL.SITE_SURVEY.CONFIRM")
        public ProjectOperationResult confirm(ProjectOperationCommand command) { calls.incrementAndGet(); return null; }
        public void independent() { calls.incrementAndGet(); }
    }
    @Test @SuppressWarnings("unchecked") void actualProxyUsesExecutorAndLeavesUnannotatedMethodIndependent() {
        var executor = mock(ProjectControlledOperationExecutor.class);
        ObjectProvider<ProjectControlledOperationExecutor> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(executor);
        var target = new Boundary(); var factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvisor(new ProjectControlledOperationConfiguration().projectControlledOperationAdvisor(provider));
        var proxy = (Boundary) factory.getProxy();
        var request = new ProjectOperationCommand(1L,"TASK",2L,null,null,null,null,null,"key");
        when(executor.execute(eq("SOL.SITE_SURVEY.CONFIRM"),eq(1),eq(request),any())).thenAnswer(call ->
                ((ProjectControlledOperationExecutor.Work)call.getArgument(3)).invoke(request));
        proxy.confirm(request); proxy.independent();
        assertEquals(2,target.calls.get()); verify(executor,times(1)).execute(anyString(),anyInt(),any(),any());
    }
    @Test void nestedScopesDoNotLeakOrActAsGlobalSkipFlag() {
        var outer = new ProjectVerifiedOperationScope.Frame(1L,2L,3L,"SOL","A","A.SAVE","11",null);
        var inner = new ProjectVerifiedOperationScope.Frame(1L,2L,3L,"SOL","B","B.SAVE","12",null);
        try (var first = ProjectVerifiedOperationScope.open(outer)) {
            assertSame(outer,ProjectVerifiedOperationScope.current());
            try (var second = ProjectVerifiedOperationScope.open(inner)) { assertSame(inner,ProjectVerifiedOperationScope.current()); }
            assertSame(outer,ProjectVerifiedOperationScope.current());
            assertFalse(ProjectVerifiedOperationScope.matches(1L,2L,3L,"SOL","B",null));
        }
        assertNull(ProjectVerifiedOperationScope.current());
    }
    @Test void allFourteenPublishedControlledEntriesArePublicAndAnnotated() {
        long count = java.util.Arrays.stream(ProjectControlledBusinessOperations.class.getMethods())
                .filter(method -> method.isAnnotationPresent(ProjectExecutionControlled.class)).count();
        assertEquals(14,count);
    }
}
