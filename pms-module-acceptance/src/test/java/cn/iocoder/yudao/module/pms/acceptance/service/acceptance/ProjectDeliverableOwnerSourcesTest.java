package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionResultDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.AcceptanceActivityIdLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.AcceptanceActivityScopeQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.AcceptanceReportIdLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionResultMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.SatisfactionTaskScopeQuery;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenMaterialView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 投影来源事实重验（P06R）：绑定关系指向统一要求实例 ID，投影身份由 requestPayloadJson 携带，
 * 材料文件按 inspectDocument 返回的自身锚构造锁查询（兼容新旧锚，不可变历史不重挂）。
 */
class ProjectDeliverableOwnerSourcesTest {

    final PlatformDeliveryRequirementApi platform = mock(PlatformDeliveryRequirementApi.class);
    final AcceptanceActivityMapper activities = mock(AcceptanceActivityMapper.class);
    final AcceptanceReportVersionMapper reports = mock(AcceptanceReportVersionMapper.class);
    final SatisfactionCollectionTaskMapper tasks = mock(SatisfactionCollectionTaskMapper.class);
    final SatisfactionResultMapper results = mock(SatisfactionResultMapper.class);
    final FileEvidenceApi files = mock(FileEvidenceApi.class);
    final ProjectDeliverableOwnerSources service =
            new ProjectDeliverableOwnerSources(platform, activities, reports, tasks, results, files);

    final TemplateFrozenView view = new TemplateFrozenView(31L, 9L, "D1", "验收报告", "S5", null, null,
            null, true, 1, null, "SATISFIED", "{}", 1);
    final AcceptanceActivityDO activity = new AcceptanceActivityDO();
    final AcceptanceReportVersionDO report = new AcceptanceReportVersionDO();
    final TemplateFrozenMaterialView material = new TemplateFrozenMaterialView(9001L, 31L, "FILE", 40L,
            400L, 1, "a".repeat(64), "report.pdf", null, null, null, "ACTIVE", "ARCHIVED", null, 0);

    static final String REPORT_PROJECTION =
            "{\"projectionKind\":\"ACCEPTANCE_REPORT\",\"reportVersionId\":55,\"reportVersionNo\":1}";
    static final String SATISFACTION_PROJECTION =
            "{\"projectionKind\":\"SATISFACTION_RESULT\",\"resultId\":55,\"resultVersion\":1}";

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(7L);
        activity.setId(51L);
        activity.setTenantId(7L);
        activity.setProjectId(9L);
        activity.setDeliverableId(31L);
        activity.setCurrentReportVersionId(55L);
        report.setId(55L);
        report.setTenantId(7L);
        report.setAcceptanceId(51L);
        report.setReportVersionNo(1);
        report.setReportStatus("EFFECTIVE");
        when(reports.selectById(55L)).thenReturn(report);
        when(reports.selectByIdForUpdate(new AcceptanceReportIdLockQuery(7L, 51L, 55L))).thenReturn(report);
        when(activities.selectByIdForUpdate(new AcceptanceActivityIdLockQuery(7L, 51L))).thenReturn(activity);
        when(platform.listMaterials(31L)).thenReturn(List.of(material));
        when(files.inspectDocument(7L, 40L)).thenReturn(new FileEvidenceApi.Document(40L, "ACC",
                "ACCEPTANCE_REPORT_VERSION", "55", "ACCEPTANCE_REPORT_ATTACHMENT", "report-slot", 400L, 1,
                "a".repeat(64), "report.pdf", true));
        when(files.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(true, "FILE_EVIDENCE_VALID", 1, 1, 1));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private TemplateFrozenSubmissionView projection(String payloadJson, List<Long> materialIds) {
        return new TemplateFrozenSubmissionView(1001L, 31L, "report:55",
                PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, "CURRENT", payloadJson, null,
                materialIds, null);
    }

    @Test
    void currentReportUsesItsExactOriginalFileReference() {
        var evidence = service.revalidate(view, projection(REPORT_PROJECTION, List.of(9001L)));
        assertTrue(evidence.valid());
        assertEquals(1, evidence.fileCount());
        verify(files).lockAndRevalidate(new FileEvidenceApi.Query(7L, 400L, 1, "ACC",
                "ACCEPTANCE_REPORT_VERSION", "55", "ACCEPTANCE_REPORT_ATTACHMENT", "report-slot", "a".repeat(64)));
        verify(reports, never()).updateById(any(AcceptanceReportVersionDO.class));
    }

    @Test
    void revokedOrReplacedReportCannotRemainAccepted() {
        report.setReportStatus("REVOKED");
        assertFalse(service.revalidate(view, projection(REPORT_PROJECTION, List.of(9001L))).valid());
        report.setReportStatus("EFFECTIVE");
        activity.setCurrentReportVersionId(56L);
        assertFalse(service.revalidate(view, projection(REPORT_PROJECTION, List.of(9001L))).valid());
        verifyNoInteractions(files);
    }

    @Test
    void anotherProjectOrDeliverableCannotReuseTheSource() {
        activity.setProjectId(10L);
        assertFalse(service.revalidate(view, projection(REPORT_PROJECTION, List.of(9001L))).valid());
        activity.setProjectId(9L);
        activity.setDeliverableId(32L);
        assertFalse(service.revalidate(view, projection(REPORT_PROJECTION, List.of(9001L))).valid());
        verifyNoInteractions(files);
    }

    @Test
    void unavailableOrChangedFileFailsDespiteEffectiveReport() {
        when(files.inspectDocument(7L, 40L)).thenReturn(null);
        assertEquals("DELIVERABLE_FILE_UNAVAILABLE",
                service.revalidate(view, projection(REPORT_PROJECTION, List.of(9001L))).reason());

        when(files.inspectDocument(7L, 40L)).thenReturn(new FileEvidenceApi.Document(40L, "ACC",
                "ACCEPTANCE_REPORT_VERSION", "55", "ACCEPTANCE_REPORT_ATTACHMENT", "report-slot", 400L, 1,
                "b".repeat(64), "report.pdf", true));
        assertEquals("DELIVERABLE_FILE_UNAVAILABLE",
                service.revalidate(view, projection(REPORT_PROJECTION, List.of(9001L))).reason());

        when(files.inspectDocument(7L, 40L)).thenReturn(new FileEvidenceApi.Document(40L, "ACC",
                "ACCEPTANCE_REPORT_VERSION", "55", "ACCEPTANCE_REPORT_ATTACHMENT", "report-slot", 400L, 1,
                "a".repeat(64), "report.pdf", true));
        when(files.lockAndRevalidate(any())).thenReturn(
                new FileEvidenceApi.Fact(false, "FILE_EVIDENCE_UNAVAILABLE", 2, 2, 2));
        assertEquals("FILE_EVIDENCE_UNAVAILABLE",
                service.revalidate(view, projection(REPORT_PROJECTION, List.of(9001L))).reason());
    }

    @Test
    void satisfactionMustStillBeThePassingCurrentResult() {
        var result = new SatisfactionResultDO();
        result.setId(55L);
        result.setTenantId(7L);
        result.setCollectionTaskId(60L);
        result.setResultVersion(1);
        result.setResultStatus("EFFECTIVE");
        result.setPassed(true);
        var task = new SatisfactionCollectionTaskDO();
        task.setId(60L);
        task.setTenantId(7L);
        task.setProjectId(9L);
        task.setDeliverableId(31L);
        task.setResultId(55L);
        when(results.selectById(55L)).thenReturn(result);
        when(results.selectByIdForUpdate(7L, 55L)).thenReturn(result);
        when(tasks.selectByIdForUpdate(7L, 60L)).thenReturn(task);
        assertTrue(service.revalidate(view, projection(SATISFACTION_PROJECTION, List.of(9001L))).valid());
        result.setPassed(false);
        assertFalse(service.revalidate(view, projection(SATISFACTION_PROJECTION, List.of(9001L))).valid());
        result.setPassed(true);
        result.setResultStatus("INVALIDATED");
        assertFalse(service.revalidate(view, projection(SATISFACTION_PROJECTION, List.of(9001L))).valid());
    }

    @Test
    void unsupportedProjectionKindIsRejected() {
        assertFalse(service.revalidate(view, projection("{\"projectionKind\":\"OTHER\"}", List.of())).valid());
        assertEquals("DELIVERABLE_SOURCE_UNSUPPORTED",
                service.revalidate(view, projection("{\"projectionKind\":\"OTHER\"}", List.of())).reason());
    }

    @Test
    void ownerTypeFollowsWhichBusinessTableBindsTheRequirement() {
        when(activities.selectByProjectScope(new AcceptanceActivityScopeQuery(7L, Set.of(9L))))
                .thenReturn(List.of(activity));
        assertEquals("ACCEPTANCE_REPORT", service.ownerType(view));
        when(activities.selectByProjectScope(new AcceptanceActivityScopeQuery(7L, Set.of(9L))))
                .thenReturn(List.of());
        var task = new SatisfactionCollectionTaskDO();
        task.setId(60L);
        task.setDeliverableId(31L);
        when(tasks.selectByScope(new SatisfactionTaskScopeQuery(7L, Set.of(9L), null))).thenReturn(List.of(task));
        assertEquals("SATISFACTION_RESULT", service.ownerType(view));
        when(tasks.selectByScope(new SatisfactionTaskScopeQuery(7L, Set.of(9L), null))).thenReturn(List.of());
        assertNull(service.ownerType(view));
    }
}
