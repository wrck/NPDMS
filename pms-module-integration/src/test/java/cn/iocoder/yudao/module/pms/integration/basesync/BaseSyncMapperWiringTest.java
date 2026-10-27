package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.module.pms.integration.api.basesync.AbstractSyncTask;
import cn.iocoder.yudao.module.pms.integration.api.basesync.DefaultSyncTask;
import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncBatch;
import cn.iocoder.yudao.module.pms.integration.basesync.task.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaseSyncMapperWiringTest {
    @Configuration
    @ComponentScan("cn.iocoder.yudao.module.pms.integration.basesync.task")
    @MapperScan(basePackages="cn.iocoder.yudao.module.pms.integration.basesync",annotationClass=Mapper.class)
    static class Wiring {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:base-wiring-"+UUID.randomUUID()+";MODE=MySQL","sa","");
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory=new SqlSessionFactoryBean();factory.setDataSource(source);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/basesync/*.xml"));
            return factory.getObject();
        }
        @Bean cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi organizationScope(){return mock(cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi.class);}
        @Bean cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi managedOrganizations(){return mock(cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.class);}
        @Bean cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi contractVisibility(){return mock(cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi.class);}
        @Bean cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi projectScope(){return mock(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.class);}
        @Bean BaseSyncProjectReferences projectReferences(BaseSyncProjectReferences.ReferenceMapper mapper,cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi scope){return new BaseSyncProjectReferences(mapper,scope);}
        @Bean cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi extensions(){return mock(cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi.class);}
        @Bean cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi permissions(){return mock(cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi.class);}
        @Bean cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi events(){return mock(cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi.class);}
        @Bean BaseSyncPublishedProjects published(BaseSyncPublishedProjects.ReferenceMapper mapper){return new BaseSyncPublishedProjects(mapper);}
        @Bean cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryExtensionApi legacySoftware(){return mock(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryExtensionApi.class);}
        @Bean cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi devices(){return mock(cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi.class);}
        @Bean cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi locations(){return mock(cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi.class);}
        @Bean jakarta.validation.Validator validator(){return mock(jakarta.validation.Validator.class);}
        @Bean org.springframework.security.crypto.password.PasswordEncoder passwordEncoder(){return mock(org.springframework.security.crypto.password.PasswordEncoder.class);}
        @Bean cn.iocoder.yudao.framework.tenant.config.TenantProperties tenantProperties(){return mock(cn.iocoder.yudao.framework.tenant.config.TenantProperties.class);}
        @Bean org.springframework.cache.CacheManager caches(){return mock(org.springframework.cache.CacheManager.class);}
        @Bean BaseSyncReferences references(BaseSyncReferences.ReferenceMapper mapper) {
            return new BaseSyncReferences(mapper);
        }
    }

    @Test void nestedMappersAreScannedAndSourceQueriesStayOnTheirOwnConnection() throws Exception {
        try(var spring=new AnnotationConfigApplicationContext(Wiring.class)) {
            var tasks=spring.getBeansOfType(AbstractSyncTask.class);
            assertEquals(24,tasks.size());
            assertEquals(Set.of("BASE_P01","BASE_P04","BASE_P08","BASE_P10","BASE_P11","BASE_P12","BASE_P13","BASE_P14","BASE_P15","BASE_P16","BASE_P18","BASE_P19","BASE_P20","BASE_P21","BASE_P22","BASE_P23","BASE_P24","BASE_P25","BASE_P30","BASE_P31","BASE_P33","BASE_P34","BASE_P35","BASE_P37"),
                    tasks.values().stream().map(AbstractSyncTask::taskCode).collect(java.util.stream.Collectors.toSet()));
            var local=spring.getBean(SqlSessionFactory.class).getConfiguration();
            var external=new org.apache.ibatis.session.Configuration();
            for(var resource:new PathMatchingResourcePatternResolver().getResources("classpath*:base-sync-source/*Mapper.xml"))
                try(var input=resource.getInputStream()) {
                    new XMLMapperBuilder(input,external,resource.toString(),external.getSqlFragments()).parse();
                }
            new SqlSessionFactoryBuilder().build(external);
            for(var task:tasks.values()) {
                assertEquals(task instanceof P11CrmProductSyncTask?DefaultSyncTask.class:AbstractSyncTask.class,task.getClass().getSuperclass());
                for(var type:task.getClass().getDeclaredClasses()) if(type.isInterface() && type.getSimpleName().endsWith("Mapper")) {
                    if(type.isAnnotationPresent(Mapper.class)) {
                        assertNotNull(spring.getBean(type));
                        assertTrue(local.hasMapper(type));
                        assertFalse(external.hasMapper(type));
                    } else {
                        assertEquals(0,spring.getBeansOfType(type).size());
                        assertTrue(external.hasMapper(type));
                        assertFalse(local.hasMapper(type));
                    }
                }
            }
        }
    }
    record LimitQuery(Integer limit,List<String> projectKeys) {}
    @Test void sourceXmlBindsProbeLimitAndOmitsItForUnlimitedReads() throws Exception {
        var configuration=new org.apache.ibatis.session.Configuration();
        for(var resource:new PathMatchingResourcePatternResolver().getResources("classpath*:base-sync-source/*Mapper.xml"))
            try(var input=resource.getInputStream()) {
                new XMLMapperBuilder(input,configuration,resource.toString(),configuration.getSqlFragments()).parse();
            }
        int checked=0;
        for(var id:new ArrayList<>(configuration.getMappedStatementNames())) {
            if(!id.contains("."))continue;
            var mapped=configuration.getMappedStatement(id);
            var bounded=mapped.getBoundSql(new LimitQuery(11,List.of("1")));
            if(bounded.getParameterMappings().stream().noneMatch(p->p.getProperty().equals("limit")))continue;
            checked++;
            assertTrue(bounded.getSql().matches("(?is).*\\b(?:LIMIT|TOP)\\b.*"),id);
            var unlimited=mapped.getBoundSql(new LimitQuery(null,List.of("1")));
            assertFalse(unlimited.getSql().matches("(?is).*\\b(?:LIMIT|TOP)\\b.*"),id);
            assertFalse(unlimited.getSql().contains("<if"),id);
            assertTrue(unlimited.getParameterMappings().stream().noneMatch(p->p.getProperty().equals("limit")),id);
            var statement=mock(java.sql.PreparedStatement.class);
            configuration.newParameterHandler(mapped,new LimitQuery(11,List.of("1")),bounded).setParameters(statement);
            verify(statement).setInt(anyInt(),eq(11));
        }
        assertTrue(checked>=50,"all source limit statements must be exercised");
    }
    @Test void newBatchParameterShapesBindBothRowsInEveryChangedInsertXml() throws Exception {
        try(var spring=new AnnotationConfigApplicationContext(Wiring.class)) {
            var configuration=spring.getBean(SqlSessionFactory.class).getConfiguration();
            record InsertCase(Class<?> task,String dataName,List<?> rows) {}
            var cases=List.of(
                new InsertCase(P11CrmProductSyncTask.class,"CrmProduct",List.of(
                    P11CrmProductSyncTask.ProductSyncRow.builder().id(101L).build(),
                    P11CrmProductSyncTask.ProductSyncRow.builder().id(102L).build())),
                new InsertCase(P12SupplementProductSyncTask.class,"SupplementProduct",List.of(
                    P12SupplementProductSyncTask.ProductSyncRow.builder().id(101L).build(),
                    P12SupplementProductSyncTask.ProductSyncRow.builder().id(102L).build())),
                new InsertCase(P23CollectionPlanSyncTask.class,"CollectionPlan",List.of(
                    new P23CollectionPlanSyncTask.CollectionPlanCacheSource().setTarget(new P23CollectionPlanSyncTask.CollectionPlanCacheRow().setId(101L)),
                    new P23CollectionPlanSyncTask.CollectionPlanCacheSource().setTarget(new P23CollectionPlanSyncTask.CollectionPlanCacheRow().setId(102L)))),
                new InsertCase(P25WarrantySyncTask.class,"Warranty",List.of(
                    new P25WarrantySyncTask.WarrantySyncSource().setTarget(P25WarrantySyncTask.WarrantySyncRow.builder().id(101L).build()),
                    new P25WarrantySyncTask.WarrantySyncSource().setTarget(P25WarrantySyncTask.WarrantySyncRow.builder().id(102L).build()))),
                new InsertCase(P34FactoryHistorySyncTask.class,"FactoryHistory",List.of(
                    new P34FactoryHistorySyncTask.FactorySyncSource().setTarget(P34FactoryHistorySyncTask.FactorySyncRow.builder().id(101L).build()),
                    new P34FactoryHistorySyncTask.FactorySyncSource().setTarget(P34FactoryHistorySyncTask.FactorySyncRow.builder().id(102L).build())))
            );
            for(var example:cases){
                var mapped=configuration.getMappedStatement(example.task().getName()+"$TargetMapper.insert"+example.dataName());
                var batch=new SyncBatch<>(null,example.rows());
                var statement=mock(java.sql.PreparedStatement.class);
                configuration.newParameterHandler(mapped,batch,mapped.getBoundSql(batch)).setParameters(statement);
                verify(statement).setLong(anyInt(),eq(101L));verify(statement).setLong(anyInt(),eq(102L));
            }
        }
    }
}

