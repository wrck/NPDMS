package cn.iocoder.yudao.module.pms.platform.support.persistence;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.override.MybatisMapperProxy;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.lang.reflect.Proxy;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/** One XML query compiled with trusted MyBatis mappings, shared by every declared entity. */
public final class DeclaredBusinessCurrentRows {
    private DeclaredBusinessCurrentRows() { }

    public static BaseBusinessEntity lock(BusinessModelDeclaration declaration, DeclaredCurrentRowQuery query) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new BusinessContractException("TRANSACTION_REQUIRED", "Current entity read requires a transaction");
        Object mapper = declaration.mapper();
        Object target = AopProxyUtils.getSingletonTarget(mapper);
        if (target != null) mapper = target;
        if (!Proxy.isProxyClass(mapper.getClass()) || !(Proxy.getInvocationHandler(mapper) instanceof MybatisMapperProxy<?> proxy))
            throw new BusinessContractException("MAPPER_RUNTIME_UNAVAILABLE", "Declared entity requires its production MyBatis mapper");
        var session = proxy.getSqlSession();
        var configuration = session.getConfiguration();
        String namespace = proxy.getMapperInterface().getName();
        String statement = namespace + ".__pmsCurrentEntityForUpdate";
        synchronized (configuration) {
            if (!configuration.hasStatement(statement, false)) {
                var table = TableInfoHelper.getTableInfo(declaration.entityClass());
                if (table == null || !table.getTableName().matches("[A-Za-z_][A-Za-z0-9_]*"))
                    throw new BusinessContractException("MAPPER_MAPPING_INVALID", "Entity table mapping is unavailable");
                var original = configuration.getMappedStatement(namespace + ".selectById");
                var resultMap = original.getResultMaps().getFirst();
                // MyBatis-Plus may attach an inline ResultMap only to the statement for a
                // plain @TableName DO. Register that exact map before the shared XML refers
                // to it; no entity annotation or type-handler configuration is changed.
                if (!configuration.hasResultMap(resultMap.getId())) {
                    configuration.addResultMap(resultMap);
                }
                String xml;
                try (var stream = DeclaredBusinessCurrentRows.class.getResourceAsStream("declared-current-row.xml")) {
                    if (stream == null) throw new IllegalStateException("Shared current-row XML is missing");
                    xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                // Only registered ORM metadata is compiled here. Request values never enter SQL text.
                xml = xml.replace("@namespace@", namespace)
                        .replace("@resultMap@", original.getResultMaps().getFirst().getId())
                        .replace("@table@", table.getTableName()).replace("@columns@", table.getAllSqlSelect())
                        .replace("@logicDelete@", table.getLogicDeleteSql(true, true));
                new XMLMapperBuilder(new StringReader(xml), configuration, "pms:declared-current-row:" + namespace,
                        configuration.getSqlFragments()).parse();
            }
        }
        return session.selectOne(statement, query);
    }
}
