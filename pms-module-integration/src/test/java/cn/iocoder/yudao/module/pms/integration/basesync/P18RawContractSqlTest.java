package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.integration.basesync.task.P18ProjectContractSyncTask;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Production XML and tenant rewriting only; no database, connection or DDL. */
class P18RawContractSqlTest {
    private static final String NAMESPACE=P18ProjectContractSyncTask.TargetMapper.class.getName()+".";
    private Configuration configuration() throws Exception {
        var configuration=new Configuration();
        String resource="mapper/basesync/P18TargetMapper.xml";
        try(var input=new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
    private TenantLineInnerInterceptor interceptor() {
        TenantContextHolder.setTenantId(7L);
        return new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties()) {
            @Override public boolean ignoreTable(String tableName) { return false; }
        });
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }
    @Test void rawInsertBindsOriginalContractTextAndNullReference() throws Exception {
        String original="  AbC-合同-001  ";
        var source=new P18ProjectContractSyncTask.Source().setTarget(new P18ProjectContractSyncTask.Row()
                .setId(11L).setTenantId(7L).setProjectId(21L).setContractNo(original).setContractId(null));
        var parameters=Map.of("rows",List.of(source));
        var statement=configuration().getMappedStatement(NAMESPACE+"insertProjectContract");
        var bound=statement.getBoundSql(parameters);
        var prepared=mock(PreparedStatement.class);
        new DefaultParameterHandler(statement,parameters,bound).setParameters(prepared);
        int contractNoPosition=0,contractIdPosition=0;
        for(int index=0;index<bound.getParameterMappings().size();index++) {
            String property=bound.getParameterMappings().get(index).getProperty();
            if(property.endsWith(".target.contractNo"))contractNoPosition=index+1;
            if(property.endsWith(".target.contractId"))contractIdPosition=index+1;
        }
        assertTrue(contractNoPosition>0 && contractIdPosition>0);
        verify(prepared).setString(contractNoPosition,original);
        verify(prepared).setNull(eq(contractIdPosition),anyInt());
        String rewritten=interceptor().parserMulti(bound.getSql(),null);
        assertEquals(bound.getSql().chars().filter(c->c=='?').count(),rewritten.chars().filter(c->c=='?').count());
    }
    @Test void postfillRetainsExactKeysCurrentTargetsAndOriginalAuditThroughTenantRewrite() throws Exception {
        var statement=configuration().getMappedStatement(NAMESPACE+"fillContractReferences");
        var query=new P18ProjectContractSyncTask.ContractReferencesQuery(7,List.of(11L,12L),List.of(
                new P18ProjectContractSyncTask.CompanyRelationIds("001",List.of(11L)),
                new P18ProjectContractSyncTask.CompanyRelationIds("002",List.of(12L))));
        var bound=statement.getBoundSql(Map.of("query",query));
        String rewritten=interceptor().parserMulti(bound.getSql(),null);
        String sql=rewritten.replaceAll("\\s+","").replace("`","").toLowerCase(java.util.Locale.ROOT);
        assertTrue(sql.contains("r.tenant_id=?"));
        assertTrue(sql.contains("r.tenant_id=7"));
        assertTrue(sql.contains("p.tenant_id=r.tenant_id"));
        assertTrue(sql.contains("c.tenant_id=r.tenant_id"));
        assertEquals(2,sql.split("r.idin\\(\\?\\)",-1).length-1);
        assertTrue(sql.contains("cast(p.company_codeasbinary)=cast(?asbinary)"));
        assertTrue(sql.contains("r.contract_idisnull"));
        assertTrue(sql.contains("cast(c.contract_noasbinary)=cast(r.contract_noasbinary)"));
        assertTrue(sql.contains("cast(c.company_codeasbinary)=cast(p.company_codeasbinary)"));
        assertTrue(sql.contains("r.update_time=r.update_time"));
        assertFalse(sql.contains("setr.contract_no="));
        assertEquals(bound.getParameterMappings().size(),rewritten.chars().filter(c->c=='?').count());
    }
    @Test void emptyPostfillTargetsRemainEmptyForUpdateAndReadback() throws Exception {
        var configuration=configuration();
        var companies=List.of(new P18ProjectContractSyncTask.CompanyRelationIds("001",List.of(11L)));
        for(String name:List.of("fillContractReferences","selectContractReferences")) {
            String sql=configuration.getMappedStatement(NAMESPACE+name)
                    .getBoundSql(Map.of("query",new P18ProjectContractSyncTask.ContractReferencesQuery(7,List.of(),companies)))
                    .getSql().replaceAll("\\s+","");
            assertTrue(sql.contains("AND1=0"),name);
            assertFalse(sql.contains("IN()"),name);
        }
        for(var groups:List.of(List.<P18ProjectContractSyncTask.CompanyRelationIds>of(),
                List.of(new P18ProjectContractSyncTask.CompanyRelationIds("001",List.of())))) {
            String sql=configuration.getMappedStatement(NAMESPACE+"fillContractReferences")
                    .getBoundSql(Map.of("query",new P18ProjectContractSyncTask.ContractReferencesQuery(7,List.of(11L),groups)))
                    .getSql().replaceAll("\\s+","");
            assertTrue(sql.contains("AND1=0"));assertFalse(sql.contains("IN()"));
        }
    }
    @Test void rawRelationshipLookupUsesProjectAndUnmodifiedContractNumberWithoutContractCatalogue() throws Exception {
        var query=new P18ProjectContractSyncTask.RelationsQuery(7,List.of(new P18ProjectContractSyncTask.RelationKey(21L," Raw-C ")),false);
        String sql=configuration().getMappedStatement(NAMESPACE+"selectRelations").getBoundSql(Map.of("query",query))
                .getSql().replaceAll("\\s+","").toLowerCase(java.util.Locale.ROOT);
        assertTrue(sql.contains("tenant_id=?"));
        assertTrue(sql.contains("project_id=?"));
        assertTrue(sql.contains("cast(contract_noasbinary)=cast(?asbinary)"));
        assertTrue(sql.endsWith("forupdate"));
        assertFalse(sql.contains("joincom_contract"));
    }
}
