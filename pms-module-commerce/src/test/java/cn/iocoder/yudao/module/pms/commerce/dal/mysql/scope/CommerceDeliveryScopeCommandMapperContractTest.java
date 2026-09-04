package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.CommerceDeliveryScopeCommandQuery.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommerceDeliveryScopeCommandMapperContractTest {

    private static final Path XML = Path.of("src/main/resources/mapper/scope/CommerceDeliveryScopeCommandMapper.xml");
    private static final String NS = CommerceDeliveryScopeCommandMapper.class.getName() + ".";

    @Test
    void parsesScenarioQueriesAndStableLockOrder() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Files.newInputStream(XML)) {
            new XMLMapperBuilder(input, configuration, XML.toString(), configuration.getSqlFragments()).parse();
        }

        BoundSql orders = configuration.getMappedStatement(NS + "selectOrderLinesForUpdate")
                .getBoundSql(new OrderLinesLock(1L, List.of(30L, 31L)));
        assertTrue(normalize(orders.getSql()).contains("ORDER BY id FOR UPDATE"));
        assertEquals(3, orders.getParameterMappings().size());

        BoundSql scopes = configuration.getMappedStatement(NS + "selectCurrentScopesForUpdate")
                .getBoundSql(new CurrentScopesLock(1L, List.of(30L, 31L)));
        assertTrue(normalize(scopes.getSql()).contains("ORDER BY order_line_id, id FOR UPDATE"));

        BoundSql details = configuration.getMappedStatement(NS + "selectScopeDetailsForUpdate")
                .getBoundSql(new ScopeDetailsLock(1L, List.of(100L, 101L)));
        assertTrue(normalize(details.getSql()).contains("ORDER BY delivery_scope_id, id FOR UPDATE"));

        BoundSql update = configuration.getMappedStatement(NS + "advanceProjectVersion")
                .getBoundSql(new AdvanceProjectVersion(1L, 10L, 0L, 0, 1L, 1,
                        "ASSIGNED", "20", LocalDateTime.now()));
        assertTrue(normalize(update.getSql()).contains("scope_version = ?"));
        assertFalse(Files.readString(XML).contains("${"));
    }

    @Test
    void mapperMethodsUseOneScenarioParameter() {
        for (var method : CommerceDeliveryScopeCommandMapper.class.getDeclaredMethods()) {
            assertEquals(1, method.getParameterCount(), method.getName());
            assertTrue(method.getParameterTypes()[0].getEnclosingClass()
                    == cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.CommerceDeliveryScopeCommandQuery.class,
                    method.getName());
        }
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
