package cn.iocoder.yudao.module.pms.bindings.backend;

import cn.iocoder.yudao.module.pms.bindings.dal.dataobject.InlineInstanceDO;
import cn.iocoder.yudao.module.pms.bindings.dal.mysql.InlineInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.ProcessDefinitionSnapshot;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.BusinessFactPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InlineSyncTransactionTest {
    @Test void instanceFailureRollsBackTheResultInTheSameDatabaseTransaction() {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE formed_result (id BIGINT PRIMARY KEY)");
            var instances = mock(InlineInstanceMapper.class);
            var formation = mock(BusinessResultFormationPort.class);
            var ref = new EntityRef(7L, "PRJ", "project", 9L);
            when(formation.form(anyString(), eq(ref), any(), anyString(), anyString())).thenAnswer(call -> {
                jdbc.update("INSERT INTO formed_result(id) VALUES (1)");
                return new BusinessResultRecord("ready", ref, "result", ResultSemantics.NEW_RESULT, "key", null, true, null, 1L);
            });
            when(instances.insert(any(InlineInstanceDO.class))).thenThrow(new IllegalStateException("instance write failed"));
            var proxy = new ProxyFactory(new InlineSyncBackend(instances, mock(BusinessFactPort.class), formation));
            proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(database), new AnnotationTransactionAttributeSource()));
            var service = (InlineSyncBackend) proxy.getProxy();
            var definition = new ProcessDefinitionSnapshot("ready", 1, "test", "PRJ", "project", "PRJ/project", 1,
                    "evaluate", 1, "FIELD_CONDITION", "1", List.of(), "ready");
            assertThrows(IllegalStateException.class, () -> service.run(new InlineSyncBackend.RunCommand(definition, ref, "key")));
            verify(formation).form(anyString(), eq(ref), any(), anyString(), anyString());
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM formed_result", Long.class));
        } finally { database.shutdown(); }
    }
}
