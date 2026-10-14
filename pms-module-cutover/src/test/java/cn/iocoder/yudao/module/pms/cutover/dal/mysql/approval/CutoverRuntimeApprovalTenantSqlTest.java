package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalTodoPageQuery;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CutoverRuntimeApprovalTenantSqlTest {
    @ParameterizedTest
    @ValueSource(strings = {"selectTodoPage", "selectTodoProjectionPage", "countTodos"})
    void roleTodoQueryRetainsTenantInterceptor(String method) throws Exception {
        var configuration = new Configuration();
        var path = Path.of("src/main/resources/mapper/approval/CutoverRuntimeApprovalNodeMapper.xml");
        try (var input = Files.newInputStream(path)) {
            new XMLMapperBuilder(input, configuration, path.toString(), configuration.getSqlFragments()).parse();
        }
        var sql = configuration.getMappedStatement(CutoverRuntimeApprovalNodeMapper.class.getName() + "." + method)
                .getBoundSql(Map.of("query", ApprovalTodoPageQuery.builder().tenantId(1L).currentUserId(2108539871029018630L).offset(0).pageSize(20).candidateRoleIds(java.util.Set.of()).build())).getSql();
        var interceptor = new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public Expression getTenantId() { return new LongValue(1L); }
        });
        var protectedSql = interceptor.parserSingle(sql, null);
        assertThat(protectedSql).contains("n.tenant_id = 1", "i.tenant_id = 1", "t.tenant_id = 1");
    }
}
