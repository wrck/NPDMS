package cn.iocoder.yudao.module.pms.customer.service.contact.business;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.*;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.business.ProjectContactBusinessDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.*;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.business.ProjectContactBusinessMapper;
import cn.iocoder.yudao.module.pms.customer.service.contact.*;
import cn.iocoder.yudao.module.pms.customer.service.security.*;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.project.api.contact.ProjectContactContextApi;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Own fresh H2 database: real inherited CRUD, source/primary/history SQL and rollback. */
class ProjectContactBusinessServiceTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    ProjectContactBusinessService service;
    ProjectContactContextApi projects;
    BusinessEventPort events;
    BusinessScopeAccess scope;

    @BeforeEach void open() throws Exception {
        TenantContextHolder.setTenantId(1L);
        context=new AnnotationConfigApplicationContext();
        var dataSource=new DriverManagerDataSource("jdbc:h2:mem:contact_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(dataSource);
        for(var type:List.of(ProjectContactBusinessDO.class,CustomerContactMasterDO.class,ContactHistoryDO.class,CustomerMasterDO.class))schema(type);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(dataSource);
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        var interceptors=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        interceptors.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor());
        interceptors.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.H2));config.addInterceptor(interceptors);
        factory.setConfiguration(config);
        var global=new com.baomidou.mybatisplus.core.config.GlobalConfig();global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());factory.setGlobalConfig(global);
        var resources=new ArrayList<ByteArrayResource>();
        for(String xml:List.of("CustomerContactMasterMapper.xml","ProjectCustomerContactMapper.xml","business/ProjectContactBusinessMapper.xml")) {
            String text=Files.readString(Path.of("src/main/resources/mapper/contact/"+xml)).replace("b'0'","0").replace("b'1'","1");
            resources.add(new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8),xml));
        }
        factory.setMapperLocations(resources.toArray(ByteArrayResource[]::new));var sessions=new SqlSessionTemplate(factory.getObject());
        for(var type:List.of(ProjectContactBusinessMapper.class,CustomerContactMasterMapper.class,ProjectCustomerContactMapper.class,ContactHistoryMapper.class)){if(!sessions.getConfiguration().hasMapper(type))sessions.getConfiguration().addMapper(type);context.getBeanFactory().registerSingleton(type.getSimpleName(),sessions.getMapper(type));}
        projects=mock(ProjectContactContextApi.class);setContext(true,true);
        when(projects.lockForWrite(any())).thenAnswer(call->{var query=call.<ProjectContactContextApi.WriteQuery>getArgument(0);if(!Objects.equals(query.expectedProjectVersion(),2L))throw new IllegalStateException("PROJECT_VERSION_CONFLICT");return projects.inspect(new ProjectContactContextApi.Query(query.tenantId(),query.actorUserId(),query.projectId()));});
        scope=mock(BusinessScopeAccess.class);when(scope.policyRef()).thenReturn("project");when(scope.readableScopeIds(any())).thenReturn(Set.of(7L));
        events=mock(BusinessEventPort.class);
        var journal=mock(OperationExecutionStore.class);when(journal.reserve(any(),any())).thenReturn(true);
        var defaults=new BusinessDefaults(()->new AbstractBusinessApplicationService.ResolvedCaller(1L,3L,"contact-it"),mock(BusinessAccessGuard.class),scope,
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator(),new TransactionTemplate(new DataSourceTransactionManager(dataSource)),journal,
                mock(OperationAuditApi.class),events,List.of(mock(BusinessDeletionGuard.class)),()->mock(DefaultBusinessDeliveryApi.class));
        context.getBeanFactory().registerSingleton("defaults",defaults);context.getBeanFactory().registerSingleton("projects",projects);
        var extensions=mock(cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi.class);
        when(extensions.read(any(),any())).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi.Values(null,Map.of(),0));
        context.getBeanFactory().registerSingleton("extensions",extensions);
        context.getBeanFactory().registerSingleton("forms",mock(cn.iocoder.yudao.module.pms.platform.api.entity.EntityFormApi.class));
        var access=mock(CustomerContactAccessService.class);when(access.resolve(anyLong(),anyBoolean())).thenReturn(CustomerFieldMaskingService.ContactAccess.MASKED);
        context.getBeanFactory().registerSingleton("access",access);context.registerBean(CustomerFieldMaskingService.class);
        context.getBeanFactory().registerSingleton("nativeContacts",mock(ProjectCustomerContactService.class));context.getBeanFactory().registerSingleton("wakeup",mock(ProjectContactRuleWakeup.class));
        context.registerBean(ProjectContactBusinessService.class);context.refresh();service=context.getBean(ProjectContactBusinessService.class);
        jdbc.update("INSERT INTO cus_customer_master(id,tenant_id,lifecycle_status,deleted) VALUES(8,1,'ENABLED',0)");
    }
    @AfterEach void close(){if(jdbc!=null)jdbc.execute("SHUTDOWN");if(context!=null)context.close();TenantContextHolder.clear();}
    void setContext(boolean manage,boolean history){when(projects.inspect(any())).thenReturn(new ProjectContactContextApi.Context(7L,8L,2L,"ACTIVE",manage,history));}
    Map<String,Object> input(String name){return new LinkedHashMap<>(Map.of("projectId",7L,"name",name,"mobile","13800138000","email","member@example.com","status",0,"primaryFlag",false,"$business",Map.of("expectedProjectVersion",2L)));}
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}

    @Test void inheritedSavePreservesSourceAndPrimaryHistory(){
        var first=service.createForm(input("来源姓名"),"first");var row=service.get(first.entityRef().entityId());
        assertTrue(row.getPrimaryFlag());assertEquals(1,count("cus_customer_contact"));assertEquals(2,count("cus_contact_history"));
        var saved=service.updateForm(row.getId(),Map.of("name","仅项目修改","$business",Map.of("expectedProjectVersion",2L)),row.getVersion(),"save");
        assertEquals("来源姓名",jdbc.queryForObject("SELECT name FROM cus_customer_contact WHERE id=?",String.class,row.getCustomerContactId()));
        assertEquals("仅项目修改",service.get(row.getId()).getName());
        assertThrows(RuntimeException.class,()->service.updateForm(row.getId(),Map.of("name","旧版本","$business",Map.of("expectedProjectVersion",2L)),first.newConcurrencyBasis(),"stale"));
        var secondInput=input("新主联系人");secondInput.put("primaryFlag",true);var second=service.createForm(secondInput,"second");
        assertFalse(service.get(row.getId()).getPrimaryFlag());assertTrue(service.get(second.entityRef().entityId()).getPrimaryFlag());
        assertEquals(saved.newConcurrencyBasis()+1,service.get(row.getId()).getVersion());
        assertEquals(6,count("cus_contact_history"));
    }
    @Test void primaryRemovalRequiresAcknowledgementAndRetainsSourceAndAudit(){
        var created=service.createForm(input("主联系人"),"first");long id=created.entityRef().entityId();
        assertThrows(RuntimeException.class,()->service.delete(id,created.newConcurrencyBasis(),"unconfirmed"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cus_project_customer_contact_relation WHERE deleted=1",Integer.class));
        service.deleteAcknowledged(id,created.newConcurrencyBasis(),"confirmed",true);
        assertEquals(1,count("cus_customer_contact"));assertEquals(3,count("cus_contact_history"));
        assertEquals(3L,jdbc.queryForObject("SELECT deleted_by FROM cus_project_customer_contact_relation WHERE id=?",Long.class,id));
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM cus_project_customer_contact_relation WHERE id=?",java.sql.Timestamp.class,id));
    }
    @Test void maskedReadAndApprovalNeverExposeRawContactAndHistoryIsScoped(){
        var created=service.createForm(input("联系人"),"first");long id=created.entityRef().entityId();
        var runtimeQuery=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Query(1L,7L,service.runtimeDefinition().type(),id);
        assertEquals(true,service.runtimeObservation(runtimeQuery,false).facts().get("PROJECT_CONTACT_ACTIVE"));
        assertEquals("13800138000",service.readableValues(service.get(id)).get("mobile"));
        assertEquals("138****8000",service.approvalSubject(id,false,false).values().get("mobile"));
        setContext(false,false);assertEquals("138****8000",service.readableValues(service.get(id)).get("mobile"));
        assertThrows(RuntimeException.class,()->service.approvalSubject(id,true,false));
        jdbc.update("UPDATE cus_project_customer_contact_relation SET status=1 WHERE id=?",id);
        assertEquals(false,service.runtimeObservation(runtimeQuery,false).facts().get("PROJECT_CONTACT_ACTIVE"));
        assertThrows(RuntimeException.class,()->service.get(id));
        var query=new BusinessPageQuery();query.setProjectId(7L);assertEquals(0,service.page(query).getTotal());
        query.setFilters(List.of(new BusinessFieldFilter("mobile",BusinessFieldFilter.Operator.EQ,List.of("13800138000"))));
        assertThrows(RuntimeException.class,()->service.page(query));
    }
    @Test void staleProjectAndOutboxFailureRollBackSourceContactAndHistory(){
        var stale=input("旧项目版本");stale.put("$business",Map.of("expectedProjectVersion",1L));assertThrows(RuntimeException.class,()->service.createForm(stale,"stale"));
        assertEquals(0,count("cus_customer_contact"));
        doThrow(new IllegalStateException("OUTBOX_FAILED")).when(events).append(any());
        assertThrows(RuntimeException.class,()->service.createForm(input("必须回滚"),"rollback"));
        verify(events).append(any());
        assertEquals(0,count("cus_customer_contact"));assertEquals(0,count("cus_project_customer_contact_relation"));assertEquals(0,count("cus_contact_history"));
    }
    void schema(Class<?> type){
        var columns=new LinkedHashMap<String,String>();
        for(Class<?> current=type;current!=Object.class;current=current.getSuperclass())for(Field field:current.getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers()) || field.isSynthetic())continue;var annotation=field.getAnnotation(TableField.class);if(annotation!=null&&!annotation.exist())continue;
            String name=annotation!=null&&!annotation.value().isBlank()?annotation.value():field.getName().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
            var value=field.getType();String sql=value==Long.class||value==Integer.class?"BIGINT":value==Boolean.class?"BOOLEAN":value==java.time.LocalDateTime.class?"TIMESTAMP":"VARCHAR(100000)";
            if(name.equals("id"))sql="BIGINT AUTO_INCREMENT PRIMARY KEY";else if(name.equals("deleted"))sql="BOOLEAN DEFAULT FALSE";else if(name.endsWith("_time"))sql="TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
            columns.putIfAbsent(name,name+" "+sql);
        }
        jdbc.execute("CREATE TABLE "+type.getAnnotation(TableName.class).value()+" ("+String.join(",",columns.values())+")");
    }
}
