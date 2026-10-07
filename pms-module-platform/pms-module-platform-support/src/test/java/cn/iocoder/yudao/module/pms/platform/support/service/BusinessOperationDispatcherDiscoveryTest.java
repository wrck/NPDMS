package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessOperationDispatcherDiscoveryTest {
    @Test void discoveredOwnerUsesTheInheritedExecutionSequence() {
        var persistence = mock(BusinessEntityPersistenceRegistry.class);
        var defaults = mock(DefaultBusinessApplicationService.class);
        var owner = new Owner();
        var dispatcher = new BusinessOperationDispatcher(persistence, defaults, List.of(owner));
        var receipt = dispatcher.dispatch(request());
        assertEquals(List.of("caller", "validate", "authorize", "lock", "command", "outcome"), owner.steps);
        assertEquals(ReceiptOutcome.SAVED, receipt.outcome());
        verifyNoInteractions(defaults);
    }

    @Test void inheritedIdentitySurvivesAnActualSpringProxy() {
        var persistence = mock(BusinessEntityPersistenceRegistry.class);
        var defaults = mock(DefaultBusinessApplicationService.class);
        var factory = new org.springframework.aop.framework.ProxyFactory(new Owner());
        factory.setProxyTargetClass(true);
        var owner = (Owner) factory.getProxy();
        assertTrue(org.springframework.aop.support.AopUtils.isCglibProxy(owner));
        var dispatcher = new BusinessOperationDispatcher(persistence, defaults, List.of(owner));
        assertEquals(ReceiptOutcome.SAVED, dispatcher.dispatch(request()).outcome());
        verifyNoInteractions(defaults);
    }

    @Test void duplicateOwnersFailDuringAssemblyRatherThanSilentlyReplacingOne() {
        var persistence = mock(BusinessEntityPersistenceRegistry.class);
        var ex = assertThrows(BusinessContractException.class, () -> new BusinessOperationDispatcher(
                persistence, mock(DefaultBusinessApplicationService.class), List.of(new Owner(), new Owner())));
        assertEquals("SERVICE_DECLARED_TWICE", ex.getErrorCode());
    }

    private static <T> T mock(Class<T> type) {return org.mockito.Mockito.mock(type,org.mockito.Mockito.withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));}

    private BusinessOperationRequest request() {
        return new BusinessOperationRequest("save", 1, EntityDataRef.current(new EntityRef(1L, "SOL", "requirementAnalysis", 3L)),
                "SOL", "requirementAnalysis", Map.of(), "key", 0L, OperationEntryKind.INDEPENDENT, null);
    }

    @BusinessEntityService(ownerModule = "SOL", entityType = "requirementAnalysis")
    static class Owner extends AbstractBusinessApplicationService<BaseBusinessEntity> {
        final List<String> steps = new ArrayList<>();
        Owner() { super(null, null); }
        protected ResolvedCaller resolveCaller(BusinessOperationRequest r) { steps.add("caller"); return new ResolvedCaller(1L, 2L, null); }
        protected void validateIdentityAndInput(ResolvedCaller c, BusinessOperationRequest r) { steps.add("validate"); }
        protected void authorizeAndCheckState(ResolvedCaller c, BusinessOperationRequest r) { steps.add("authorize"); }
        protected LockedAggregate<BaseBusinessEntity> lockAggregate(ResolvedCaller c, BusinessOperationRequest r) { steps.add("lock"); return new LockedAggregate<>(null, 0L); }
        protected BusinessOperationReceipt domainCommand(ResolvedCaller c, BusinessOperationRequest r, LockedAggregate<BaseBusinessEntity> a) {
            steps.add("command"); return new BusinessOperationReceipt(ReceiptOutcome.SAVED, r.targetRef().entity(), 1L, List.of(), null, null);
        }
        protected void recordOutcome(ResolvedCaller c, BusinessOperationRequest r, BusinessOperationReceipt result) { steps.add("outcome"); }
    }
    static class DerivedOwner extends Owner {}
}
