package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import static org.junit.jupiter.api.Assertions.*;

class BusinessEntityIdentityResolverTest {
    @BusinessEntityService(ownerModule="SOL", entityType="requirementAnalysis", nativeEntityType="REQUIREMENT_ANALYSIS")
    static class NativeOwner extends BusinessOperationDispatcherDiscoveryTest.Owner {}
    @BusinessEntityService(ownerModule="SOL", entityType="otherAlias", nativeEntityType="REQUIREMENT_ANALYSIS")
    static class ConflictingOwner extends BusinessOperationDispatcherDiscoveryTest.Owner {}

    @Test void realBeanMetadataResolvesWithoutInitializingOwnerAndPreservesExactTarget() {
        var beans = new DefaultListableBeanFactory();
        var definition = new RootBeanDefinition(NativeOwner.class);
        definition.setInstanceSupplier(() -> { throw new AssertionError("mapping must not initialize owner"); });
        beans.registerBeanDefinition("owner", definition);
        var resolver = new BusinessEntityIdentityResolver(beans);
        var target = new EntityDataRef(new EntityRef(23L,"SOL","requirementAnalysis",9007199254740993L),9007199254740994L);
        var nativeTarget = resolver.nativeRef(target);
        assertEquals(new EntityRef(23L,"SOL","REQUIREMENT_ANALYSIS",9007199254740993L), nativeTarget.entity());
        assertEquals(target.revisionId(),nativeTarget.revisionId());
        assertTrue(resolver.hasMapping("SOL","requirementAnalysis"));
        assertFalse(beans.containsSingleton("owner"));
        assertFalse(resolver.hasMapping("SOL","RequirementAnalysis"));
        assertEquals(new EntityRef(23L,"SOL","RequirementAnalysis",3L),resolver.nativeRef(new EntityRef(23L,"SOL","RequirementAnalysis",3L)));
    }
    @Test void duplicateNativeIdentityFailsClosed() {
        var beans = new DefaultListableBeanFactory();
        beans.registerBeanDefinition("first",new RootBeanDefinition(NativeOwner.class));
        beans.registerBeanDefinition("second",new RootBeanDefinition(ConflictingOwner.class));
        var resolver = new BusinessEntityIdentityResolver(beans);
        var error=assertThrows(BusinessContractException.class,()->resolver.hasMapping("SOL","requirementAnalysis"));
        assertEquals("IDENTITY_MAPPING_CONFLICT",error.getErrorCode());
    }
}
