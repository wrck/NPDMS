package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacySnapshotDO;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Opt-in real MySQL checks. All fixtures use a fresh synthetic source and are removed after each test. */
@EnabledIfEnvironmentVariable(named = "LEGACY_DETAIL_MYSQL", matches = "true")
class ProjectLegacyDetailMySqlTest {
    private static final long PROJECT = 900000000000000001L;
    private static final String CONTRACT = "SYNTHETIC-LEGACY-DETAIL";
    private final ProjectAccessActor actor = new ProjectAccessActor(1L, 1L);
    private final LegacyDetailCatalog catalog = new LegacyDetailCatalog();
    private final String sourceKey = Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits()));
    private JdbcTemplate jdbc;
    private ProjectLegacyDetailMapper mapper;
    private DriverManagerDataSource datasource;

    @BeforeEach
    void connect() throws Exception {
        String url = System.getenv("LEGACY_DETAIL_MYSQL_URL");
        assertTrue(url != null && url.startsWith("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?"),
                "Only the explicitly designated local database is allowed");
        datasource = new DriverManagerDataSource(url, System.getenv("LEGACY_DETAIL_MYSQL_USER"),
                System.getenv("LEGACY_DETAIL_MYSQL_PASSWORD"));
        datasource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        jdbc = new JdbcTemplate(datasource);
        assertEquals("npdms_domain_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM proj_legacy_detail_source WHERE source_project_key=?",
                Integer.class, sourceKey));
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(datasource);
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/legacydetail/ProjectLegacyDetailMapper.xml"));
        mapper = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(ProjectLegacyDetailMapper.class);
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void cleanOnlyOwnFixture() {
        try {
            if (jdbc != null) {
                var ids = jdbc.queryForList("SELECT id FROM proj_legacy_detail_source WHERE tenant_id=1 AND project_id=? AND source_project_key=?",
                        Long.class, PROJECT, sourceKey);
                for (long id : ids) {
                    jdbc.update("DELETE r FROM proj_legacy_detail_record r JOIN proj_legacy_detail_snapshot s ON s.id=r.snapshot_id "
                            + "WHERE r.tenant_id=1 AND s.tenant_id=1 AND s.source_id=?", id);
                    jdbc.update("DELETE FROM proj_legacy_detail_snapshot WHERE tenant_id=1 AND source_id=?", id);
                    jdbc.update("DELETE FROM proj_legacy_detail_source WHERE tenant_id=1 AND id=? AND project_id=? AND source_project_key=?",
                            id, PROJECT, sourceKey);
                }
            }
        } finally { TenantContextHolder.clear(); }
    }

    private ProjectLegacyDetailService service(ProjectLegacyDetailMapper persistence) {
        var projects = mock(ProjectManualCreationService.class);
        var contracts = mock(ProjectContractQueryApi.class);
        when(contracts.getCurrentContractNumbers(PROJECT)).thenReturn(Set.of(CONTRACT));
        var target = new ProjectLegacyDetailService(projects, contracts, persistence, new LegacyDetailNormalizer(catalog), catalog);
        var transaction = new TransactionInterceptor();
        transaction.setTransactionManager(new DataSourceTransactionManager(datasource));
        transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(transaction);
        return (ProjectLegacyDetailService) proxy.getProxy();
    }

    private LegacyDetailInput input(String batch, String name, String expected) {
        var counts = new TreeMap<String, Integer>();
        catalog.domains().forEach(d -> counts.put(d.code(), 0));
        counts.put("project", 1);
        var values=new LinkedHashMap<String,String>();
        catalog.require("project").fields().forEach(field->values.put(field.key(),null));
        values.put("projectId",sourceKey);values.put("projectName",name);
        return new LegacyDetailInput(batch, sourceKey, CONTRACT, LocalDateTime.now(), expected, counts,
                List.of(new LegacyDetailInput.SourceRecord("project", sourceKey, null, null, null, values)));
    }

    @Test
    void failedHeadUpdateRollsBackSourceSnapshotAndRecordsTogether() {
        var failing = mock(ProjectLegacyDetailMapper.class, org.mockito.AdditionalAnswers.delegatesTo(mapper));
        doReturn(0).when(failing).updateHead(any());
        assertThrows(RuntimeException.class, () -> service(failing).ingest(PROJECT, input("rollback", "Synthetic", null), actor));
        assertNull(mapper.selectSourceForUpdate(new SourceIdentityQuery(1L, sourceKey)));
        var snapshot = org.mockito.ArgumentCaptor.forClass(ProjectLegacySnapshotDO.class);
        verify(failing).appendSnapshot(snapshot.capture());
        assertNull(mapper.selectSnapshot(new SnapshotQuery(1L, snapshot.getValue().getId())));
        assertEquals(0, mapper.countRecords(new RecordsPageQuery(1L, snapshot.getValue().getId(), "project", null, null, null, 0, 20)));
    }

    @Test
    void appendPreservesOldSnapshotAndMapperEnforcesTenantAndVersion() {
        var service = service(mapper);
        var first = service.ingest(PROJECT, input("first", "Synthetic old", null), actor);
        var second = service.ingest(PROJECT, input("second", "Synthetic new", first.checksum()), actor);
        assertNotEquals(first.snapshotId(), second.snapshotId());
        assertEquals("Synthetic old", service.records(PROJECT, first.sourceId(), first.snapshotId(), "project", null, null, null, 1, 20, actor)
                .getList().getFirst().values().get("projectName"));
        assertEquals("Synthetic new", service.records(PROJECT, second.sourceId(), second.snapshotId(), "project", null, null, null, 1, 20, actor)
                .getList().getFirst().values().get("projectName"));
        assertTrue(mapper.selectProjectSources(new ProjectSourcesQuery(2L, PROJECT)).isEmpty());
        assertNull(mapper.selectSnapshot(new SnapshotQuery(2L, first.snapshotId())));
        assertEquals(0, mapper.countRecords(new RecordsPageQuery(2L, first.snapshotId(), "project", null, null, null, 0, 20)));
        assertEquals(0, mapper.updateHead(new SourceHeadUpdate(1L, first.sourceId(), 0L, first.snapshotId())));
        assertEquals(second.snapshotId(), mapper.selectSourceForUpdate(new SourceIdentityQuery(1L, sourceKey)).getCurrentSnapshotId());
    }
}
