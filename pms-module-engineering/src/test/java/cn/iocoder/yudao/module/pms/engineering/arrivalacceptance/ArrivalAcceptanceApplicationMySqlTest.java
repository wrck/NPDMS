package cn.iocoder.yudao.module.pms.engineering.arrivalacceptance;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactAcceptedMessage;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactArchivedMessage;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.ArrivalController;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalLineDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalAcceptanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalLineMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence.ArtifactCallbackHandler;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence.ArtifactCallbackResult;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.FileArtifactFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectQualificationPort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetExpectation;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ArrivalAcceptanceApplicationMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ArrivalAcceptanceApplicationMySqlTest {

    static final long ACTOR_ID = 8L;
    static final long ARTIFACT_ID = 40L;
    static final int FILE_VERSION = 5;
    static final String FILE_REFERENCE = "ARRIVAL-RECEIPT-1";
    static final String FILE_HASH = "a".repeat(64);

    @Resource JdbcTemplate jdbcTemplate;
    @Resource ArrivalAcceptanceApplicationService applicationService;
    @Resource ArtifactCallbackHandler callbackHandler;
    @Resource ArrivalLineMapper lineMapper;
    @Resource DeliveryEvidenceMapper evidenceMapper;
    @Resource DeliveryEvidenceRevisionMapper revisionMapper;
    @Resource TestOwnerPorts ownerPorts;

    long tenantId;
    long projectId;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
        String database = environment.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = environment.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=UTF-8&nullCatalogMeansCurrent=true");
        registry.add("spring.datasource.username", () -> required(environment, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(environment, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.engineering");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        long suffix = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        tenantId = 979_700_000_000L + suffix;
        projectId = 979_800_000_000L + suffix;
        TenantContextHolder.setTenantId(tenantId);
        ownerPorts.reset(projectId);
    }

    @AfterEach
    void tearDown() {
        deleteTenantData(jdbcTemplate, tenantId);
        TenantContextHolder.clear();
    }

    @Test
    void fullApplicationLifecyclePersistsFiveTableFactOutboxAndReplay() {
        assertEquals(5, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema=DATABASE() AND table_name IN "
                        + "('imp_arrival_acceptance','imp_arrival_line','imp_arrival_difference',"
                        + "'imp_delivery_evidence','imp_delivery_evidence_revision')", Integer.class));

        ArrivalAcceptanceDO draft = createDraft("create-full");
        EvidenceFixture evidence = attachAcceptedDeviceAndEvidence(draft);
        ArrivalAcceptanceApplicationService.SubmissionResult submitted = applicationService.submit(
                new ArrivalAcceptanceApplicationService.SubmitCommand(
                        tenantId, draft.getId(), ACTOR_ID, 0, "submit-full", "corr-submit-full"));
        assertEquals("ACCEPTED", submitted.status());

        ArrivalAcceptanceApplicationService.ConfirmCommand command =
                new ArrivalAcceptanceApplicationService.ConfirmCommand(
                        tenantId, draft.getId(), ACTOR_ID, 1, "confirm-full", "corr-confirm-full");
        ArrivalAcceptanceApplicationService.ConfirmationResult confirmed = applicationService.confirm(command);
        ArrivalAcceptanceApplicationService.ConfirmationResult replay = applicationService.confirm(command);

        assertEquals(confirmed.arrivalAcceptanceId(), replay.arrivalAcceptanceId());
        assertEquals(confirmed.eventId(), replay.eventId());
        assertEquals(confirmed.projectFactVersion(), replay.projectFactVersion());
        assertEquals(confirmed.confirmedAt().truncatedTo(ChronoUnit.MILLIS), replay.confirmedAt());
        assertEquals("CONFIRMED", confirmed.status());
        assertEquals(1L, confirmed.projectFactVersion());
        assertEquals("CONFIRMED", scalar("SELECT status FROM imp_arrival_acceptance WHERE id=?", draft.getId()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT project_fact_version FROM imp_arrival_acceptance WHERE id=?", Long.class, draft.getId()));
        assertEquals("PUBLISHED_PENDING_ACC", scalar(
                "SELECT acc_sync_status FROM imp_delivery_evidence WHERE id=?", evidence.evidenceId()));
        assertEquals(1, count("SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=? "
                + "AND event_type='ImplementationEvidencePublished'", tenantId));
        assertEquals(3, count("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? "
                + "AND aggregate_type='ArrivalAcceptance'", tenantId));
        assertEquals(3, count("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? "
                + "AND status='COMPLETED'", tenantId));
        assertTrue(count("SELECT COUNT(*) FROM imp_arrival_line WHERE tenant_id=?", tenantId) > 0);
        assertEquals(0, count("SELECT COUNT(*) FROM imp_arrival_difference WHERE tenant_id=?", tenantId));
    }

    @Test
    void ownerFailureRollsBackPlatformClaimAndLeavesCandidateUnchanged() {
        ArrivalAcceptanceDO draft = createDraft("create-owner-failure");
        EvidenceFixture evidence = attachAcceptedDeviceAndEvidence(draft);
        ownerPorts.fileUnavailable = true;

        assertThrows(IllegalStateException.class, () -> applicationService.submit(
                new ArrivalAcceptanceApplicationService.SubmitCommand(
                        tenantId, draft.getId(), ACTOR_ID, 0,
                        "submit-owner-failure", "corr-owner-failure")));

        assertEquals("DRAFT", scalar("SELECT status FROM imp_arrival_acceptance WHERE id=?", draft.getId()));
        assertEquals(0, count("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? "
                + "AND idempotency_key='submit-owner-failure'", tenantId));
        assertEquals(0, count("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? "
                + "AND correlation_id='corr-owner-failure'", tenantId));
        assertEquals(0, count("SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=?", tenantId));
        assertEquals("NOT_PUBLISHED", scalar(
                "SELECT acc_sync_status FROM imp_delivery_evidence WHERE id=?", evidence.evidenceId()));
    }

    @Test
    void ineligibleProjectAndMismatchedRuntimeTenantHaveZeroCommandSideEffects() {
        ownerPorts.projectEligible = false;
        assertThrows(IllegalStateException.class, () -> createDraft("create-ineligible"));
        assertEquals(0, count("SELECT COUNT(*) FROM imp_arrival_acceptance WHERE tenant_id=?", tenantId));
        assertEquals(0, count("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=?", tenantId));

        ArrivalAcceptanceDO draft;
        ownerPorts.projectEligible = true;
        draft = createDraft("create-cross-tenant-fixture");
        EvidenceFixture evidence = attachAcceptedDeviceAndEvidence(draft);
        TenantContextHolder.setTenantId(tenantId + 1);
        assertThrows(IllegalArgumentException.class, () -> callbackHandler.handle(
                accepted("accepted-cross-tenant", evidence)));
        assertEquals(0, count("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? "
                + "AND idempotency_key='accepted-cross-tenant'", tenantId));
    }

    @Test
    void callbackOutOfOrderDuplicateAndArchiveRecoveryAreIdempotent() {
        ArrivalAcceptanceDO draft = createDraft("create-callback");
        EvidenceFixture evidence = attachAcceptedDeviceAndEvidence(draft);
        applicationService.submit(new ArrivalAcceptanceApplicationService.SubmitCommand(
                tenantId, draft.getId(), ACTOR_ID, 0, "submit-callback", "corr-submit-callback"));
        applicationService.confirm(new ArrivalAcceptanceApplicationService.ConfirmCommand(
                tenantId, draft.getId(), ACTOR_ID, 1, "confirm-callback", "corr-confirm-callback"));

        ArtifactCallbackResult outOfOrder = callbackHandler.handle(new ArtifactArchivedMessage(
                "archive-before-accepted", tenantId, evidence.evidenceId(), 1,
                ARTIFACT_ID, FILE_VERSION, "archive-1", LocalDateTime.now(), "corr-archive-early"));
        ArtifactCallbackResult accepted = callbackHandler.handle(accepted("accepted-1", evidence));
        ArtifactCallbackResult replay = callbackHandler.handle(accepted("accepted-1", evidence));
        ArtifactCallbackResult archived = callbackHandler.handle(new ArtifactArchivedMessage(
                "archived-1", tenantId, evidence.evidenceId(), 1,
                ARTIFACT_ID, FILE_VERSION, "archive-1", LocalDateTime.now(), "corr-archive"));

        assertEquals(ArtifactCallbackResult.Outcome.IGNORED_OUT_OF_ORDER, outOfOrder.outcome());
        assertEquals(ArtifactCallbackResult.Outcome.APPLIED, accepted.outcome());
        assertEquals(accepted, replay);
        assertEquals(ArtifactCallbackResult.Outcome.APPLIED, archived.outcome());
        assertEquals("ARCHIVED", scalar(
                "SELECT acc_sync_status FROM imp_delivery_evidence WHERE id=?", evidence.evidenceId()));
        assertEquals(1, count("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? "
                + "AND correlation_id='corr-accepted'", tenantId));
    }

    @Test
    void legacyArrivalTableAndControllerContractRemainUnchanged() {
        assertEquals(1, count("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name='pms_eng_arrival'"));
        assertEquals("pms_eng_arrival", ArrivalDO.class.getAnnotation(TableName.class).value());
        assertEquals("/pms/eng-arrival",
                ArrivalController.class.getAnnotation(RequestMapping.class).value()[0]);
    }

    ArrivalAcceptanceDO createDraft(String suffix) {
        return applicationService.createDraft(new ArrivalAcceptanceApplicationService.CreateDraftCommand(
                tenantId, projectId, ACTOR_ID, "B-" + suffix, "L-" + suffix,
                LocalDateTime.of(2026, 8, 30, 9, 0), "签收人", 8L,
                "key-" + suffix, "corr-" + suffix));
    }

    EvidenceFixture attachAcceptedDeviceAndEvidence(ArrivalAcceptanceDO draft) {
        DeliveryEvidenceDO evidence = new DeliveryEvidenceDO();
        evidence.setTenantId(tenantId);
        evidence.setProjectId(projectId);
        evidence.setSourceRequirement("EXE-01");
        evidence.setSourceObjectType("ARRIVAL_ACCEPTANCE");
        evidence.setSourceObjectId(draft.getId());
        evidence.setCurrentRevisionNo(1);
        evidence.setAccSyncStatus("NOT_PUBLISHED");
        evidence.setAccRetryCount(0);
        evidence.setVersion(0);
        evidence.setCreator(String.valueOf(ACTOR_ID));
        evidence.setUpdater(String.valueOf(ACTOR_ID));
        evidenceMapper.insert(evidence);

        DeliveryEvidenceRevisionDO revision = new DeliveryEvidenceRevisionDO();
        revision.setTenantId(tenantId);
        revision.setEvidenceId(evidence.getId());
        revision.setRevisionNo(1);
        revision.setFileArtifactId(ARTIFACT_ID);
        revision.setFileReferenceId(FILE_REFERENCE);
        revision.setFileVersionNo(FILE_VERSION);
        revision.setFileScopeVersion(6L);
        revision.setFileFactVersion("{\"artifactVersion\":2,\"referenceVersion\":3,"
                + "\"availabilityVersion\":4}");
        revision.setFileHash(FILE_HASH);
        revision.setSourceRecordId(draft.getId());
        revision.setSourceVersion(0L);
        revision.setCreator(String.valueOf(ACTOR_ID));
        revisionMapper.insert(revision);

        ArrivalLineDO line = new ArrivalLineDO();
        line.setTenantId(tenantId);
        line.setArrivalAcceptanceId(draft.getId());
        line.setLineNo(1);
        line.setLineRevision(1);
        line.setScopeType("DEVICE");
        line.setDeviceId(11L);
        line.setDeviceAssignmentVersion(9L);
        line.setExpectedQuantity(BigDecimal.ONE);
        line.setAcceptedQuantity(BigDecimal.ONE);
        line.setUnit("台");
        line.setStatus("ACCEPTED");
        line.setCurrentMarker(1);
        line.setVersion(0);
        line.setCreator(String.valueOf(ACTOR_ID));
        line.setUpdater(String.valueOf(ACTOR_ID));
        lineMapper.insert(line);
        return new EvidenceFixture(evidence.getId(), revision.getId(), line.getId());
    }

    private ArtifactAcceptedMessage accepted(String eventId, EvidenceFixture evidence) {
        return new ArtifactAcceptedMessage(eventId, tenantId, evidence.evidenceId(), 1,
                ARTIFACT_ID, FILE_VERSION, "review-1",
                LocalDateTime.of(2026, 8, 30, 11, 0), "corr-accepted");
    }

    int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }

    String scalar(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, String.class, arguments);
    }

    static void deleteTenantData(JdbcTemplate jdbcTemplate, long tenantId) {
        jdbcTemplate.update("DELETE FROM plt_outbox_event WHERE tenant_id=?", tenantId);
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE tenant_id=?", tenantId);
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE tenant_id=?", tenantId);
        jdbcTemplate.update("DELETE FROM imp_arrival_difference WHERE tenant_id=?", tenantId);
        jdbcTemplate.update("DELETE FROM imp_arrival_line WHERE tenant_id=?", tenantId);
        jdbcTemplate.update("UPDATE imp_arrival_acceptance SET evidence_id=NULL,evidence_revision=NULL "
                + "WHERE tenant_id=?", tenantId);
        jdbcTemplate.update("DELETE FROM imp_delivery_evidence_revision WHERE tenant_id=?", tenantId);
        jdbcTemplate.update("DELETE FROM imp_delivery_evidence WHERE tenant_id=?", tenantId);
        jdbcTemplate.update("DELETE FROM imp_arrival_acceptance WHERE tenant_id=?", tenantId);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    record EvidenceFixture(long evidenceId, long revisionId, long lineId) {
    }

    static final class TestOwnerPorts implements ProjectQualificationPort, DeliveryScopePort,
            DeviceScopeFactPort, FileArtifactFactPort {

        volatile long projectId;
        volatile boolean projectEligible;
        volatile boolean fileUnavailable;

        void reset(long value) {
            projectId = value;
            projectEligible = true;
            fileUnavailable = false;
        }

        @Override
        public ProjectQualificationFact inspect(Long tenantId, Long requestedProjectId, Long actorUserId) {
            return qualification(requestedProjectId, actorUserId);
        }

        @Override
        public ProjectQualificationFact lockAndRevalidate(RevalidationCommand command) {
            return qualification(command.projectId(), command.actorUserId());
        }

        private ProjectQualificationFact qualification(Long requestedProjectId, Long actorUserId) {
            if (!projectEligible || requestedProjectId == null || requestedProjectId != projectId
                    || !Long.valueOf(ACTOR_ID).equals(actorUserId)) {
                throw new IllegalStateException("test project is not ACTIVE/S4 or actor is not project manager");
            }
            return new ProjectQualificationFact(projectId, ACTOR_ID, Set.of("PROJECT_MANAGER"),
                    "ACTIVE", "S4", 5, 6L, 7L);
        }

        @Override
        public AssignedScope inspectAssignedScope(Long requestedProjectId) {
            return assignedScope(requestedProjectId);
        }

        @Override
        public AssignedScope lockAndRevalidate(Long requestedProjectId, Long expectedScopeVersion) {
            if (!Long.valueOf(8L).equals(expectedScopeVersion)) {
                throw new IllegalStateException("test delivery scope is stale");
            }
            return assignedScope(requestedProjectId);
        }

        private AssignedScope assignedScope(Long requestedProjectId) {
            return new AssignedScope(requestedProjectId, 8L, List.of(
                    new AssignedLine(20L, BigDecimal.ONE, "台", "PRODUCT-1", "MODEL-1", Set.of("SN-1"))));
        }

        @Override
        public DeviceScopeFact resolveBySerials(Long tenantId, Long requestedProjectId, Set<String> serialNumbers) {
            return deviceScope(requestedProjectId, serialNumbers);
        }

        @Override
        public DeviceScopeFact lockAndRevalidate(Long tenantId, Long requestedProjectId,
                                                 List<ExpectedDeviceFact> expectedDevices) {
            return deviceScope(requestedProjectId, expectedDevices.stream()
                    .map(ExpectedDeviceFact::serialNumber).collect(java.util.stream.Collectors.toSet()));
        }

        private DeviceScopeFact deviceScope(Long requestedProjectId, Set<String> serialNumbers) {
            if (!Set.of("SN-1").equals(serialNumbers)) {
                throw new IllegalStateException("test device scope is stale");
            }
            return new DeviceScopeFact(requestedProjectId,
                    List.of(new DeviceFact(11L, "SN-1", requestedProjectId, 9L)));
        }

        @Override
        public FileArtifactVersionFact inspectArrivalEvidence(Long artifactId, Integer versionNo,
                                                               Long arrivalAcceptanceId, String referenceKey) {
            return fileFact();
        }

        @Override
        public FileArtifactVersionFact lockAndRevalidateArrivalEvidence(ArrivalEvidenceExpectation expectation) {
            if (fileUnavailable) throw new IllegalStateException("test PLT owner unavailable");
            return fileFact();
        }

        private FileArtifactVersionFact fileFact() {
            return new FileArtifactVersionFact(ARTIFACT_ID, FILE_VERSION, FILE_REFERENCE,
                    "RECEIPT", "receipt.pdf", 128L, "application/pdf", FILE_HASH,
                    "AVAILABLE", "ACTIVE", new FileFactVersion(2, 3, 4), 6L);
        }

        @Override
        public List<FileReferenceSetFact> inspectReferenceSets(List<FileReferenceSetKey> keys) {
            return List.of();
        }

        @Override
        public List<FileReferenceSetFact> lockAndRevalidateReferenceSets(
                List<FileReferenceSetExpectation> expectations) {
            return List.of();
        }
    }

    @SpringBootConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan({"cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean TestOwnerPorts testOwnerPorts() {
            return new TestOwnerPorts();
        }

        @Bean ArrivalAcceptanceApplicationService arrivalAcceptanceApplicationService(
                ArrivalAcceptanceMapper acceptanceMapper, ArrivalLineMapper lineMapper,
                cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalDifferenceMapper differenceMapper,
                DeliveryEvidenceMapper evidenceMapper, DeliveryEvidenceRevisionMapper revisionMapper,
                ProjectQualificationPort projectPort, DeliveryScopePort deliveryPort,
                DeviceScopeFactPort devicePort, FileArtifactFactPort filePort,
                PlatformCommandExecutionApi commandExecutionApi) {
            return new ArrivalAcceptanceApplicationService(acceptanceMapper, lineMapper, differenceMapper,
                    evidenceMapper, revisionMapper, projectPort, deliveryPort, devicePort, filePort,
                    commandExecutionApi);
        }

        @Bean ArtifactCallbackHandler artifactCallbackHandler(
                DeliveryEvidenceMapper evidenceMapper, DeliveryEvidenceRevisionMapper revisionMapper,
                PlatformCommandExecutionApi commandExecutionApi) {
            return new ArtifactCallbackHandler(evidenceMapper, revisionMapper, commandExecutionApi);
        }
    }
}
