package cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate;

import cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate.vo.CompletionCertificateDeviceSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate.vo.CompletionCertificateSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDeviceDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateDeviceMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.AcceptanceRecordCodeGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 完工证明设备明细级联：创建/更新整存整取、删除随证明清理、非草稿禁改。 */
@ExtendWith(MockitoExtension.class)
class CompletionCertificateServiceTest {
    @org.mockito.Mock private cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.CompletionSatisfactionBundleMapper jointBundles;

    @Mock
    private CompletionCertificateMapper completionCertificateMapper;
    @Mock
    private CompletionCertificateDeviceMapper completionCertificateDeviceMapper;
    @Mock
    private AcceptanceRecordCodeGenerator recordCodeGenerator;

    @Mock
    private cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi deliveryMaterials;

    @Mock cn.iocoder.yudao.module.pms.acceptance.service.acceptance.NativeAcceptanceDeliveryAccess nativeAccess;
 @Mock cn.iocoder.yudao.module.pms.acceptance.service.acceptance.NativeAcceptanceProjectAccess projectAccess;
 @Mock cn.iocoder.yudao.module.pms.engineering.api.arrival.SignedArrivalDateApi arrivalDates;
 @InjectMocks
    private CompletionCertificateServiceImpl service;

    @org.junit.jupiter.api.BeforeEach void tenant(){cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(7L);}
    @org.junit.jupiter.api.AfterEach void clear(){cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();}
    private static CompletionCertificateDeviceSaveReqVO device(String type, String model, int quantity) {
        CompletionCertificateDeviceSaveReqVO reqVO = new CompletionCertificateDeviceSaveReqVO();
        reqVO.setDeviceType(type);
        reqVO.setDeviceModel(model);
        reqVO.setQuantity(quantity);
        return reqVO;
    }

    private static CompletionCertificateSaveReqVO saveReqVO(Long id, int status,
            CompletionCertificateDeviceSaveReqVO... devices) {
        CompletionCertificateSaveReqVO reqVO = new CompletionCertificateSaveReqVO();
        reqVO.setId(id);
        reqVO.setVersion(0);
        reqVO.setProjectId(9L);
        reqVO.setName("测试项目");
        reqVO.setStatus(status);
        reqVO.setDevices(devices.length == 0 ? List.of() : List.of(devices));
        return reqVO;
    }

    @Test void nonDraftCompletionCannotBeDeletedThroughTheLegacyEntry() {
        var existing=new CompletionCertificateDO();existing.setId(6L);existing.setProjectId(9L);
        when(completionCertificateMapper.selectDeliveryOwnerForUpdate(any())).thenReturn(existing);
        for(int status: new int[]{1,2,3,4}) {
            existing.setStatus(status);
            assertThrows(RuntimeException.class,()->service.deleteCompletionCertificate(6L));
        }
        verify(completionCertificateMapper,never()).deleteById(6L);
        verify(completionCertificateDeviceMapper,never()).deleteByCertificateId(6L);
    }
    @Test void createRejectsInternalCustomerSignature() {
        var request=saveReqVO(null,0);request.setCustomerSignUrl("/internal-signature.png");
        assertThrows(IllegalArgumentException.class,()->service.createCompletionCertificate(request));
        verify(completionCertificateMapper,never()).insert(any(CompletionCertificateDO.class));
    }
    @Test void updateRejectsInternalCustomerSigningDate() {
        var existing=new CompletionCertificateDO();existing.setId(6L);existing.setTenantId(7L);existing.setProjectId(9L);existing.setStatus(0);
        when(completionCertificateMapper.selectDeliveryOwnerForUpdate(any())).thenReturn(existing);
        var request=saveReqVO(6L,0);request.setCustomerSignDate(java.time.LocalDate.now());
        assertThrows(IllegalArgumentException.class,()->service.updateCompletionCertificate(request));
        verify(completionCertificateMapper,never()).updateById(any(CompletionCertificateDO.class));
    }

    @Test
    void rejectingPairedPendingCertificateRevokesCustomerLinksAfterTheStateChange() {
        var certificate = new CompletionCertificateDO(); certificate.setId(8L); certificate.setVersion(2L); certificate.setProjectId(9L); certificate.setStatus(1);
        when(completionCertificateMapper.selectDeliveryOwnerForUpdate(any())).thenReturn(certificate);
        when(completionCertificateMapper.updateById(any(CompletionCertificateDO.class))).thenReturn(1);
        var bundle = new cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.CompletionSatisfactionBundleDO(); bundle.setId(40L);
        when(jointBundles.selectByCertificate(any())).thenReturn(bundle);
        try (var security = org.mockito.Mockito.mockStatic(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.class)) {
            security.when(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils::getLoginUserId).thenReturn(1L);
            service.rejectCompletionCertificate(8L);
        }
        var order = org.mockito.Mockito.inOrder(completionCertificateMapper, jointBundles);
        order.verify(completionCertificateMapper).selectDeliveryOwnerForUpdate(any());
        order.verify(completionCertificateMapper).updateById(any(CompletionCertificateDO.class));
        order.verify(jointBundles).selectByCertificate(any());
        order.verify(jointBundles).revokeActiveGrants(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.CompletionGrantRevocation(7L, 40L, "1"));
    }

    @Test
    void rejectedStateConflictDoesNotRevokeAnOtherwiseValidCustomerLink() {
        var certificate = new CompletionCertificateDO(); certificate.setId(8L); certificate.setVersion(2L); certificate.setProjectId(9L); certificate.setStatus(1);
        when(completionCertificateMapper.selectDeliveryOwnerForUpdate(any())).thenReturn(certificate);
        when(completionCertificateMapper.updateById(any(CompletionCertificateDO.class))).thenReturn(0);
        assertThrows(Exception.class, () -> service.rejectCompletionCertificate(8L));
        verify(jointBundles, never()).revokeActiveGrants(any());
    }

    @Test
    void create_shouldGenerateCodeAndInsertDevicesWithSort() {
        CompletionCertificateSaveReqVO reqVO = (CompletionCertificateSaveReqVO) saveReqVO(null, 0,
                device("防护设备", "LPH-9000", 2), device("检测设备", "IDS-2000", 1));
        when(recordCodeGenerator.next(eq(9L), any(), eq(completionCertificateMapper))).thenReturn("PJT1-WZ-001");

        service.createCompletionCertificate(reqVO);

        ArgumentCaptor<CompletionCertificateDO> certCaptor = ArgumentCaptor.forClass(CompletionCertificateDO.class);
        verify(completionCertificateMapper).insert(certCaptor.capture());
        assertEquals("PJT1-WZ-001", certCaptor.getValue().getCode());

        ArgumentCaptor<List<CompletionCertificateDeviceDO>> deviceCaptor = ArgumentCaptor.captor();
        verify(completionCertificateDeviceMapper).insertBatch(deviceCaptor.capture());
        List<CompletionCertificateDeviceDO> inserted = deviceCaptor.getValue();
        assertEquals(2, inserted.size());
        assertEquals(certCaptor.getValue().getId(), inserted.get(0).getCertificateId());
        assertEquals(0, inserted.get(0).getSort());
        assertEquals("LPH-9000", inserted.get(0).getDeviceModel());
        assertEquals(1, inserted.get(1).getSort());
    }

    @Test
    void update_draft_shouldReplaceDevices() {
        CompletionCertificateSaveReqVO reqVO = (CompletionCertificateSaveReqVO) saveReqVO(5L, 0,
                device("防护设备", "LPH-9000", 3));
        CompletionCertificateDO existing = new CompletionCertificateDO();
        existing.setId(5L);
        existing.setVersion(0L);
        existing.setStatus(0);
        when(completionCertificateMapper.updateById(any(CompletionCertificateDO.class))).thenReturn(1);
        when(completionCertificateMapper.selectDeliveryOwnerForUpdate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery(7L,5L))).thenReturn(existing);

        when(completionCertificateMapper.updateManualArrivalDate(any())).thenReturn(1);
        service.updateCompletionCertificate(reqVO);

        verify(completionCertificateDeviceMapper).deleteByCertificateId(5L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CompletionCertificateDeviceDO>> deviceCaptor = ArgumentCaptor.forClass(List.class);
        verify(completionCertificateDeviceMapper).insertBatch(deviceCaptor.capture());
        assertEquals(1, deviceCaptor.getValue().size());
        assertEquals(5L, deviceCaptor.getValue().get(0).getCertificateId());
    }

    @Test
    void update_nonDraft_shouldRejectWithoutDeviceChange() {
        CompletionCertificateSaveReqVO reqVO = (CompletionCertificateSaveReqVO) saveReqVO(5L, 2);
        CompletionCertificateDO existing = new CompletionCertificateDO();
        existing.setId(5L);
        existing.setStatus(2);
        when(completionCertificateMapper.selectDeliveryOwnerForUpdate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery(7L,5L))).thenReturn(existing);

        assertThrows(Exception.class, () -> service.updateCompletionCertificate(reqVO));
        verify(completionCertificateDeviceMapper, never()).deleteByCertificateId(any());
        verify(completionCertificateDeviceMapper, never()).insertBatch(anyList());
    }

    @Test
    void delete_shouldCascadeDevices() {
        CompletionCertificateDO existing = new CompletionCertificateDO();
        existing.setId(6L);
        existing.setStatus(0);
        when(completionCertificateMapper.selectDeliveryOwnerForUpdate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery(7L,6L))).thenReturn(existing);

        service.deleteCompletionCertificate(6L);

        verify(completionCertificateDeviceMapper).deleteByCertificateId(6L);
        verify(completionCertificateMapper).deleteById(6L);
    }

    @Test
    void getDevices_shouldDelegateToMapper() {
        CompletionCertificateDO certificate = new CompletionCertificateDO();
        certificate.setId(7L);
        certificate.setProjectId(9L);
        when(completionCertificateMapper.selectById(7L)).thenReturn(certificate);
        List<CompletionCertificateDeviceDO> devices = List.of(new CompletionCertificateDeviceDO());
        when(completionCertificateDeviceMapper.selectListByCertificateId(7L)).thenReturn(devices);

        assertSame(devices, service.getCompletionCertificateDevices(7L));
    }

    @Test
    void customerConfirm_pendingCustomer_shouldTransitionWithConfirmTime() {
        CompletionCertificateDO existing = new CompletionCertificateDO();
        existing.setId(8L);
        existing.setVersion(0L);
        existing.setProjectId(9L);
        existing.setName("Real certificate");
        when(completionCertificateMapper.updateById(any(CompletionCertificateDO.class))).thenReturn(1);
        existing.setStatus(1);
        when(completionCertificateMapper.selectDeliveryOwnerForUpdate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery(7L,8L))).thenReturn(existing);

        service.customerConfirm(8L);

        ArgumentCaptor<CompletionCertificateDO> captor = ArgumentCaptor.forClass(CompletionCertificateDO.class);
        verify(completionCertificateMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getStatus());
        assertEquals(8L, captor.getValue().getId());
        assertNotNull(captor.getValue().getCustomerConfirmTime());
        // 客户确认是客户单位外部人员（非系统用户），不落系统 user_id
        assertNull(captor.getValue().getCustomerConfirmUserId());
    }


    @Test void configuredSatisfactionCannotBypassScoringThroughLegacyConfirmation() {
        var row = new CompletionCertificateDO(); row.setId(8L); row.setStatus(1); row.setVersion(2L);
        row.setSatisfactionTemplateId(90L);
        when(completionCertificateMapper.selectDeliveryOwnerForUpdate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery(7L, 8L))).thenReturn(row);
        assertThrows(IllegalStateException.class, () -> service.customerConfirm(8L));
        verify(completionCertificateMapper, never()).updateById(any(CompletionCertificateDO.class));
        org.mockito.Mockito.verifyNoInteractions(deliveryMaterials);
    }

    @Test void staleDraftUpdateDoesNotReplaceDevices() {
        var row=new CompletionCertificateDO();row.setId(5L);row.setStatus(0);row.setVersion(2L);
        when(completionCertificateMapper.selectDeliveryOwnerForUpdate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery(7L,5L))).thenReturn(row);
        assertThrows(Exception.class,()->service.updateCompletionCertificate(saveReqVO(5L,0,device("router","x",1))));
        verify(completionCertificateDeviceMapper,never()).deleteByCertificateId(any());
        verify(completionCertificateDeviceMapper,never()).insertBatch(anyList());
    }
    @Test void staleConfirmationDoesNotPublishBusinessResult() {
        var row=new CompletionCertificateDO();row.setId(8L);row.setStatus(1);row.setVersion(2L);
        when(completionCertificateMapper.selectDeliveryOwnerForUpdate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery(7L,8L))).thenReturn(row);
        assertThrows(Exception.class,()->service.customerConfirm(8L));
        org.mockito.Mockito.verifyNoInteractions(deliveryMaterials);
    }
    @Test void archivePublishesSameUnrevisionedRealCertificateIdentity() {
        var row=new CompletionCertificateDO();row.setId(8L);row.setStatus(2);row.setVersion(2L);row.setProjectId(9L);row.setName("Real certificate");
        when(completionCertificateMapper.selectDeliveryOwnerForUpdate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery(7L,8L))).thenReturn(row);
        when(completionCertificateMapper.updateById(any(CompletionCertificateDO.class))).thenReturn(1);
        service.archiveCompletionCertificate(8L);
        verify(deliveryMaterials).registerBusinessResultMaterial("ACC","completionCertificate",8L,"COMPLETION_CERTIFICATE","completionCertificate","8",null,"Real certificate",9L);
        var update=ArgumentCaptor.forClass(CompletionCertificateDO.class);verify(completionCertificateMapper).updateById(update.capture());
        assertEquals(2L,update.getValue().getVersion());assertEquals(3,update.getValue().getStatus());assertNotNull(update.getValue().getArchiveTime());
    }

    @Test void createCannotForgeCustomerConfirmedStatus() {
        assertThrows(Exception.class,()->service.createCompletionCertificate(saveReqVO(null,2)));
        verify(completionCertificateMapper,never()).insert(any(CompletionCertificateDO.class));
        org.mockito.Mockito.verifyNoInteractions(deliveryMaterials);
    }
    @Test void storesManualDateOnlyWithoutSignedSourceAndOnlyForYes() {
        var request=saveReqVO(null,0);request.setItemArrival("是");request.setArrivalDate(java.time.LocalDate.of(2026,10,11));
        service.createCompletionCertificate(request);
        when(arrivalDates.latest(any())).thenReturn(java.time.LocalDate.of(2026,10,20));
        service.createCompletionCertificate(request);
        request.setItemArrival("否");service.createCompletionCertificate(request);
        var captured=ArgumentCaptor.forClass(CompletionCertificateDO.class);
        org.mockito.Mockito.verify(completionCertificateMapper,org.mockito.Mockito.times(3)).insert(captured.capture());
        assertEquals(java.time.LocalDate.of(2026,10,11),captured.getAllValues().get(0).getManualArrivalDate());
        assertNull(captured.getAllValues().get(1).getManualArrivalDate());
        assertNull(captured.getAllValues().get(2).getManualArrivalDate());
    }
    @Test void explicitlyClearsManualDateOnDraftUpdate() {
        var existing=new CompletionCertificateDO();existing.setId(6L);existing.setProjectId(9L);existing.setTenantId(7L);existing.setStatus(0);
        existing.setManualArrivalDate(java.time.LocalDate.of(2026,10,11));
        when(completionCertificateMapper.selectDeliveryOwnerForUpdate(any())).thenReturn(existing);
        when(completionCertificateMapper.updateById(any(CompletionCertificateDO.class))).thenAnswer(invocation->{
            ((CompletionCertificateDO)invocation.getArgument(0)).setVersion(1L);return 1;
        });
        when(completionCertificateMapper.updateManualArrivalDate(any())).thenReturn(1);
        service.updateCompletionCertificate(saveReqVO(6L,0));
        verify(completionCertificateMapper).updateManualArrivalDate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionManualArrivalUpdate(7L,6L,1L,null));
    }
}
