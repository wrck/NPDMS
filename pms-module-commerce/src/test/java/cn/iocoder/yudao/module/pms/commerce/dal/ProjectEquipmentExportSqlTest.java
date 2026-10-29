package cn.iocoder.yudao.module.pms.commerce.dal;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.ProjectEquipmentExportMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.ProjectEquipmentExportQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectEquipmentExportSqlTest {
    private SqlSession session;private ProjectEquipmentExportMapper mapper;
    @BeforeEach void setup() throws Exception {
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:equipment_export_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var c=source.getConnection();var s=c.createStatement()) {
            s.execute("CREATE TABLE com_delivery_scope(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,order_line_id BIGINT,product_code VARCHAR(64),product_desc VARCHAR(64),order_no VARCHAR(64),allocated_qty DECIMAL(20,4),scope_status VARCHAR(20),effective_to TIMESTAMP,deleted INT DEFAULT 0)");
            s.execute("CREATE TABLE com_sales_order_line(id BIGINT PRIMARY KEY,tenant_id BIGINT,order_id BIGINT,company_code VARCHAR(64),product_code VARCHAR(64),delivered_qty DECIMAL(20,4),open_qty DECIMAL(20,4),deleted INT DEFAULT 0)");
            s.execute("CREATE TABLE com_order_contract_relation(tenant_id BIGINT,order_id BIGINT,contract_id BIGINT,deleted INT DEFAULT 0)");
            s.execute("CREATE TABLE com_project_contract_relation(tenant_id BIGINT,project_id BIGINT,contract_id BIGINT,status VARCHAR(20),effective_to TIMESTAMP,deleted INT DEFAULT 0)");
            s.execute("INSERT INTO com_sales_order_line VALUES(11,1,111,'001','02010005',20,30,0),(12,1,112,'002','02010075',NULL,0,0),(13,2,113,'001','FOREIGN',999,999,0)");
            for(int id=1;id<=130;id++) s.execute("INSERT INTO com_delivery_scope(id,tenant_id,project_id,order_line_id,product_code,allocated_qty,scope_status) VALUES("+id+",1,10,11,'02010005',1,'ACTIVE')");
            s.execute("INSERT INTO com_delivery_scope VALUES(131,1,10,12,'02010075','P2','ORDER2',1,'ACTIVE',NULL,0),(132,1,10,13,'FOREIGN','P3','ORDER3',1,'ACTIVE',NULL,0),"
                    +"(201,2,10,11,'OTHER-TENANT',NULL,NULL,1,'ACTIVE',NULL,0),(202,1,20,11,'OTHER-PROJECT',NULL,NULL,1,'ACTIVE',NULL,0),(203,1,10,11,'ENDED',NULL,NULL,1,'ACTIVE',CURRENT_TIMESTAMP,0),(204,1,10,11,'INACTIVE',NULL,NULL,1,'RELEASED',NULL,0),(205,1,10,11,'DELETED',NULL,NULL,1,'ACTIVE',NULL,1)");
            s.execute("INSERT INTO com_order_contract_relation VALUES(1,112,212,0)");s.execute("INSERT INTO com_project_contract_relation VALUES(1,10,212,'ACTIVE',NULL,0)");
        }
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setEnvironment(new Environment("test",new JdbcTransactionFactory(),source));config.setMapUnderscoreToCamelCase(true);
        for(var entity:List.of(
                cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO.class,
                cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO.class,
                cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO.class,
                cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(config,""),entity);
        }
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        var interceptor=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(new cn.iocoder.yudao.framework.tenant.config.TenantProperties())));config.addInterceptor(interceptor);
        try(var xml=getClass().getResourceAsStream("/mapper/scope/ProjectEquipmentExportMapper.xml")){new XMLMapperBuilder(xml,config,"equipment",config.getSqlFragments()).parse();}
        session=new SqlSessionFactoryBuilder().build(config).openSession();mapper=session.getMapper(ProjectEquipmentExportMapper.class);
    }
    @AfterEach void close(){if(session!=null)session.close();cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();}
    private ProjectEquipmentExportQuery query(){return ProjectEquipmentExportQuery.builder().tenantId(1L).projectId(10L).limit(50).companyCodes(List.of("001")).orderLineProjectIds(List.of(10L)).build();}
    @Test void cursorReadsOnlyCurrentProjectAllocationsAndNeverJoinsAnotherTenant() {
        var q=query();List<Long> ids=new ArrayList<>();
        while(true){var rows=mapper.selectBatch(q);rows.forEach(row->ids.add(row.scopeId()));if(rows.size()<50)break;q=q.toBuilder().afterId(rows.getLast().scopeId()).build();}
        assertEquals(132,ids.size());assertEquals(132,new HashSet<>(ids).size());
        var last=mapper.selectBatch(query().toBuilder().afterId(130L).build());assertEquals(12L,last.getFirst().matchedLineId());assertNull(last.getFirst().deliveredQuantity());assertNull(last.getLast().matchedLineId());
    }
    @Test void emptyOrderLinePermissionsKeepAllocationsButHideSourceQuantities() {
        var rows=mapper.selectBatch(query().toBuilder().companyCodes(List.of()).orderLineProjectIds(List.of()).build());
        assertEquals(50,rows.size());assertTrue(rows.stream().allMatch(row->row.matchedLineId()==null && row.deliveredQuantity()==null));
        assertTrue(mapper.selectBatch(query().toBuilder().projectId(null).build()).isEmpty());
        assertTrue(mapper.selectBatch(query().toBuilder().tenantId(2L).build()).isEmpty());
    }
}
