package cn.iocoder.yudao.module.pms.engineering.service.configuration;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigurationLogImportApi;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.query.ConfigurationImportedLogQuery;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Production mapper and real transactions; fixtures live only in a connection-private temporary table. */
@EnabledIfSystemProperty(named="skipITs",matches="false")
class ConfigurationLogImportMySqlTest {
    @Test void realUniqueReferencesAndSecondDeviceFailureRollback() throws Exception {
        assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));
        try (var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"))) {
            try(var sql=connection.createStatement()) {
                String definition;
                try(var table=sql.executeQuery("SHOW CREATE TABLE imp_eng_configuration")) {
                    assertTrue(table.next()); definition=table.getString(2);
                }
                sql.execute(definition.replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE"));
                // Mirror only the pending V423 schema on this private table, without migrating production early.
                var meta=connection.getMetaData();
                try(var columns=meta.getColumns(null,null,"imp_eng_configuration","imported_log_file_id")) {
                    if(!columns.next()) sql.execute("ALTER TABLE imp_eng_configuration ADD imported_log_file_id BIGINT NULL, ADD imported_log_batch_id BIGINT NULL, ADD UNIQUE KEY uk_import (tenant_id,imported_log_file_id)");
                }
                sql.execute("ALTER TABLE imp_eng_configuration ADD CONSTRAINT it_reject_second_device CHECK (equipment_id <> 32)");
            }
            var dataSource=new SingleConnectionDataSource(connection,true);
            var config=new MybatisConfiguration();
            GlobalConfigUtils.getGlobalConfig(config).setMetaObjectHandler(new DefaultDBFieldHandler());
            config.setMapUnderscoreToCamelCase(true);
            config.setEnvironment(new Environment("configuration-import-it",new SpringManagedTransactionFactory(),dataSource));
            var interceptor=new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
            config.addInterceptor(interceptor);
            try(var xml=getClass().getResourceAsStream("/mapper/configuration/ConfigurationMapper.xml")) {
                new XMLMapperBuilder(xml,config,"mapper/configuration/ConfigurationMapper.xml",config.getSqlFragments()).parse();
            }
            var sessions=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
            var mapper=sessions.getMapper(ConfigurationMapper.class);
            var codes=mock(EngineeringRecordCodeGenerator.class);
            when(codes.next(any(),anyString(),any(),any(String[].class))).thenReturn("IT-PZ-001","IT-PZ-002","IT-PZ-003");
            var logs=mock(DeviceConfigurationLogImportApi.class);
            var files=List.of(new DeviceConfigurationLogImportApi.File(100L,31L,"IT-SN-1",true),new DeviceConfigurationLogImportApi.File(101L,32L,"IT-SN-2",true));
            when(logs.importLog(any())).thenReturn(new DeviceConfigurationLogImportApi.Result(50L,"ARCHIVED",null,files));
            var scopes=mock(ProjectScopeApi.class); var scope=new ProjectScopeResult(21L,1L,Set.of(21L),Set.of());
            when(scopes.resolveCurrent(any())).thenReturn(scope); when(scopes.lockAndRevalidate(any())).thenReturn(scope);
            var permissions=mock(PermissionApi.class); when(permissions.hasAnyPermissions(7L,"pms:imp-configuration:create")).thenReturn(true);
            var service=new ConfigurationLogImportService(mapper,codes,logs,mock(ProjectDeviceSelectionApi.class),scopes,permissions);
            var tx=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            TenantContextHolder.setTenantId(1L); var user=new LoginUser(); user.setId(7L); user.setTenantId(1L);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null));
            try {
                assertThrows(RuntimeException.class,()->tx.execute(status->service.importLog(21L,"fail","log.txt",new byte[]{1})));
                try(var sql=connection.createStatement();var rows=sql.executeQuery("SELECT COUNT(*) FROM imp_eng_configuration")) {
                    assertTrue(rows.next()); assertEquals(0,rows.getInt(1));
                }
                when(logs.importLog(any())).thenReturn(new DeviceConfigurationLogImportApi.Result(50L,"ARCHIVED",null,List.of(files.getFirst())));
                var imported=tx.execute(status->service.importLog(21L,"ok","log.txt",new byte[]{1}));
                var replay=tx.execute(status->service.importLog(21L,"ok","log.txt",new byte[]{1}));
                assertEquals(imported,replay);
                tx.execute(status->{
                    var row=mapper.selectByImportedLogForUpdate(new ConfigurationImportedLogQuery(1L,100L));
                    assertNotNull(row); assertEquals(0,row.getStatus());
                    assertEquals(List.of(row.getCode()),mapper.selectRecordCodesIncludeDeleted(21L));
                    assertNull(mapper.selectByImportedLogForUpdate(new ConfigurationImportedLogQuery(2L,100L)));
                    return null;
                });
                try(var sql=connection.createStatement()) {
                    assertThrows(java.sql.SQLException.class,()->sql.execute("INSERT INTO imp_eng_configuration (project_id,code,equipment_id,tenant_id,imported_log_file_id) VALUES (21,'IT-DUP',31,1,100)"));
                    sql.execute("INSERT INTO imp_eng_configuration (project_id,code,equipment_id,tenant_id,imported_log_file_id) VALUES (21,'IT-OTHER-TENANT',31,2,100)");
                }
            } finally { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
        }
    }
}
