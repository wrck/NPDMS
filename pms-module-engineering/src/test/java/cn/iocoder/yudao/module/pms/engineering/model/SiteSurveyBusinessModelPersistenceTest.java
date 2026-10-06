package cn.iocoder.yudao.module.pms.engineering.model;

import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import cn.iocoder.yudao.module.pms.platform.support.service.OperationExecutionStore;
import cn.iocoder.yudao.module.pms.platform.support.service.DefaultBusinessApplicationService;
import cn.iocoder.yudao.module.pms.platform.support.service.AbstractBusinessApplicationService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.ReceiptOutcome;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessOwnerPermissionPolicy;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.validation.Validator;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.PermissionBusinessAccessGuard;
import cn.iocoder.yudao.module.pms.engineering.config.SiteSurveyPersistenceConfiguration;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.access.DefaultBusinessEntityAccess;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.metadata.*;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MyBatis statements run only against a unique in-memory H2 database. */
class SiteSurveyBusinessModelPersistenceTest {
    DriverManagerDataSource dataSource;
    SqlSession session;
    SiteSurveyEntityMapper mapper;
    TableInfo table;
    ProjectScopeApi scopes;
    PermissionApi permissions;
    PermissionCommonApi modelPermissions;
    DefaultBusinessEntityAccess access;
    BusinessModelDeclaration declaration;
    EntityActor actor = new EntityActor(3L, 9L, "survey-regression");

    @BeforeEach @SuppressWarnings("unchecked")
    void setup() throws Exception {
        TenantContextHolder.setTenantId(actor.tenantId());
        var principal = new LoginUser(); principal.setId(actor.userId()); principal.setTenantId(actor.tenantId()); principal.setUserType(2);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        TableInfoHelper.remove(SiteSurveyEntityDO.class);
        var properties = new MybatisPlusProperties();
        new SiteSurveyPersistenceConfiguration().siteSurveyAssignedIdentity().customize(properties);
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:current_survey_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("isolated-survey", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        GlobalConfigUtils.setGlobalConfig(configuration, properties.getGlobalConfig());
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(SiteSurveyEntityMapper.class);
        String mapperXml = "mapper/sitesurvey/entity/SiteSurveyEntityMapper.xml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(mapperXml)) {
            assertNotNull(stream);
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(stream, configuration, mapperXml,
                    configuration.getSqlFragments()).parse();
        }
        table = TableInfoHelper.getTableInfo(SiteSurveyEntityDO.class);
        List<String> columns = new ArrayList<>();
        columns.add("id BIGINT PRIMARY KEY");
        for (var field : table.getFieldList()) {
            var type = field.getPropertyType();
            String sqlType = type == Long.class ? "BIGINT" : type == Integer.class ? "INT"
                    : type == Boolean.class ? "BOOLEAN" : type == LocalDate.class ? "DATE"
                    : type == java.time.LocalDateTime.class ? "TIMESTAMP" : "VARCHAR(4000)";
            columns.add(field.getColumn() + " " + sqlType);
        }
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE sol_site_survey (" + String.join(",", columns) + ")");
            // Deliberately no legacy table: current reads cannot accidentally succeed against the import source.
        }
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        mapper = session.getMapper(SiteSurveyEntityMapper.class);
        var ctor = EngineeringBusinessModelContributor.class.getConstructors()[0];
        Object[] dependencies = Arrays.stream(ctor.getParameterTypes())
                .map(type -> type == SiteSurveyEntityMapper.class ? mapper : mock(type)).toArray();
        var contributor = (EngineeringBusinessModelContributor) ctor.newInstance(dependencies);
        declaration = contributor.declarations().stream().filter(d -> d.descriptor().entityType().equals("siteSurvey"))
                .findFirst().orElseThrow();
        BusinessModelContributor single = () -> List.of(declaration);
        ObjectProvider<BusinessModelContributor> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenAnswer(ignored -> Stream.of(single));
        var persistence = new BusinessEntityPersistenceRegistry(provider);
        assertSame(declaration, persistence.require("SOL", "siteSurvey"));
        assertSame(mapper, persistence.mapperOf(declaration));
        var catalog = mock(BusinessModelCatalog.class);
        when(catalog.require("SOL", "siteSurvey")).thenReturn(declaration.descriptor());
        scopes = mock(ProjectScopeApi.class);
        permissions = mock(PermissionApi.class);
        modelPermissions = mock(PermissionCommonApi.class);
        when(modelPermissions.hasAnyPermissions(9L, "pms:sol-site-survey:query")).thenReturn(true);
        when(permissions.hasAnyPermissions(9L, "pms:sol-site-survey:query")).thenReturn(true);
        when(scopes.resolveAllCurrent(new ProjectAllScopeQuery(3L, 9L, ProjectScopeApi.ACTION_VIEW)))
                .thenReturn(Set.of(100L));
        when(scopes.resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 100L, ProjectScopeApi.ACTION_VIEW)))
                .thenReturn(new ProjectScopeResult(100L, 1L, Set.of(100L), Set.of()));
        var owner = new SiteSurveyEntityProvider(mapper, mock(SiteSurveyDetails.class), scopes, permissions);
        access = new DefaultBusinessEntityAccess(catalog, persistence, new PermissionBusinessAccessGuard(modelPermissions), null,
                List.of(new SiteSurveyBusinessScopePolicy(owner, scopes)));
    }

    @AfterEach void close() { if (session != null) session.close(); SecurityContextHolder.clearContext(); TenantContextHolder.clear(); }

    SiteSurveyEntityDO insert(Long id, Long tenant, Long project, String code) {
        var row = new SiteSurveyEntityDO();
        row.setId(id); row.setTenantId(tenant); row.setProjectId(project); row.setCode(code);
        row.setName("Current survey " + code); row.setStatus(0); row.setVersion(4L); row.setDeleted(false);
        row.setSurveyDate(LocalDate.of(2026, 10, 5)); row.setCabinetReady(false); row.setPowerSupply("AC");
        assertEquals(1, mapper.insert(row));
        return row;
    }

    @Test void assignedIdentityAndLongOptimisticVersionRemainSingleMappedColumns() throws Exception {
        assertEquals("sol_site_survey", table.getTableName());
        assertEquals(IdType.ASSIGN_ID, table.getIdType());
        assertEquals(Long.class, table.getKeyType());
        assertTrue(table.isWithVersion());
        assertEquals(Long.class, table.getVersionFieldInfo().getPropertyType());
        assertEquals(1, table.getFieldList().stream().filter(f -> f.getColumn().equals("version")).count());
        assertEquals(table.getFieldList().size(), table.getFieldList().stream().map(TableFieldInfo::getColumn).distinct().count());
        assertEquals(BaseBusinessEntity.class, SiteSurveyEntityDO.class.getMethod("setId", Long.class).getDeclaringClass());
        var row = insert(null, 3L, 100L, "AUTO");
        assertNotNull(row.getId()); assertTrue(row.getId() > 0);
        var stale = mapper.selectById(row.getId());
        row.setPowerSupply(null); row.setSurveyDate(null);
        assertEquals(1, mapper.updateById(row));
        assertEquals(5L, row.getVersion());
        var persisted = mapper.selectById(row.getId());
        assertNull(persisted.getPowerSupply()); assertNull(persisted.getSurveyDate());
        assertEquals(5L, persisted.getVersion());
        stale.setName("stale write"); assertEquals(0, mapper.updateById(stale));
        assertEquals("Current survey AUTO", mapper.selectById(row.getId()).getName());
        row.setId(null); row.setCode("LONG"); row.setVersion(3_000_000_000L);
        assertEquals(1, mapper.insert(row));
        assertEquals(3_000_000_000L, mapper.selectById(row.getId()).getVersion());
        row.setName("Long version update");
        assertEquals(1, mapper.updateById(row));
        assertEquals(3_000_000_001L, mapper.selectById(row.getId()).getVersion());
        // The customization must not change the preserved legacy model's AUTO identity.
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(session.getConfiguration(), "legacy-metadata-control");
        var legacy = TableInfoHelper.initTableInfo(assistant,
                cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO.class);
        assertEquals(IdType.AUTO, legacy.getIdType());
        assertEquals("sol_eng_site_survey", legacy.getTableName());
        assertEquals(FieldStrategy.ALWAYS, table.getFieldList().stream()
                .filter(f -> f.getProperty().equals("siteLocationId")).findFirst().orElseThrow().getUpdateStrategy());
    }

    @Test void explicitlyOpenedFieldsReadAndFilterWithoutExposingOwnerJsonMetadata() throws NoSuchFieldException {
        var row = insert(11L, 3L, 100L, "NEW");
        row.setAddressSnapshot("private snapshot"); row.setOutsourceRequestId(99L);
        var fields = BusinessModelIntrospector.businessFields(SiteSurveyEntityDO.class);
        var values = BusinessModelIntrospector.readValues(row, fields);
        assertEquals("NEW", values.get("code")); assertEquals(100L, values.get("projectId"));
        assertEquals(false, values.get("cabinetReady"));
        assertEquals("project_id", BusinessModelIntrospector.requireColumn(fields, "projectId"));
        assertEquals("survey_date", BusinessModelIntrospector.requireColumn(fields, "surveyDate"));
        assertFalse(values.containsKey("addressSnapshot")); assertFalse(values.containsKey("outsourceRequestId"));
        assertFalse(values.containsKey("id")); assertFalse(values.containsKey("version"));
        assertFalse(values.containsKey("selectedMaterials"));
        for (var field : declaration.descriptor().fields()) {
            if (fields.stream().anyMatch(column -> column.code().equals(field.code()))) {
                assertDoesNotThrow(() -> BusinessModelIntrospector.requireColumn(fields, field.code()), field.code());
            } else {
                // Child facts are read under the aggregate; they never acquire a fictitious parent SQL column.
                var property=SiteSurveyEntityDO.class.getDeclaredField(field.code());
                assertFalse(property.getAnnotation(TableField.class).exist(),field.code());
                assertThrows(BusinessContractException.class,
                        () -> BusinessModelIntrospector.requireColumn(fields,field.code()));
                assertTrue(SiteSurveyEntityProvider.FIELDS.fields().stream().anyMatch(child -> child.code().equals(field.code())));
            }
        }
        var json = JsonUtils.parseTree(JsonUtils.toJsonString(row));
        for (String hidden : List.of("id", "version", "projectId", "code", "name", "surveyDate", "surveyorUserId",
                "location", "status", "outsourceRequired", "outsourceRequestId", "addressSnapshot", "confirmedAt", "archivedAt"))
            assertFalse(json.has(hidden), "Old Owner JSON must still hide " + hidden);
        assertEquals("AC", json.get("powerSupply").asText());
        assertEquals(false, json.get("cabinetReady").asBoolean());
        // The uppercase Owner field/form contract remains distinct from the model-only exposure marker.
        var ownerFields = SiteSurveyEntityProvider.FIELDS.fields().stream().map(EntityField::code).toList();
        assertFalse(ownerFields.contains("projectId")); assertFalse(ownerFields.contains("code"));
        assertTrue(ownerFields.contains("selectedMaterials"));
    }

    @Test void genericReadUsesCurrentMapperAndRequiresBothOwnerPermissionAndProjectScope() {
        insert(11L, 3L, 100L, "NEW"); insert(12L, 3L, 101L, "OTHER_PROJECT"); insert(13L, 4L, 100L, "OTHER_TENANT");
        var ref = EntityDataRef.current(new EntityRef(3L, "SOL", "siteSurvey", 11L));
        var data = access.read(ref, actor, "detail");
        assertTrue(data.available()); assertEquals("NEW", data.fieldValues().get("code")); assertEquals(4L, data.concurrencyBasis());
        assertThrows(RuntimeException.class, () -> access.read(EntityDataRef.current(new EntityRef(3L, "SOL", "siteSurvey", 12L)), actor, "detail"));
        assertThrows(RuntimeException.class, () -> access.read(EntityDataRef.current(new EntityRef(3L, "SOL", "siteSurvey", 13L)), actor, "detail"));
        assertThrows(IllegalArgumentException.class, () -> access.read(EntityDataRef.current(new EntityRef(4L, "SOL", "siteSurvey", 11L)), actor, "detail"));
        when(permissions.hasAnyPermissions(9L, "pms:sol-site-survey:query")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> access.read(ref, actor, "detail"));
    }

    @Test void modelFunctionPermissionAloneCannotReplaceOwnerScopeAndMissingPermissionDeniesPages() {
        insert(11L, 3L, 100L, "NEW");
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L, 1L, Set.of(), Set.of()));
        assertThrows(RuntimeException.class, () -> access.read(EntityDataRef.current(
                new EntityRef(3L, "SOL", "siteSurvey", 11L)), actor, "detail"));
        when(modelPermissions.hasAnyPermissions(9L, "pms:sol-site-survey:query")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> access.query(new BusinessEntityPageQuery(
                "list", "SOL", "siteSurvey", List.of(), 20, null), actor));
    }

    @Test void existingTrustedObserverStillReadsOnlyItsTenantAndOwnerAliasIsExact() {
        insert(11L, 3L, 100L, "NEW"); insert(12L, 3L, 101L, "OTHER_PROJECT"); insert(13L, 4L, 100L, "OTHER_TENANT");
        var observer = new EntityActor(3L, 0L, EntityActor.SYSTEM_OBSERVER);
        assertEquals(2, access.query(new BusinessEntityPageQuery("facts", "SOL", "siteSurvey", List.of(), 20, null), observer).members().size());
        assertFalse(access.read(EntityDataRef.current(new EntityRef(3L, "SOL", "siteSurvey", 13L)), observer, "facts").available());
        assertThrows(IllegalArgumentException.class, () -> new EntityActor(3L, 0L, "untrusted-http"));
        var policy = new SiteSurveyBusinessScopePolicy(mock(SiteSurveyEntityProvider.class), scopes);
        assertTrue(policy.supports("SOL", "siteSurvey"));
        assertFalse(policy.supports("SOL", "SITE_SURVEY"));
        assertFalse(policy.supports("PRJ", "siteSurvey"));
    }

    @Test void genericPageIntersectsCallerFilterWithOwnerProjectsAndFailsClosedOnEmptyScope() {
        insert(11L, 3L, 100L, "NEW"); insert(1L, 3L, 101L, "OTHER_PROJECT"); insert(2L, 4L, 100L, "OTHER_TENANT");
        var query = new BusinessEntityPageQuery("list", "SOL", "siteSurvey", List.of(
                new BusinessFieldFilter("cabinetReady", BusinessFieldFilter.Operator.EQ, List.of(false))), 1, null);
        var slice = access.query(query, actor);
        assertNull(slice.nextCursor(), "Only the scoped row may participate in pagination");
        var rows = slice.members();
        assertEquals(1, rows.size()); assertEquals(11L, rows.getFirst().ref().entityId());
        assertEquals("NEW", rows.getFirst().fieldValues().get("code"));
        var forged = new BusinessEntityPageQuery("list", "SOL", "siteSurvey", List.of(
                new BusinessFieldFilter("projectId", BusinessFieldFilter.Operator.EQ, List.of(101L))), 20, null);
        assertTrue(access.query(forged, actor).members().isEmpty());
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of());
        assertTrue(access.query(query, actor).members().isEmpty());
        when(scopes.resolveAllCurrent(any())).thenReturn(null);
        assertTrue(access.query(query, actor).members().isEmpty());
    }

    private record Recovery(BusinessOperationDispatcher dispatcher,
            OperationExecutionStore store,
            BusinessOperationReceipt receipt) { }

    @SuppressWarnings("unchecked") private Recovery recovery() {
        ObjectProvider<BusinessModelContributor> contributors = mock(ObjectProvider.class);
        when(contributors.orderedStream()).thenAnswer(call -> Stream.of((BusinessModelContributor) () -> List.of(declaration)));
        var persistence = new BusinessEntityPersistenceRegistry(contributors);
        var catalog = mock(BusinessModelCatalog.class);
        when(catalog.require("SOL", "siteSurvey")).thenReturn(declaration.descriptor());
        when(modelPermissions.hasAnyPermissions(9L, "pms:business-model:operate")).thenReturn(true);
        when(permissions.hasAnyPermissions(9L, "pms:sol-site-survey:create")).thenReturn(true);
        when(scopes.resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 100L, ProjectScopeApi.ACTION_MANAGE)))
                .thenReturn(new ProjectScopeResult(100L, 1L, Set.of(100L), Set.of()));
        ObjectProvider<BusinessOwnerPermissionPolicy> policies = mock(ObjectProvider.class);
        when(policies.stream()).thenAnswer(call -> Stream.of(new SiteSurveyBusinessPermissionPolicy(permissions)));
        var store = mock(OperationExecutionStore.class);
        var receipt = new BusinessOperationReceipt(
                ReceiptOutcome.SAVED,
                new EntityRef(3L, "SOL", "siteSurvey", 11L), 0L, List.of(), null, null, "create", 1);
        when(store.findExisting(any(), any())).thenReturn(Optional.of(
                new OperationExecutionStore.StoredExecution("digest", "COMPLETED", receipt)));
        var owner = new SiteSurveyBusinessApplicationService(
                () -> new AbstractBusinessApplicationService.ResolvedCaller(3L, 9L, "recovery"),
                catalog, persistence, new PermissionBusinessAccessGuard(modelPermissions, policies), store,
                mock(BusinessEventPort.class),
                mock(OperationAuditApi.class),
                new DataSourceTransactionManager(dataSource),
                mock(SiteSurveyEntityDomainCommands.class),
                mapper, permissions, scopes, mock(Validator.class));
        var dispatcher = new BusinessOperationDispatcher(persistence,
                mock(DefaultBusinessApplicationService.class), List.of(owner));
        return new Recovery(dispatcher, store, receipt);
    }

    private BusinessOperationReceipt recover(Recovery recovery) {
        // Each HTTP recovery uses a fresh SqlSession; this fixture deliberately reuses one.
        session.clearCache();
        return new TransactionTemplate(
                new DataSourceTransactionManager(dataSource)).execute(status ->
                recovery.dispatcher().recoverReceipt("SOL", "siteSurvey", "create", 1, "lost"));
    }

    @Test void nativeRecoveryKeepsOriginalReceiptAndUsesTheObjectsCurrentOwner() throws Exception {
        insert(11L, 3L, 100L, "RECOVERY");
        var recovery = recovery();
        assertEquals(recovery.receipt(), recover(recovery));
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "UPDATE sol_site_survey SET project_id=? WHERE id=?")) {
            statement.setLong(1, 101L); statement.setLong(2, 11L); assertEquals(1, statement.executeUpdate());
        }
        var denied = assertThrows(ServiceException.class, () -> recover(recovery));
        assertEquals(403, denied.getCode());
        when(scopes.resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 101L, ProjectScopeApi.ACTION_MANAGE)))
                .thenReturn(new ProjectScopeResult(101L, 1L, Set.of(101L), Set.of()));
        assertEquals(recovery.receipt(), recover(recovery));
        assertEquals(0L, recovery.receipt().newConcurrencyBasis());
        verify(scopes, atLeastOnce()).resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 101L, ProjectScopeApi.ACTION_MANAGE));
    }

    @Test void nativeRecoveryRequiresTheOriginalOperationPermissionBeforeReadingTheLedger() {
        insert(11L, 3L, 100L, "RECOVERY");
        var recovery = recovery();
        when(permissions.hasAnyPermissions(9L, "pms:sol-site-survey:create")).thenReturn(false);
        assertEquals("ACCESS_DENIED", assertThrows(BusinessContractException.class,
                () -> recover(recovery)).getErrorCode());
        verifyNoInteractions(recovery.store());
    }

    @Test void nativeRecoveryPreservesTheSoftDeletedOwnersIdentityContract() {
        insert(11L, 3L, 100L, "RECOVERY");
        var recovery = recovery();
        assertEquals(1, mapper.deleteById(11L));
        assertNull(mapper.selectById(11L));
        assertEquals(recovery.receipt(), recover(recovery));
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L, 1L, Set.of(), Set.of()));
        assertEquals(403, assertThrows(ServiceException.class, () -> recover(recovery)).getCode());
    }

    @Test void legacyUnboundReceiptsCannotBeRelabeledAsTheRequestedOperation() {
        insert(11L, 3L, 100L, "RECOVERY");
        var recovery = recovery();
        var old = new BusinessOperationReceipt(ReceiptOutcome.SAVED, recovery.receipt().entityRef(), 0L,
                List.of(), null, null);
        when(recovery.store().findExisting(any(), any())).thenReturn(Optional.of(
                new OperationExecutionStore.StoredExecution("digest", "COMPLETED", old)));
        assertEquals("IDEMPOTENCY_INTENT_MISMATCH", assertThrows(BusinessContractException.class,
                () -> recover(recovery)).getErrorCode());
        verifyNoInteractions(scopes);
    }
}
