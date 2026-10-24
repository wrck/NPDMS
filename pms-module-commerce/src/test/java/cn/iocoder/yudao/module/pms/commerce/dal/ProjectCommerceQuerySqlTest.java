package cn.iocoder.yudao.module.pms.commerce.dal;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectCommerceQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderLineCompanyScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderLineProjectCandidateQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectCommerceQuerySqlTest {
    @Test void orderCandidatesPreserveCompanyOrProjectVisibilityAndFinalRelationRecheck() throws Exception {
        var config = configuration("order/SalesOrderLineMapper.xml");
        try (var db = DriverManager.getConnection("jdbc:h2:mem:order_candidates;MODE=MySQL")) {
            try (var sql = db.createStatement()) {
                sql.execute("CREATE TABLE com_sales_order_line(id BIGINT,tenant_id BIGINT,order_id BIGINT,order_no VARCHAR(40),company_code VARCHAR(40),line_no VARCHAR(40),deleted INT)");
                sql.execute("CREATE TABLE com_order_contract_relation(tenant_id BIGINT,order_id BIGINT,contract_id BIGINT,deleted INT)");
                sql.execute("CREATE TABLE com_project_contract_relation(tenant_id BIGINT,contract_id BIGINT,project_id BIGINT,deleted INT,status VARCHAR(20),effective_to TIMESTAMP)");
                sql.execute("INSERT INTO com_sales_order_line VALUES(1,1,100,'ORDER','A','1',0),(2,1,101,'ORDER','B','2',0),(3,1,102,'ORDER','B','3',0),(4,2,100,'ORDER','A','1',0),(5,1,103,'OTHER','B','1',0),(6,1,104,'ORDER','B','1',1)");
                sql.execute("INSERT INTO com_order_contract_relation VALUES(1,101,201,0),(1,102,202,0),(2,100,203,0),(1,103,204,0),(1,104,205,0)");
                sql.execute("INSERT INTO com_project_contract_relation VALUES(1,201,10,0,'ACTIVE',NULL),(1,202,999,0,'ACTIVE',NULL),(2,203,11,0,'ACTIVE',NULL),(1,204,12,0,'ACTIVE',NULL),(1,205,13,0,'ACTIVE',NULL),(1,201,14,1,'ACTIVE',NULL),(1,201,15,0,'HISTORICAL',NULL),(1,201,16,0,'ACTIVE','2000-01-01')");
            }
            String ns = "cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper.";
            assertEquals(List.of(10L,999L), values(db,config,ns+"selectProjectCandidatesByOrder",new SalesOrderLineProjectCandidateQuery(1L,null,"ORDER")).stream().sorted().toList());
            assertTrue(values(db,config,ns+"selectProjectCandidatesByOrder",new SalesOrderLineProjectCandidateQuery(1L,null,null)).isEmpty());
            assertEquals(List.of(10L),values(db,config,ns+"selectProjectCandidatesByOrder",new SalesOrderLineProjectCandidateQuery(1L,101L,"ORDER")));
            var full = lines(List.of(10L,11L,12L,13L,14L,15L,16L));
            var narrowed = lines(List.of(10L));
            assertEquals(List.of(1L,2L),values(db,config,ns+"selectByCompanyScope",full));
            assertEquals(values(db,config,ns+"selectByCompanyScope",full),values(db,config,ns+"selectByCompanyScope",narrowed));
            assertEquals(List.of(2L),values(db,config,ns+"selectCountByCompanyScope",narrowed));
            try(var sql=db.createStatement()) {sql.execute("UPDATE com_project_contract_relation SET status='HISTORICAL' WHERE project_id=10");}
            assertEquals(List.of(1L),values(db,config,ns+"selectByCompanyScope",narrowed));
        }
    }

    @Test void receivablesKeepExactContractTenantAndActiveRelationRules() throws Exception {
        var config=configuration("contract/ProjectCommerceMapper.xml");
        try(var db=DriverManager.getConnection("jdbc:h2:mem:project_receivables;MODE=MySQL")) {
            try(var sql=db.createStatement()) {
                sql.execute("CREATE TABLE com_contract(id BIGINT,tenant_id BIGINT,contract_no VARCHAR_IGNORECASE(40),deleted BOOLEAN)");
                sql.execute("CREATE TABLE com_project_contract_relation(tenant_id BIGINT,project_id BIGINT,contract_id BIGINT,deleted BOOLEAN,status VARCHAR(20),effective_from TIMESTAMP,effective_to TIMESTAMP)");
                sql.execute("CREATE TABLE com_contract_receivable(id BIGINT,tenant_id BIGINT,contract_no VARCHAR_IGNORECASE(40),deleted BOOLEAN)");
                sql.execute("INSERT INTO com_contract VALUES(1,1,'CONTRACT',FALSE)");
                sql.execute("INSERT INTO com_project_contract_relation VALUES(1,10,1,FALSE,'ACTIVE',NULL,NULL)");
                sql.execute("INSERT INTO com_contract_receivable VALUES(1,1,'CONTRACT',FALSE),(2,1,'contract',FALSE),(3,2,'CONTRACT',FALSE),(4,1,'CONTRACT',TRUE)");
            }
            String id="cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectCommerceMapper.selectReceivables";
            assertEquals(List.of(1L),values(db,config,id,new ProjectCommerceQuery(1L,10L)));
            assertTrue(values(db,config,id,new ProjectCommerceQuery(1L,11L)).isEmpty());
            try(var sql=db.createStatement()) {sql.execute("UPDATE com_project_contract_relation SET effective_from='2100-01-01'");}
            assertTrue(values(db,config,id,new ProjectCommerceQuery(1L,10L)).isEmpty());
        }
    }

    private SalesOrderLineCompanyScopeQuery lines(List<Long> projects) {
        return new SalesOrderLineCompanyScopeQuery(1L,List.of("A"),projects,null,null,null,"ORDER",null,null,null,null,0,20);
    }
    private Configuration configuration(String path) throws Exception {
        var config=new Configuration();
        try(var xml=getClass().getResourceAsStream("/mapper/"+path)) {new XMLMapperBuilder(xml,config,path,config.getSqlFragments()).parse();}
        return config;
    }
    private List<Long> values(Connection db,Configuration config,String id,Object query) throws Exception {
        var parameters=Map.of("query",query);var bound=config.getMappedStatement(id).getBoundSql(parameters);
        try(var statement=db.prepareStatement(bound.getSql().replace(" AS BINARY)"," AS VARBINARY(255))"))) {
            int i=1;
            for(var mapping:bound.getParameterMappings()) {
                String name=mapping.getProperty();statement.setObject(i++,bound.hasAdditionalParameter(name)?bound.getAdditionalParameter(name):config.newMetaObject(parameters).getValue(name));
            }
            var rows=new ArrayList<Long>();try(var result=statement.executeQuery()) {while(result.next())rows.add(result.getLong(1));}return rows;
        }
    }
}
