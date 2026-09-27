package cn.iocoder.yudao.module.pms.platform.support.persistence;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessEntityPersistenceRegistryTest {

    static class OrderEntity extends BaseBusinessEntity {
    }

    static class ChecklistEntity extends BaseBusinessEntity {
    }

    static class ForeignEntity {
    }

    interface OrderMapper extends BaseMapper<OrderEntity> {
    }

    interface ChecklistMapper extends BaseMapper<ChecklistEntity> {
    }

    interface OrderRevisionMapper extends BaseMapper<OrderEntity> {
    }

    interface UnrelatedMapper extends BaseMapper<ChecklistEntity> {
    }

    @Test
    void registersDeclarationsAndResolvesMappers() {
        BaseMapper<?> orderMapper = mapper(OrderMapper.class);
        BaseMapper<?> checklistMapper = mapper(ChecklistMapper.class);
        BaseMapper<?> revisionMapper = mapper(OrderRevisionMapper.class);
        BusinessEntityPersistenceRegistry registry = registry(contributor(
                declaration("it", "order", "IT_ORDER", OrderEntity.class, orderMapper, null),
                declaration("it", "checklist", "IT_CHECKLIST", ChecklistEntity.class,
                        checklistMapper, revisionMapper)));

        BusinessModelDeclaration order = registry.require("it", "order");
        assertEquals("IT_ORDER", order.descriptor().stableCode());
        assertSame(OrderEntity.class, order.entityClass());
        assertTrue(registry.find("it", "missing").isEmpty());
        assertThrows(BusinessContractException.class, () -> registry.require("it", "missing"));
        assertEquals("IT_CHECKLIST", registry.require("it", "checklist").descriptor().stableCode());

        assertSame(orderMapper, registry.mapperOf(order));
        assertTrue(registry.revisionMapperOf(order).isEmpty());
        BusinessModelDeclaration checklist = registry.require("it", "checklist");
        assertSame(checklistMapper, registry.mapperOf(checklist));
        assertSame(revisionMapper, registry.revisionMapperOf(checklist).orElseThrow());
    }

    @Test
    void rejectsDuplicateEntityDeclaration() {
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> registry(contributor(
                        declaration("it", "order", "IT_ORDER", OrderEntity.class, mapper(OrderMapper.class), null),
                        declaration("it", "order", "IT_ORDER_V2", OrderEntity.class,
                                mapper(OrderMapper.class), null))));
        assertEquals("ENTITY_DECLARED_TWICE", ex.getErrorCode());
    }

    @Test
    void rejectsStableCodeConflict() {
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> registry(contributor(
                        declaration("it", "order", "IT_ORDER", OrderEntity.class, mapper(OrderMapper.class), null),
                        declaration("it", "checklist", "IT_ORDER", ChecklistEntity.class,
                                mapper(ChecklistMapper.class), null))));
        assertEquals("STABLE_CODE_CONFLICT", ex.getErrorCode());
    }

    @Test
    void rejectsEntityOutsideUnifiedHierarchy() {
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> registry(contributor(declaration("it", "foreign", "IT_FOREIGN",
                        ForeignEntity.class, mapper(OrderMapper.class), null))));
        assertEquals("ENTITY_NOT_UNIFIED", ex.getErrorCode());
    }

    @Test
    void rejectsMapperGenericMismatch() {
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> registry(contributor(declaration("it", "order", "IT_ORDER",
                        OrderEntity.class, mapper(UnrelatedMapper.class), null))));
        assertEquals("DECLARATION_MISMATCH", ex.getErrorCode());
    }

    @Test
    void rejectsNonBaseMapperDeclaration() {
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> registry(contributor(declaration("it", "order", "IT_ORDER",
                        OrderEntity.class, "not-a-mapper", null))));
        assertEquals("DECLARATION_INCOMPLETE", ex.getErrorCode());
    }

    @Test
    void rejectsNonBaseMapperRevision() {
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> registry(contributor(declaration("it", "order", "IT_ORDER",
                        OrderEntity.class, mapper(OrderMapper.class), "not-a-mapper"))));
        assertEquals("DECLARATION_MISMATCH", ex.getErrorCode());
    }

    private static BusinessEntityPersistenceRegistry registry(BusinessModelContributor... contributors) {
        ObjectProvider<BusinessModelContributor> provider = new ObjectProvider<>() {
            @Override
            public BusinessModelContributor getObject(Object... args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public BusinessModelContributor getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Stream<BusinessModelContributor> orderedStream() {
                return Stream.of(contributors);
            }
        };
        return new BusinessEntityPersistenceRegistry(provider);
    }

    private static BusinessModelContributor contributor(BusinessModelDeclaration... declarations) {
        return () -> List.of(declarations);
    }

    private static BusinessModelDeclaration declaration(String ownerModule, String entityType, String stableCode,
                                                        Class<?> entityClass, Object mapper, Object revisionMapper) {
        BusinessModelDescriptor descriptor = new BusinessModelDescriptor(ownerModule, entityType, stableCode,
                1, BusinessModelKind.AGGREGATE_ROOT, stableCode, null,
                List.of(), List.of(), List.of(), List.of(), null);
        return new BusinessModelDeclaration(descriptor, entityClass, mapper, revisionMapper);
    }

    private static BaseMapper<?> mapper(Class<? extends BaseMapper<?>> mapperInterface) {
        return (BaseMapper<?>) Proxy.newProxyInstance(mapperInterface.getClassLoader(),
                new Class<?>[]{mapperInterface}, (proxy, method, args) -> null);
    }
}
