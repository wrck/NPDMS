package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** 专用临时Compose数据库，执行真实迁移后的Mapper、租户拦截器和履行服务；不代表原生Owner端到端验收。 */
@EnabledIfSystemProperty(named="delivery.fulfillment.mysql", matches="true")
class DeliveryFulfillmentMySqlTest {
    @Test void migrationPreservesHistoryAndScopedWithdrawalDoesNotWithdrawSharedMaterial() throws Exception {
        String url=System.getenv("DELIVERY_VERIFY_JDBC_URL");
        assertEquals("jdbc:mysql://127.0.0.1:25406/delivery_verify?useSSL=false&allowPublicKeyRetrieval=true",url);
        try(var connection=DriverManager.getConnection(url,System.getenv("DELIVERY_VERIFY_DB_USER"),System.getenv("DELIVERY_VERIFY_DB_PASSWORD"))) {
            var configuration=new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.getGlobalConfig(configuration)
                    .setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());
            configuration.setEnvironment(new Environment("delivery-fulfillment",new JdbcTransactionFactory(),new SingleConnectionDataSource(connection,true)));
            var interceptor=new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
            configuration.addInterceptor(interceptor);
            for(var mapper:List.of(DeliveryMaterialMapper.class,DeliveryRequirementMapper.class,DeliverySubmissionMapper.class,DeliveryFulfillmentMapper.class)) {
                configuration.addMapper(mapper);
                String resource="/mapper/delivery/"+mapper.getSimpleName()+".xml";
                try(var input=getClass().getResourceAsStream(resource)) {
                    assertNotNull(input);new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
                }
            }
            try(var session=new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(false)) {
                TenantContextHolder.setTenantId(7L);
                var materials=session.getMapper(DeliveryMaterialMapper.class);
                var requirements=session.getMapper(DeliveryRequirementMapper.class);
                var submissions=session.getMapper(DeliverySubmissionMapper.class);
                var fulfillment=new DeliveryFulfillmentService(session.getMapper(DeliveryFulfillmentMapper.class));
                var original=materials.selectById(9101L);
                assertEquals(9001L,original.getRequirementId());
                assertEquals("ACTIVE",original.getStatus());
                assertNull(original.getSourceIdentityKey());
                assertEquals("WITHDRAWN",materials.selectByRequirement(9001L).stream().filter(m->m.getId()==9102L).findFirst().orElseThrow().getStatus());
                assertEquals("{\"original\":true}",submissions.selectById(9401L).getSubmitEvidenceJson());
                assertEquals("[9101]",submissions.selectById(9401L).getMaterialIdsJson());
                fulfillment.associate(requirements.selectById(9002L),original);
                fulfillment.associate(requirements.selectById(9002L),original); // same relation is idempotent
                session.clearCache();
                assertEquals(List.of(9101L),materials.selectByRequirement(9002L).stream().map(m->m.getId()).toList());
                fulfillment.retainOnly(9001L,Set.of());
                session.clearCache();
                assertEquals("WITHDRAWN",materials.selectByRequirement(9001L).stream().filter(m->m.getId()==9101L).findFirst().orElseThrow().getStatus());
                assertEquals("ACTIVE",materials.selectByRequirement(9002L).getFirst().getStatus());
                assertEquals(9001L,materials.selectByRequirement(9002L).getFirst().getRequirementId());
                assertEquals("ACTIVE",materials.selectById(9101L).getStatus());
                var publicRequirements=new DeliveryRequirementService(requirements,submissions,materials,null,null,null,null);
                assertEquals(0,publicRequirements.countOf(requirements.selectById(9001L)));
                assertEquals(1,publicRequirements.countOf(requirements.selectById(9002L)));
                assertEquals(9101L,materials.selectSourceFile(new DeliverySourceFileQuery(7L,99L,9301L,1)).getId());
                assertNull(materials.selectSourceFile(new DeliverySourceFileQuery(7L,100L,9301L,1)));
                assertEquals(9401L,submissions.selectListUsingMaterial(new DeliveryMaterialSubmissionQuery(7L,9101L)).getFirst().getId());
                // Two immutable source submissions can share one material and retain independent archive targets.
                try(var fixture=connection.createStatement()) {
                    fixture.executeUpdate("INSERT INTO plt_delivery_submission (id,tenant_id,requirement_id,request_key,material_ids_json,source_type,project_id,request_payload_json,status,submit_evidence_json,archive_status,archive_retry_count,creator,updater,deleted) VALUES (9402,7,9001,'report:1','[9101]','AUTO_PROJECTION',99,'{}','SUPERSEDED','{}','PENDING_COMPENSATION',0,'verify','verify',0),(9403,7,9002,'report:2','[9101]','AUTO_PROJECTION',99,'{}','CURRENT','{}','PENDING_COMPENSATION',0,'verify','verify',0)");
                }
                assertEquals(List.of(9402L,9403L),submissions.selectPendingArchiveSubmissions(new DeliveryArchiveQueueQuery(7L)).stream().map(r->r.getId()).toList());
                assertNotNull(submissions.selectArchiveSubmissionForUpdate(new DeliveryArchiveSubmissionQuery(7L,9402L)));
                assertEquals(1,submissions.updateArchiveSubmissionState(new DeliveryArchiveSubmissionStateQuery(7L,9402L,"ARCHIVED",null)));
                session.clearCache();
                assertEquals(List.of(9403L),submissions.selectPendingArchiveSubmissions(new DeliveryArchiveQueueQuery(7L)).stream().map(r->r.getId()).toList());
                assertEquals(0,submissions.updateArchiveSubmissionState(new DeliveryArchiveSubmissionStateQuery(7L,9402L,"PENDING_COMPENSATION","late-failure")));
                assertNull(submissions.selectArchiveSubmissionForUpdate(new DeliveryArchiveSubmissionQuery(8L,9403L)));
                assertEquals(1,submissions.updateArchiveSubmissionState(new DeliveryArchiveSubmissionStateQuery(7L,9403L,"PENDING_COMPENSATION","target-failed")));
                session.clearCache();assertEquals(1,submissions.selectById(9403L).getArchiveRetryCount());
                assertEquals("ARCHIVED",submissions.selectById(9402L).getArchiveStatus());
                try(var fixture=connection.createStatement()) {
                    fixture.executeUpdate("UPDATE plt_delivery_material SET archive_status='PENDING_COMPENSATION' WHERE id=9101 AND tenant_id=7");
                }
                var completedMaterial=new DeliveryMaterialArchiveStateQuery(7L,9101L,"ARCHIVED",null,java.time.LocalDateTime.now(),"verify");
                assertEquals(0,materials.updateArchiveStateIfPending(completedMaterial)); // target 9403 remains pending
                assertEquals(1,submissions.updateArchiveSubmissionState(new DeliveryArchiveSubmissionStateQuery(7L,9403L,"ARCHIVED",null)));
                assertEquals(1,materials.updateArchiveStateIfPending(completedMaterial));
                try(var fixture=connection.createStatement()) {
                    fixture.executeUpdate("UPDATE plt_delivery_material SET archive_status='NOT_REQUIRED' WHERE id=9101 AND tenant_id=7");
                }
                session.clearCache();
                // Atomic archive obligation merge does not overwrite terminal states or evidence anchors.
                var obligation = new DeliveryMaterialArchiveObligationQuery(7L,9101L);
                assertEquals(1,materials.requireArchiveIfNotRequired(obligation));
                session.clearCache();
                assertEquals("PENDING_COMPENSATION",materials.selectById(9101L).getArchiveStatus());
                assertEquals(0,materials.requireArchiveIfNotRequired(obligation));
                assertEquals(0,materials.requireArchiveIfNotRequired(new DeliveryMaterialArchiveObligationQuery(8L,9101L)));
                for(String terminal:List.of("ARCHIVED","INVALID")) {
                    try(var fixture=connection.prepareStatement("UPDATE plt_delivery_material SET archive_status=? WHERE id=9101 AND tenant_id=7")) {
                        fixture.setString(1,terminal);fixture.executeUpdate();
                    }
                    assertEquals(0,materials.requireArchiveIfNotRequired(obligation));
                    session.clearCache();
                    assertEquals(terminal,materials.selectById(9101L).getArchiveStatus());
                }
                assertEquals(9001L,materials.selectById(9101L).getRequirementId());
                assertEquals(9201L,materials.selectById(9101L).getFileReferenceId());
                TenantContextHolder.setTenantId(8L);session.clearCache();
                assertTrue(materials.selectByRequirement(9001L).isEmpty());
                assertNull(materials.selectSourceFile(new DeliverySourceFileQuery(8L,99L,9301L,1)));
                assertThrows(BusinessContractException.class,()->fulfillment.associate(requirements.selectById(9002L),original));
                assertEquals(0,materials.assignSourceIdentityIfMissing(new DeliverySourceIdentityAssignment(8L,9101L,"wrong-tenant")));
                TenantContextHolder.setTenantId(7L);session.clearCache();
                assertEquals(1,materials.assignSourceIdentityIfMissing(new DeliverySourceIdentityAssignment(7L,9101L,"native-result-canonical")));
                assertEquals(0,materials.assignSourceIdentityIfMissing(new DeliverySourceIdentityAssignment(7L,9101L,"overwrite-history")));
                session.clearCache();
                var canonical=materials.selectById(9101L);
                assertEquals("native-result-canonical",canonical.getSourceIdentityKey());
                assertEquals(original.getOwnerModule(),canonical.getOwnerModule());
                assertEquals(9001L,canonical.getRequirementId());assertEquals(9201L,canonical.getFileReferenceId());
                assertEquals("{\"original\":true}",submissions.selectById(9401L).getSubmitEvidenceJson());
                session.rollback();
            } finally { TenantContextHolder.clear(); }
        }
    }
}
