package cn.iocoder.yudao.module.pms.acceptance.api.deliverable;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectDeliverableArchiveFactApiImplTest {
    private final PlatformDeliveryRequirementApi platform = mock(PlatformDeliveryRequirementApi.class);
    private final ProjectDeliverableArchiveFactApiImpl service = new ProjectDeliverableArchiveFactApiImpl(
            platform, mock(AcceptanceReportVersionMapper.class), mock(AcceptanceActivityMapper.class));
    private final PlatformDeliveryRequirementApi.TemplateFrozenView definition =
            mock(PlatformDeliveryRequirementApi.TemplateFrozenView.class);
    private final PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView submission =
            mock(PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView.class);

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(7L);
        when(definition.id()).thenReturn(45L);
        when(platform.listByProject(20L)).thenReturn(List.of(definition));
        when(platform.findCurrentSubmission(45L)).thenReturn(Optional.of(submission));
        when(submission.id()).thenReturn(90L);
        when(submission.requestKey()).thenReturn("manual-upload");
        when(submission.status()).thenReturn("ACCEPTED");
    }

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @ParameterizedTest
    @CsvSource({"D_ARRIVAL,RECEIPT", "D_INITIAL,PRELIMINARY", "D_FINAL,FINAL"})
    void submittedTemplateFilesRetainDeclaredCategory(String code, String category) {
        when(definition.deliverableCode()).thenReturn(code);
        var fact = service.listByProject(7L, 20L).getFirst();
        assertEquals(category, fact.sourceCategory());
        assertEquals("ProjectDeliverableSubmission", fact.sourceObjectType());
        assertEquals(90L, fact.sourceObjectId());
        assertNull(fact.archiveStatus());
    }

    @Test void unsubmittedDefinitionDoesNotBecomeAReceiptFact() {
        when(definition.deliverableCode()).thenReturn("D_ARRIVAL");
        when(platform.findCurrentSubmission(45L)).thenReturn(Optional.empty());
        var fact = service.listByProject(7L, 20L).getFirst();
        assertNull(fact.sourceCategory());
        assertNull(fact.sourceObjectId());
    }

    @Test void documentTitleDoesNotDefineItsCategory() {
        when(definition.deliverableCode()).thenReturn("CUSTOM_DOCUMENT");
        when(definition.name()).thenReturn("初验报告");
        assertNull(service.listByProject(7L, 20L).getFirst().sourceCategory());
    }

    @Test void tenantMismatchRemainsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.listByProject(8L, 20L));
        verifyNoInteractions(platform);
    }

    @ParameterizedTest
    @CsvSource({
            "NOT_REQUIRED,NOT_REQUIRED,NOT_REQUIRED",
            "NOT_REQUIRED,ARCHIVED,ARCHIVED",
            "NOT_REQUIRED,PENDING_COMPENSATION,PENDING_COMPENSATION",
            "NOT_REQUIRED,INVALID,INVALID",
            "ARCHIVED,PENDING_COMPENSATION,PENDING_COMPENSATION",
            "PENDING_COMPENSATION,INVALID,INVALID"
    })
    void archiveObligationsAreNotInventedOrHidden(String firstStatus, String secondStatus, String expected) {
        var first = mock(PlatformDeliveryRequirementApi.TemplateFrozenMaterialView.class);
        var second = mock(PlatformDeliveryRequirementApi.TemplateFrozenMaterialView.class);
        var unrelated = mock(PlatformDeliveryRequirementApi.TemplateFrozenMaterialView.class);
        when(first.id()).thenReturn(1L);
        when(second.id()).thenReturn(2L);
        when(unrelated.id()).thenReturn(3L);
        when(first.archiveStatus()).thenReturn(firstStatus);
        when(second.archiveStatus()).thenReturn(secondStatus);
        when(submission.materialIds()).thenReturn(List.of(1L, 2L));
        when(platform.listMaterials(45L)).thenReturn(List.of(first, second, unrelated));
        assertEquals(expected, service.listByProject(7L, 20L).getFirst().archiveStatus());
    }
}
