package cn.iocoder.yudao.module.pms.commerce.dal;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ExactContractProjectQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectCommerceQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExactContractProjectSqlTest {
    private static final String NS="cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectCommerceMapper.";

    @Test void reverseExactLookupMatchesAllThreeCurrentOwnerPathsAndExcludesInvalidFacts() throws Exception {
        var config=new Configuration();
        try(var xml=getClass().getResourceAsStream("/mapper/contract/ProjectCommerceMapper.xml")) {
            new XMLMapperBuilder(xml,config,"contract/ProjectCommerceMapper.xml",config.getSqlFragments()).parse();
        }
        try(var db=DriverManager.getConnection("jdbc:h2:mem:exact_contract_"+UUID.randomUUID()+";MODE=MySQL")) {
            var sql=db.createStatement();
            sql.execute("CREATE TABLE com_contract(id BIGINT,tenant_id BIGINT,contract_no VARCHAR(128),company_code VARCHAR(40),deleted INT)");
            sql.execute("CREATE TABLE com_project_contract_relation(tenant_id BIGINT,contract_id BIGINT,project_id BIGINT,deleted INT,status VARCHAR(20),effective_from TIMESTAMP,effective_to TIMESTAMP)");
            sql.execute("CREATE TABLE com_sales_order(id BIGINT,tenant_id BIGINT,contract_no VARCHAR(128),company_code VARCHAR(40),deleted INT)");
            sql.execute("CREATE TABLE com_sales_order_line(id BIGINT,tenant_id BIGINT,order_id BIGINT,deleted INT)");
            sql.execute("CREATE TABLE com_delivery_scope(tenant_id BIGINT,order_line_id BIGINT,project_id BIGINT,deleted INT,scope_status VARCHAR(20),effective_to TIMESTAMP,allocation_confirmed INT DEFAULT 1)");
            sql.execute("CREATE TABLE com_order_contract_relation(tenant_id BIGINT,order_id BIGINT,contract_id BIGINT,deleted INT,effective_from TIMESTAMP,effective_to TIMESTAMP)");
            sql.execute("INSERT INTO com_contract VALUES(1,1,'Exact%_','A',0),(2,2,'Exact%_','A',0),(3,1,'Deleted','A',1)");
            sql.execute("INSERT INTO com_project_contract_relation VALUES(1,1,10,0,'ACTIVE',NULL,NULL),(2,2,40,0,'ACTIVE',NULL,NULL),(1,3,41,0,'ACTIVE',NULL,NULL)");
            sql.execute("INSERT INTO com_sales_order VALUES(20,1,'Exact%_','A',0),(30,1,'Other','B',0),(50,1,'Exact%_','B',0)");
            sql.execute("INSERT INTO com_sales_order_line VALUES(20,1,20,0),(30,1,30,0),(50,1,50,0)");
            sql.execute("INSERT INTO com_delivery_scope(tenant_id,order_line_id,project_id,deleted,scope_status,effective_to) VALUES(1,20,20,0,'ACTIVE',NULL),(1,30,30,0,'ACTIVE',NULL),(1,50,50,0,'ACTIVE',NULL)");
            sql.execute("INSERT INTO com_order_contract_relation VALUES(1,30,1,0,'2020-01-01',NULL)");
            var query=new ExactContractProjectQuery(1L,"Exact%_");
            assertEquals(Set.of(10L,20L,30L),values(db,config,"selectProjectIdsByExactContractNo",query));
            for(var project:List.of(10L,20L,30L)) {
                assertEquals(Set.of(1L),values(db,config,"selectContracts",new ProjectCommerceQuery(1L,project)));
            }
            for(var input:List.of("Exact","exact%_","Exact%_ ","%","Deleted","missing")) {
                assertEquals(Set.of(),values(db,config,"selectProjectIdsByExactContractNo",new ExactContractProjectQuery(1L,input)),input);
            }
            assertEquals(Set.of(40L),values(db,config,"selectProjectIdsByExactContractNo",new ExactContractProjectQuery(2L,"Exact%_")));
            // Direct project association: every ineffective/deleted/wrong-tenant variant must disappear.
            for(var invalid:List.of("status='HISTORICAL'","deleted=1","effective_from='2999-01-01'","effective_to='2000-01-01'","tenant_id=2")) {
                sql.execute("UPDATE com_project_contract_relation SET "+invalid+" WHERE project_id=10");
                assertEquals(Set.of(20L,30L),values(db,config,"selectProjectIdsByExactContractNo",query),invalid);
                sql.execute("UPDATE com_project_contract_relation SET tenant_id=1,status='ACTIVE',deleted=0,effective_from=NULL,effective_to=NULL WHERE project_id=10");
            }
            // Both delivery paths retain current scope, line/order ownership, and deletion checks.
            for(var project:List.of(20L,30L)) {
                var remaining=project==20L?Set.of(10L,30L):Set.of(10L,20L);
                for(var invalid:List.of("scope_status='HISTORICAL'","deleted=1","effective_to='2000-01-01'","tenant_id=2")) {
                    sql.execute("UPDATE com_delivery_scope SET "+invalid+" WHERE project_id="+project);
                    assertEquals(remaining,values(db,config,"selectProjectIdsByExactContractNo",query),invalid);
                    sql.execute("UPDATE com_delivery_scope SET tenant_id=1,scope_status='ACTIVE',deleted=0,effective_to=NULL WHERE project_id="+project);
                }
                for(var table:List.of("com_sales_order_line","com_sales_order")) {
                    sql.execute("UPDATE "+table+" SET deleted=1 WHERE id="+project);
                    assertEquals(remaining,values(db,config,"selectProjectIdsByExactContractNo",query),table);
                    sql.execute("UPDATE "+table+" SET deleted=0 WHERE id="+project);
                }
            }
            for(var invalid:List.of("deleted=1","effective_from='2999-01-01'","effective_to='2000-01-01'","tenant_id=2")) {
                sql.execute("UPDATE com_order_contract_relation SET "+invalid);
                assertEquals(Set.of(10L,20L),values(db,config,"selectProjectIdsByExactContractNo",query),invalid);
                sql.execute("UPDATE com_order_contract_relation SET tenant_id=1,deleted=0,effective_from='2020-01-01',effective_to=NULL");
            }
            sql.execute("UPDATE com_contract SET deleted=1 WHERE id=1");
            assertEquals(Set.of(),values(db,config,"selectProjectIdsByExactContractNo",query));
        }
    }

    private Set<Long> values(Connection db,Configuration config,String method,Object query) throws Exception {
        var params=Map.of("query",query);var bound=config.getMappedStatement(NS+method).getBoundSql(params);
        // MySQL unqualified BINARY retains all bytes; H2 defaults to one byte, so use VARBINARY.
        try(var statement=db.prepareStatement(bound.getSql().replace(" AS BINARY)"," AS VARBINARY)"))) {
            int i=1;
            for(var mapping:bound.getParameterMappings()) {
                String name=mapping.getProperty();
                statement.setObject(i++,bound.hasAdditionalParameter(name)?bound.getAdditionalParameter(name):config.newMetaObject(params).getValue(name));
            }
            var ids=new HashSet<Long>();
            try(var rows=statement.executeQuery()) {while(rows.next()) ids.add(rows.getLong(1));}
            return ids;
        }
    }
}
