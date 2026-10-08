package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import static org.junit.jupiter.api.Assertions.*;

class BusinessEntityIdentityResolverTest {
    @BusinessEntityService(ownerModule="SOL", entityType="requirementAnalysis", nativeEntityType="REQUIREMENT_ANALYSIS")
    static class NativeOwner extends BusinessOperationDispatcherDiscoveryTest.Owner {}
    @BusinessEntityService(ownerModule="SOL", entityType="otherAlias", nativeEntityType="REQUIREMENT_ANALYSIS")
    static class ConflictingOwner extends BusinessOperationDispatcherDiscoveryTest.Owner {}
    @BusinessEntityService(ownerModule="SOL", entityType="REQUIREMENT_ANALYSIS")
    static class UnaliasedOwner extends BusinessOperationDispatcherDiscoveryTest.Owner {}

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
    @Test void nativeAliasCannotShadowAnotherDeclarationWithoutAnAlias() {
        var alias = declaration("requirementAnalysis", "REQUIREMENT_ANALYSIS");
        var plain = declaration("REQUIREMENT_ANALYSIS", null);
        for (var declarations : List.of(List.of(alias, plain), List.of(plain, alias))) {
            var beans = new DefaultListableBeanFactory();
            beans.registerSingleton("models", (BusinessModelContributor) () -> declarations);
            var resolver = new BusinessEntityIdentityResolver(beans);
            var error = assertThrows(BusinessContractException.class, () -> resolver.declaredRef(
                    new EntityRef(23L, "SOL", "REQUIREMENT_ANALYSIS", 9007199254740993L)));
            assertEquals("IDENTITY_MAPPING_CONFLICT", error.getErrorCode());
        }
    }
    @Test void nativeAliasCannotShadowUnaliasedServiceMetadata() {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("models", (BusinessModelContributor) () -> List.of(declaration("requirementAnalysis", "REQUIREMENT_ANALYSIS")));
        var definition = new RootBeanDefinition(UnaliasedOwner.class);
        definition.setInstanceSupplier(() -> { throw new AssertionError("mapping must not initialize owner"); });
        beans.registerBeanDefinition("owner", definition);
        var error = assertThrows(BusinessContractException.class,
                () -> new BusinessEntityIdentityResolver(beans).hasMapping("SOL", "requirementAnalysis"));
        assertEquals("IDENTITY_MAPPING_CONFLICT", error.getErrorCode());
        assertFalse(beans.containsSingleton("owner"));
    }
    @Test void selfAliasesAndIdenticalNamesInOtherOwnersRemainUnambiguous() {
        var beans = new DefaultListableBeanFactory();
        var other = declaration("requirementAnalysis", "REQUIREMENT_ANALYSIS");
        var model = other.descriptor();
        var otherOwner = new BusinessModelDescriptor("ACC", "REQUIREMENT_ANALYSIS", "ACC_TEST", 1,
                model.kind(), model.title(), null, List.of(), List.of(), List.of(), List.of(), null);
        beans.registerSingleton("models", (BusinessModelContributor) () -> List.of(other,
                declaration("selfAlias", "selfAlias"), new BusinessModelDeclaration(otherOwner, Object.class, null, null)));
        var resolver = new BusinessEntityIdentityResolver(beans);
        assertEquals("requirementAnalysis", resolver.declaredRef(new EntityRef(23L, "SOL", "REQUIREMENT_ANALYSIS", 3L)).entityType());
        var self = new EntityRef(23L, "SOL", "selfAlias", 3L);
        assertEquals(self, resolver.declaredRef(resolver.nativeRef(self)));
        var crossOwner = new EntityRef(23L, "ACC", "REQUIREMENT_ANALYSIS", 3L);
        assertEquals(crossOwner, resolver.declaredRef(crossOwner));
    }
    private BusinessModelDeclaration declaration(String type, String nativeType) {
        var descriptor = new BusinessModelDescriptor("SOL", type, "TEST_" + type, 1,
                BusinessModelKind.AGGREGATE_ROOT, type, null, List.of(), List.of(), List.of(), List.of(), null);
        return new BusinessModelDeclaration(descriptor, Object.class, null, null, nativeType);
    }
}
