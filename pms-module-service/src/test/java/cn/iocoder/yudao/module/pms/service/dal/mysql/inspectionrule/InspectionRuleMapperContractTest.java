package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule;

import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleDisableUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleDraftUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleChildrenQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleDetectionIdQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleIdentityLockQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleNameQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleRevisionKeyQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleRevisionPageQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.SelectableInspectionRuleQuery;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionRuleMapperContractTest {

    @Test
    void mapperQueriesMustUseScenarioQueryObjects() throws Exception {
        assertParameterType(InspectionRuleMapper.class, "selectByTenantAndDetectionId", InspectionRuleDetectionIdQuery.class);
        assertParameterType(InspectionRuleMapper.class, "selectByTenantAndRuleName", InspectionRuleNameQuery.class);
        assertParameterType(InspectionRuleMapper.class, "selectByIdForUpdate", InspectionRuleIdentityLockQuery.class);
        assertParameterType(InspectionRuleRevisionMapper.class, "selectByRuleIdAndRevisionNo", InspectionRuleRevisionKeyQuery.class);
        assertParameterType(InspectionRuleRevisionMapper.class, "selectPage", InspectionRuleRevisionPageQuery.class);
        assertParameterType(InspectionRuleRevisionMapper.class, "updateDraftIfMatch", InspectionRuleDraftUpdate.class);
        assertParameterType(InspectionRuleRevisionMapper.class, "disablePublishedIfMatch", InspectionRuleDisableUpdate.class);
        assertParameterType(InspectionRuleRevisionMapper.class, "selectMaxRevisionNoByRule", InspectionRuleIdentityLockQuery.class);
        assertParameterType(InspectionRuleCommandRevisionMapper.class, "selectListByRevisionIds", InspectionRuleChildrenQuery.class);
        assertParameterType(InspectionRuleCommandRevisionMapper.class, "hardDeleteByRevisionIds", InspectionRuleChildrenQuery.class);
        assertParameterType(InspectionRuleProductTypeRevisionMapper.class, "selectListByRevisionIds", InspectionRuleChildrenQuery.class);
        assertParameterType(InspectionRuleProductTypeRevisionMapper.class, "hardDeleteByRevisionIds", InspectionRuleChildrenQuery.class);
        assertParameterType(InspectionRuleSecurityReviewMapper.class, "selectListValidByRevisionIds", InspectionRuleChildrenQuery.class);
        assertParameterType(SelectableInspectionRuleMapper.class, "selectListSelectable", SelectableInspectionRuleQuery.class);
    }

    @Test
    void stableKeyQueriesMustIncludeExplicitTenantConditions() {
        Wrapper<?> detectionWrapper = captureSelectOneWrapper(
                InspectionRuleMapper.class,
                mapper -> mapper.selectByTenantAndDetectionId(new InspectionRuleDetectionIdQuery(11L, "DET-001")));
        Wrapper<?> nameWrapper = captureSelectOneWrapper(
                InspectionRuleMapper.class,
                mapper -> mapper.selectByTenantAndRuleName(new InspectionRuleNameQuery(12L, "Rule A")));
        Wrapper<?> revisionWrapper = captureSelectOneWrapper(
                InspectionRuleRevisionMapper.class,
                mapper -> mapper.selectByRuleIdAndRevisionNo(new InspectionRuleRevisionKeyQuery(13L, 21L, 2)));

        assertEquals(7, detectionWrapper.getExpression().getNormal().size());
        assertEquals(7, nameWrapper.getExpression().getNormal().size());
        assertEquals(11, revisionWrapper.getExpression().getNormal().size());
    }

    @Test
    void emptyRevisionAndProductTypeScopesMustReturnEmptyWithoutExecutingSql() {
        InspectionRuleCommandRevisionMapper commandMapper = rejectingSqlProxy(InspectionRuleCommandRevisionMapper.class);
        InspectionRuleProductTypeRevisionMapper productTypeMapper = rejectingSqlProxy(InspectionRuleProductTypeRevisionMapper.class);
        InspectionRuleSecurityReviewMapper securityReviewMapper = rejectingSqlProxy(InspectionRuleSecurityReviewMapper.class);
        SelectableInspectionRuleMapper selectableMapper = rejectingSqlProxy(SelectableInspectionRuleMapper.class);

        InspectionRuleChildrenQuery emptyChildren = new InspectionRuleChildrenQuery(1L, Set.of(), null);
        SelectableInspectionRuleQuery emptySelectable = new SelectableInspectionRuleQuery(1L, Set.of());

        assertEquals(List.of(), commandMapper.selectListByRevisionIds(emptyChildren));
        assertEquals(0, commandMapper.hardDeleteByRevisionIds(emptyChildren));
        assertEquals(List.of(), productTypeMapper.selectListByRevisionIds(emptyChildren));
        assertEquals(0, productTypeMapper.hardDeleteByRevisionIds(emptyChildren));
        assertEquals(List.of(), securityReviewMapper.selectListValidByRevisionIds(emptyChildren));
        assertEquals(List.of(), selectableMapper.selectListSelectable(emptySelectable));
    }

    @Test
    void xmlQueriesMustKeepTenantDeletionFilteringStableOrderAndLocking() throws Exception {
        String ruleXml = Files.readString(Path.of("src/main/resources/mapper/inspectionrule/InspectionRuleMapper.xml"));
        assertTrue(ruleXml.contains("tenant_id = #{query.tenantId}"));
        assertTrue(ruleXml.contains("id = #{query.ruleId}"));
        assertTrue(ruleXml.contains("FOR UPDATE"));

        String revisionXml = Files.readString(Path.of("src/main/resources/mapper/inspectionrule/InspectionRuleRevisionMapper.xml"));
        assertTrue(revisionXml.contains("r.tenant_id = #{query.tenantId}"));
        assertTrue(revisionXml.contains("r.deleted = b'0'"));
        assertTrue(revisionXml.contains("i.deleted = b'0'"));
        assertTrue(revisionXml.contains("i.rule_name LIKE CONCAT('%', #{query.ruleNameKeyword}, '%')"));
        assertTrue(revisionXml.contains("p.product_type_code = #{query.productTypeCode}"));
        assertTrue(revisionXml.contains("ORDER BY r.rule_id, r.revision_no DESC, r.id DESC"));
        assertTrue(revisionXml.contains("LIMIT #{query.offset}, #{query.pageSize}"));
        assertTrue(revisionXml.contains("tenant_id = #{command.tenantId}"));
        assertTrue(revisionXml.contains("status_code = 'DRAFT'"));
        assertTrue(revisionXml.contains("version = #{command.expectedVersion}"));
        assertTrue(revisionXml.contains("version = version + 1"));
        assertTrue(revisionXml.contains("SELECT MAX(revision_no)"));
        assertTrue(revisionXml.contains("rule_id = #{query.ruleId}"));
        assertTrue(revisionXml.contains("target.id = #{query.targetRevisionId}"));
        assertTrue(revisionXml.contains("target.deleted = b'0'"));
        assertTrue(revisionXml.contains("FOR UPDATE"));

        String selectableXml = Files.readString(Path.of("src/main/resources/mapper/inspectionrule/SelectableInspectionRuleMapper.xml"));
        assertTrue(selectableXml.contains("r.tenant_id = #{query.tenantId}"));
        assertTrue(selectableXml.contains("r.status_code = 'PUBLISHED'"));
        assertTrue(selectableXml.contains("collection=\"query.productTypeCodes\""));
        assertTrue(selectableXml.contains("ORDER BY r.sort_order, i.detection_id, r.id"));
    }

    private static void assertParameterType(Class<?> mapperType, String methodName, Class<?> parameterType) throws Exception {
        Method method = mapperType.getMethod(methodName, parameterType);
        assertEquals(1, method.getParameterCount());
    }

    @SuppressWarnings("unchecked")
    private static <T> Wrapper<?> captureSelectOneWrapper(Class<T> mapperType, MapperCall<T> call) {
        AtomicReference<Wrapper<?>> captured = new AtomicReference<>();
        T mapper = (T) Proxy.newProxyInstance(mapperType.getClassLoader(), new Class<?>[]{mapperType}, (proxy, method, args) -> {
            if (method.isDefault()) {
                return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
            }
            if (method.getName().equals("selectList")) {
                captured.set((Wrapper<?>) args[0]);
                return List.of();
            }
            throw new AssertionError("unexpected SQL method " + method.getName());
        });
        call.execute(mapper);
        return captured.get();
    }

    @SuppressWarnings("unchecked")
    private static <T> T rejectingSqlProxy(Class<T> mapperType) {
        return (T) Proxy.newProxyInstance(mapperType.getClassLoader(), new Class<?>[]{mapperType}, (proxy, method, args) -> {
            if (method.isDefault()) {
                return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
            }
            throw new AssertionError("empty scope must not execute SQL method " + method.getName());
        });
    }

    @FunctionalInterface
    private interface MapperCall<T> {
        void execute(T mapper);
    }
}
