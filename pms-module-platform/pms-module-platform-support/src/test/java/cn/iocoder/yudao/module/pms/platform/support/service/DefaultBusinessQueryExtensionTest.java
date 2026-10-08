package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.DefaultBusinessModels;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DefaultBusinessQueryExtensionTest {
    static class Note extends BaseProjectBusinessEntity { @BusinessModelField String title; }
    interface NoteMapper extends BaseMapper<Note> { }
    DefaultBusinessApplicationService defaults;
    BusinessOperationDispatcher dispatcher;
    final EntityRef identity = new EntityRef(7L, "IT", "note", 11L);
    final BusinessEntityPageQuery query = new BusinessEntityPageQuery("list", "IT", "note", List.of(), 20, null);
    int queries, reads;
    BusinessEntityData data(EntityRef ref, Map<String,Object> values) {
        return new BusinessEntityData(ref, null, values, 3L, true, null);
    }
    BusinessEntitySlice slice(BusinessEntityData value) {
        return new BusinessEntitySlice(List.of(value), null, Completeness.COMPLETE, null);
    }
    @BeforeEach void setup() {
        var mapper = (NoteMapper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{NoteMapper.class},
                (proxy, method, args) -> { throw new AssertionError("This routing test must not execute SQL"); });
        var declaration = DefaultBusinessModels.project("IT", "note", "IT_NOTE", "记录", "it:note", Note.class, mapper, List.of());
        var beans = new StaticListableBeanFactory();
        beans.addBean("models", (BusinessModelContributor) () -> List.of(declaration));
        var registry = new BusinessEntityPersistenceRegistry(beans.getBeanProvider(BusinessModelContributor.class));
        BusinessModelCatalog catalog = new BusinessModelCatalog() {
            public Optional<BusinessModelDescriptor> find(String owner, String type) { return Optional.of(declaration.descriptor()); }
            public Optional<BusinessModelDescriptor> findByStableCode(String code) { return Optional.of(declaration.descriptor()); }
            public List<BusinessModelDescriptor> all() { return List.of(declaration.descriptor()); }
        };
        BusinessAccessGuard guard = new BusinessAccessGuard() {
            public void requireReadable(BusinessModelDescriptor d, EntityActor a, String scene) { assertEquals(7L, a.tenantId()); }
            public void requireWritable(BusinessModelDescriptor d, EntityActor a, String scene) { fail("Read must not grant write"); }
        };
        BusinessEntityAccessPort access = new BusinessEntityAccessPort() {
            public BusinessEntityData read(EntityDataRef ref, EntityActor actor, String scene) { reads++; return data(ref.entity(), Map.of("title", "authorized projection")); }
            public BusinessEntitySlice query(BusinessEntityPageQuery q, EntityActor actor) { queries++; return slice(data(identity, Map.of())); }
        };
        defaults = new DefaultBusinessApplicationService(() -> new AbstractBusinessApplicationService.ResolvedCaller(7L,42L,null), catalog,registry,guard,null,null,null,null);
        defaults.configureDefaultCapabilities(access, () -> { throw new AssertionError("Query does not require delivery"); });
        dispatcher = new BusinessOperationDispatcher(registry, defaults);
    }
    @Test void ordinaryBusinessUsesDefaultQuery() {
        assertEquals("authorized projection", dispatcher.query(query).members().getFirst().fieldValues().get("title"));
        assertEquals(1,queries); assertEquals(1,reads);
    }
    @Test void complexSelectionOverrideIsUsedAndItsRowsAreReauthorizedAndProjected() {
        dispatcher.register("IT","note",new ExtensibleBusinessApplicationService(defaults) {
            @Override protected BusinessEntitySlice queryEntities(BusinessEntityPageQuery q, EntityActor actor) {
                return slice(data(identity,Map.of("internalSecret","not public")));
            }
        });
        var result=dispatcher.query(query);
        assertEquals(0,queries); assertEquals(1,reads);
        assertEquals(Map.of("title","authorized projection"),result.members().getFirst().fieldValues());
    }
    @Test void legacyCommandServiceDoesNotReplaceTheConfiguredReadPath() {
        // Legacy command implementations do not have the new query capability ports wired.
        var nativeCommands = new DefaultBusinessApplicationService(null,null,null,null,null,null,null,null);
        dispatcher.register("IT", "note", nativeCommands);
        assertEquals("authorized projection", dispatcher.query(query).members().getFirst().fieldValues().get("title"));
        assertEquals(1, queries); assertEquals(1, reads);
    }
    @Test void overrideCannotReturnAnotherTenant() {
        dispatcher.register("IT","note",new ExtensibleBusinessApplicationService(defaults) {
            @Override protected BusinessEntitySlice queryEntities(BusinessEntityPageQuery q, EntityActor actor) {
                return slice(data(new EntityRef(8L,"IT","note",11L),Map.of()));
            }
        });
        var denied=assertThrows(BusinessContractException.class,()->dispatcher.query(query));
        assertEquals("QUERY_RESULT_INVALID",denied.getErrorCode());assertEquals(0,reads);
    }
}
