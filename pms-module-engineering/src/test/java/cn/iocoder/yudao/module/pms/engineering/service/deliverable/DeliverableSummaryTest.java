package cn.iocoder.yudao.module.pms.engineering.service.deliverable;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableArchiveFactApi;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.deliverable.DeliverableMapper;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi.DeliveryMaterialView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliverableSummaryTest {
    private final DeliverableServiceImpl service = new DeliverableServiceImpl();
    private final PlatformDeliveryMaterialApi materials = mock(PlatformDeliveryMaterialApi.class);
    private final DeliveryMaterialView material = mock(DeliveryMaterialView.class);
    private final ProjectDeliverableArchiveFactApi archive = mock(ProjectDeliverableArchiveFactApi.class);

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(7L);
        var legacy = mock(DeliverableMapper.class);
        ReflectionTestUtils.setField(service, "deliveryMaterialApi", materials);
        ReflectionTestUtils.setField(service, "projectDeliverableArchiveFactApi", archive);
        ReflectionTestUtils.setField(service, "deliverableMapper", legacy);
        when(materials.listByProject(20L)).thenReturn(List.of(material));
        when(archive.listByProject(7L, 20L)).thenReturn(List.of());
        when(legacy.selectListByProject(20L)).thenReturn(List.of());
        when(material.status()).thenReturn("ACTIVE");
        when(material.fileName()).thenReturn("附件.pdf");
    }

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @ParameterizedTest
    @CsvSource({
            "IMP,arrival,IMP.ARRIVAL_SIGN_DOCUMENT,RECEIPT",
            "ACC,acceptanceActivity,ACC.PRELIMINARY_REPORT,PRELIMINARY",
            "ACC,acceptanceActivity,ACC.FINAL_REPORT,FINAL"
    })
    void nativeAttachmentsAppearInTheirSourceGroup(String owner, String entity, String type, String category) {
        when(material.ownerModule()).thenReturn(owner);
        when(material.entityType()).thenReturn(entity);
        when(material.typeCode()).thenReturn(type);
        var result = service.getProjectSummary(20L);
        assertEquals(1, result.size());
        assertEquals(category, result.getFirst().getCategory());
        assertEquals("附件.pdf", result.getFirst().getName());
        assertTrue(result.getFirst().getSourceLabel().contains("自动归集"));
    }

    @Test void signedArrivalResultAlsoAppearsWithoutAnAttachment() {
        when(material.ownerModule()).thenReturn("IMP");
        when(material.entityType()).thenReturn("arrival");
        when(material.typeCode()).thenReturn("RECEIPT");
        when(material.businessObjectType()).thenReturn("arrival");
        assertEquals("RECEIPT", service.getProjectSummary(20L).getFirst().getCategory());
    }

    @Test void similarFilenameOrForeignSourceCannotBecomeReceiptOrReport() {
        when(material.ownerModule()).thenReturn("SOL");
        when(material.entityType()).thenReturn("requirementAnalysis");
        when(material.fileName()).thenReturn("到货签收单-初验-终验报告.pdf");
        when(material.typeCode()).thenReturn("ACC.FINAL_REPORT");
        assertEquals("OTHER", service.getProjectSummary(20L).getFirst().getCategory());
    }

    @Test void withdrawnSourceStaysVoidedInItsGroup() {
        when(material.ownerModule()).thenReturn("ACC");
        when(material.entityType()).thenReturn("acceptanceActivity");
        when(material.typeCode()).thenReturn("ACC.FINAL_REPORT");
        when(material.status()).thenReturn("WITHDRAWN");
        var result = service.getProjectSummary(20L).getFirst();
        assertEquals("FINAL", result.getCategory());
        assertEquals(2, result.getStatus());
        assertEquals("材料已撤回", result.getRemark());
    }

    @Test void noMaterialsDoesNotFabricateDeliverables() {
        when(materials.listByProject(20L)).thenReturn(List.of());
        assertTrue(service.getProjectSummary(20L).isEmpty());
    }

    @Test void templateCollectedReportUsesNativeOriginInsteadOfRequirementCode() {
        when(material.ownerModule()).thenReturn("ACC");
        when(material.entityType()).thenReturn("project_deliverable");
        when(material.typeCode()).thenReturn("D_INITIAL");
        when(material.sourceOwnerModule()).thenReturn("ACC");
        when(material.sourceEntityType()).thenReturn("acceptanceActivity");
        when(material.businessTypeCode()).thenReturn("ACC.PRELIMINARY_REPORT");
        assertEquals("PRELIMINARY", service.getProjectSummary(20L).getFirst().getCategory());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RECEIPT", "PRELIMINARY", "FINAL"})
    void templateUploadUsesItsScopedRequirementAndKeepsArchivePending(String category) {
        var fact = mock(ProjectDeliverableArchiveFactApi.ProjectDeliverableArchiveFact.class);
        when(fact.deliverableId()).thenReturn(45L);
        when(fact.sourceCategory()).thenReturn(category);
        when(fact.sourceObjectType()).thenReturn("ProjectDeliverableSubmission");
        when(fact.archiveStatus()).thenReturn("PENDING_COMPENSATION");
        when(archive.listByProject(7L, 20L)).thenReturn(List.of(fact));
        when(material.ownerModule()).thenReturn("ACC");
        when(material.entityType()).thenReturn("project_deliverable");
        when(material.requirementId()).thenReturn(45L);
        var result = service.getProjectSummary(20L);
        assertEquals(category, result.getFirst().getCategory());
        assertEquals("模板交付件自动归集", result.getFirst().getSourceLabel());
        assertEquals(category, result.get(1).getCategory());
        assertEquals("归档补偿处理中", result.get(1).getRemark());
    }

    @Test void unrelatedTemplateRequirementCannotClassifyMaterial() {
        var fact = mock(ProjectDeliverableArchiveFactApi.ProjectDeliverableArchiveFact.class);
        when(fact.deliverableId()).thenReturn(45L);
        when(fact.sourceCategory()).thenReturn("FINAL");
        when(archive.listByProject(7L, 20L)).thenReturn(List.of(fact));
        when(material.ownerModule()).thenReturn("ACC");
        when(material.entityType()).thenReturn("project_deliverable");
        when(material.requirementId()).thenReturn(46L);
        assertEquals("OTHER", service.getProjectSummary(20L).getFirst().getCategory());
    }

    @ParameterizedTest
    @CsvSource({"NOT_REQUIRED,1,", "ARCHIVED,,", "PENDING_COMPENSATION,,归档补偿处理中", "INVALID,,归档失败"})
    void archiveSummaryPreservesActualObligation(String archiveStatus, Integer status, String remark) {
        var fact = mock(ProjectDeliverableArchiveFactApi.ProjectDeliverableArchiveFact.class);
        when(fact.archiveStatus()).thenReturn(archiveStatus);
        when(archive.listByProject(7L, 20L)).thenReturn(List.of(fact));
        when(materials.listByProject(20L)).thenReturn(List.of());
        var result = service.getProjectSummary(20L).getFirst();
        assertEquals(status, result.getStatus());
        assertEquals(remark, result.getRemark());
    }
}
