package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.acceptance.vo.AcceptancePageReqVO;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.acceptance.vo.AcceptanceSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.archivedocument.vo.ArchiveDocumentPageReqVO;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.archivedocument.vo.ArchiveDocumentSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate.vo.CompletionCertificatePageReqVO;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate.vo.CompletionCertificateSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.deliverablechecklist.vo.DeliverableChecklistPageReqVO;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.deliverablechecklist.vo.DeliverableChecklistSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AcceptanceDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.archivedocument.ArchiveDocumentDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AcceptanceMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query.AcceptanceOwnerLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query.AcceptancePageQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.ArchiveDocumentMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.query.ArchiveDocumentDeliveryLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.query.ArchiveDocumentPageQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateDeviceMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificatePageQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.query.DeliverableChecklistOwnerLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.query.DeliverableChecklistPageQuery;
import cn.iocoder.yudao.module.pms.acceptance.service.AcceptanceRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.acceptance.service.archivedocument.ArchiveDocumentServiceImpl;
import cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate.CompletionCertificateServiceImpl;
import cn.iocoder.yudao.module.pms.acceptance.service.deliverablechecklist.DeliverableChecklistDeliveryAccess;
import cn.iocoder.yudao.module.pms.acceptance.service.deliverablechecklist.DeliverableChecklistServiceImpl;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Scope boundaries at the four native CRUD service entry points. */
class NativeAcceptanceProjectAccessServiceScopeTest {
    private final AcceptanceMapper acceptanceMapper = mock(AcceptanceMapper.class);
    private final ArchiveDocumentMapper archiveMapper = mock(ArchiveDocumentMapper.class);
    private final DeliverableChecklistMapper checklistMapper = mock(DeliverableChecklistMapper.class);
    private final CompletionCertificateMapper certificateMapper = mock(CompletionCertificateMapper.class);
    private final CompletionCertificateDeviceMapper certificateDeviceMapper = mock(CompletionCertificateDeviceMapper.class);
    private final AcceptanceRecordCodeGenerator codes = mock(AcceptanceRecordCodeGenerator.class);
    private final PlatformDeliveryMaterialApi materials = mock(PlatformDeliveryMaterialApi.class);
    private final NativeAcceptanceProjectAccess projectAccess = mock(NativeAcceptanceProjectAccess.class);
    private final DeliverableChecklistDeliveryAccess checklistDeliveryAccess = mock(DeliverableChecklistDeliveryAccess.class);

    private final AcceptanceServiceImpl acceptance = new AcceptanceServiceImpl();
    private final ArchiveDocumentServiceImpl archive = new ArchiveDocumentServiceImpl();
    private final DeliverableChecklistServiceImpl checklist = new DeliverableChecklistServiceImpl();
    private final CompletionCertificateServiceImpl certificate = new CompletionCertificateServiceImpl();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        ReflectionTestUtils.setField(acceptance, "acceptanceMapper", acceptanceMapper);
        ReflectionTestUtils.setField(acceptance, "recordCodeGenerator", codes);
        ReflectionTestUtils.setField(acceptance, "projectAccess", projectAccess);
        ReflectionTestUtils.setField(archive, "archiveDocumentMapper", archiveMapper);
        ReflectionTestUtils.setField(archive, "recordCodeGenerator", codes);
        ReflectionTestUtils.setField(archive, "deliveryMaterials", materials);
        ReflectionTestUtils.setField(archive, "projectAccess", projectAccess);
        ReflectionTestUtils.setField(checklist, "deliverableChecklistMapper", checklistMapper);
        ReflectionTestUtils.setField(checklist, "recordCodeGenerator", codes);
        ReflectionTestUtils.setField(checklist, "deliveryAccess", checklistDeliveryAccess);
        ReflectionTestUtils.setField(checklist, "materials", materials);
        ReflectionTestUtils.setField(checklist, "projectAccess", projectAccess);
        ReflectionTestUtils.setField(certificate, "completionCertificateMapper", certificateMapper);
        ReflectionTestUtils.setField(certificate, "completionCertificateDeviceMapper", certificateDeviceMapper);
        ReflectionTestUtils.setField(certificate, "recordCodeGenerator", codes);
        ReflectionTestUtils.setField(certificate, "deliveryMaterials", materials);
        ReflectionTestUtils.setField(certificate, "projectAccess", projectAccess);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void allFourPagesPassOnlyTheResolvedProjectIdsToTheirMappers() {
        var visible = Set.of(20L, 21L);
        when(projectAccess.readableProjects()).thenReturn(visible);

        acceptance.getAcceptancePage(new AcceptancePageReqVO());
        archive.getArchiveDocumentPage(new ArchiveDocumentPageReqVO());
        checklist.getDeliverableChecklistPage(new DeliverableChecklistPageReqVO());
        certificate.getCompletionCertificatePage(new CompletionCertificatePageReqVO());

        var acceptanceQuery = org.mockito.ArgumentCaptor.forClass(AcceptancePageQuery.class);
        var archiveQuery = org.mockito.ArgumentCaptor.forClass(ArchiveDocumentPageQuery.class);
        var checklistQuery = org.mockito.ArgumentCaptor.forClass(DeliverableChecklistPageQuery.class);
        var certificateQuery = org.mockito.ArgumentCaptor.forClass(CompletionCertificatePageQuery.class);
        verify(acceptanceMapper).selectPage(acceptanceQuery.capture());
        verify(archiveMapper).selectPage(archiveQuery.capture());
        verify(checklistMapper).selectPage(checklistQuery.capture());
        verify(certificateMapper).selectPage(certificateQuery.capture());
        assertEquals(visible, acceptanceQuery.getValue().getVisibleProjectIds());
        assertEquals(visible, archiveQuery.getValue().getVisibleProjectIds());
        assertEquals(visible, checklistQuery.getValue().getVisibleProjectIds());
        assertEquals(visible, certificateQuery.getValue().getVisibleProjectIds());
        assertEquals(7L, acceptanceQuery.getValue().getTenantId());
        assertEquals(7L, archiveQuery.getValue().getTenantId());
        assertEquals(7L, checklistQuery.getValue().getTenantId());
        assertEquals(7L, certificateQuery.getValue().getTenantId());
    }

    @Test
    void createAndWriteScopeDenialsNeverReachNativeMappers() {
        doThrow(denied()).when(projectAccess).requireWrite(20L);

        assertThrows(BusinessContractException.class, () -> acceptance.createAcceptance(acceptanceRequest()));
        assertThrows(BusinessContractException.class, () -> archive.createArchiveDocument(archiveRequest()));
        assertThrows(BusinessContractException.class, () -> checklist.createDeliverableChecklist(checklistRequest()));
        assertThrows(BusinessContractException.class, () -> certificate.createCompletionCertificate(certificateRequest()));

        when(acceptanceMapper.selectOwnerForUpdate(new AcceptanceOwnerLockQuery(7L, 1L))).thenReturn(acceptanceRow());
        when(archiveMapper.selectDeliveryOwnerForUpdate(new ArchiveDocumentDeliveryLockQuery(7L, 1L))).thenReturn(archiveRow());
        when(checklistMapper.selectOwnerForUpdate(new DeliverableChecklistOwnerLockQuery(7L, 1L))).thenReturn(checklistRow());
        when(certificateMapper.selectDeliveryOwnerForUpdate(new CompletionCertificateDeliveryLockQuery(7L, 1L))).thenReturn(certificateRow());

        var acceptanceUpdate = acceptanceRequest(); acceptanceUpdate.setId(1L); acceptanceUpdate.setVersion(1);
        var archiveUpdate = archiveRequest(); archiveUpdate.setId(1L); archiveUpdate.setVersion(1);
        var checklistUpdate = checklistRequest(); checklistUpdate.setId(1L); checklistUpdate.setVersion(1);
        var certificateUpdate = certificateRequest(); certificateUpdate.setId(1L); certificateUpdate.setVersion(1);
        assertThrows(BusinessContractException.class, () -> acceptance.updateAcceptance(acceptanceUpdate));
        assertThrows(BusinessContractException.class, () -> archive.updateArchiveDocument(archiveUpdate));
        assertThrows(BusinessContractException.class, () -> checklist.updateDeliverableChecklist(checklistUpdate));
        assertThrows(BusinessContractException.class, () -> certificate.updateCompletionCertificate(certificateUpdate));

        verify(acceptanceMapper, never()).insert(any(AcceptanceDO.class));
        verify(acceptanceMapper, never()).updateById(any(AcceptanceDO.class));
        verify(archiveMapper, never()).insert(any(ArchiveDocumentDO.class));
        verify(archiveMapper, never()).updateById(any(ArchiveDocumentDO.class));
        verify(checklistMapper, never()).insert(any(DeliverableChecklistDO.class));
        verify(checklistMapper, never()).updateById(any(DeliverableChecklistDO.class));
        verify(certificateMapper, never()).insert(any(CompletionCertificateDO.class));
        verify(certificateMapper, never()).updateById(any(CompletionCertificateDO.class));
        verifyNoInteractions(certificateDeviceMapper);
    }

    @Test
    void readsAreDeniedAndCertificateChildrenCannotBypassParentScope() {
        when(acceptanceMapper.selectById(1L)).thenReturn(acceptanceRow());
        when(archiveMapper.selectById(1L)).thenReturn(archiveRow());
        when(checklistMapper.selectById(1L)).thenReturn(checklistRow());
        when(certificateMapper.selectById(1L)).thenReturn(certificateRow());
        doThrow(denied()).when(projectAccess).requireRead(20L);

        assertThrows(BusinessContractException.class, () -> acceptance.getAcceptance(1L));
        assertThrows(BusinessContractException.class, () -> archive.getArchiveDocument(1L));
        assertThrows(BusinessContractException.class, () -> checklist.getDeliverableChecklist(1L));
        assertThrows(BusinessContractException.class, () -> certificate.getCompletionCertificate(1L));
        assertThrows(BusinessContractException.class, () -> certificate.getCompletionCertificateDevices(1L));
        verifyNoInteractions(certificateDeviceMapper);
    }

    private static BusinessContractException denied() {
        return new BusinessContractException("DELIVERY_ACCESS_DENIED", "scope denied");
    }

    private static AcceptanceSaveReqVO acceptanceRequest() {
        var request = new AcceptanceSaveReqVO(); request.setProjectId(20L); request.setName("acceptance"); request.setStatus(0); return request;
    }
    private static ArchiveDocumentSaveReqVO archiveRequest() {
        var request = new ArchiveDocumentSaveReqVO(); request.setProjectId(20L); request.setName("archive"); request.setStatus(0); return request;
    }
    private static DeliverableChecklistSaveReqVO checklistRequest() {
        var request = new DeliverableChecklistSaveReqVO(); request.setProjectId(20L); request.setName("checklist"); request.setStatus(0); return request;
    }
    private static CompletionCertificateSaveReqVO certificateRequest() {
        var request = new CompletionCertificateSaveReqVO(); request.setProjectId(20L); request.setName("certificate"); request.setStatus(0); return request;
    }
    private static AcceptanceDO acceptanceRow() {
        var row = new AcceptanceDO(); row.setId(1L); row.setTenantId(7L); row.setProjectId(20L); row.setStatus(0); row.setVersion(1L); return row;
    }
    private static ArchiveDocumentDO archiveRow() {
        var row = new ArchiveDocumentDO(); row.setId(1L); row.setTenantId(7L); row.setProjectId(20L); row.setName("archive"); row.setStatus(0); row.setVersion(1L); return row;
    }
    private static DeliverableChecklistDO checklistRow() {
        var row = new DeliverableChecklistDO(); row.setId(1L); row.setTenantId(7L); row.setProjectId(20L); row.setName("checklist"); row.setStatus(0); row.setVersion(1L); return row;
    }
    private static CompletionCertificateDO certificateRow() {
        var row = new CompletionCertificateDO(); row.setId(1L); row.setTenantId(7L); row.setProjectId(20L); row.setName("certificate"); row.setStatus(0); row.setVersion(1L); return row;
    }
}
