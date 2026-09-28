package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryBusinessObjectEvidenceProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统一交付材料登记语义验证（P06R）：业务成果材料经 Owner 证据提供方校验后落库、
 * 同一（来源实体 + 业务对象 + 修订锚）幂等、无提供方拒绝登记、FILE 行证据失效重验拒绝。
 */
@ExtendWith(MockitoExtension.class)
class DeliveryMaterialServiceTest {

    @Mock DeliveryMaterialMapper materialMapper;
    @Mock DeliveryCatalogService catalogService;
    @Mock FileEvidenceApi fileEvidenceApi;
    @Mock DeliveryEventPublisher eventPublisher;
    @Mock DeliveryBusinessObjectEvidenceProvider solutionProvider;

    private DeliveryMaterialService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        org.mockito.Mockito.lenient().when(solutionProvider.supports("solution")).thenReturn(true);
        service = new DeliveryMaterialService(materialMapper, catalogService, fileEvidenceApi,
                eventPublisher, List.of(solutionProvider));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void businessResultRegistersAfterProviderValidation() {
        when(materialMapper.selectByBusinessObject("SOL", "solution", 42L, "solution", "42", 9L))
                .thenReturn(null);
        when(materialMapper.insert(any(DeliveryMaterialDO.class))).thenReturn(1);

        DeliveryMaterialDO registered = service.registerBusinessResult("SOL", "solution", 42L,
                "IMPLEMENTATION_PLAN", "solution", "42", 9L, "重大方案（基线v9）", 9L);

        ArgumentCaptor<DeliveryMaterialDO> captor = ArgumentCaptor.forClass(DeliveryMaterialDO.class);
        verify(materialMapper).insert(captor.capture());
        DeliveryMaterialDO row = captor.getValue();
        assertEquals(DeliveryMaterialDO.KIND_BUSINESS_RESULT, row.getMaterialKind());
        assertEquals("solution", row.getBusinessObjectType());
        assertEquals("42", row.getBusinessObjectId());
        assertEquals(9L, row.getBusinessRevisionNo());
        assertEquals(DeliveryMaterialDO.SOURCE_ASSOCIATED, row.getSourceKind());
        assertEquals(9L, row.getProjectId());
        // 文件证据列为空：业务成果材料不锚定文件
        assertNull(row.getFileReferenceId());
        verify(solutionProvider).validateCurrent(1L, 9L, "42", 9L);
    }

    @Test
    void businessResultIsIdempotentPerBusinessObjectRevision() {
        DeliveryMaterialDO existing = new DeliveryMaterialDO();
        existing.setId(77L);
        when(materialMapper.selectByBusinessObject("SOL", "solution", 42L, "solution", "42", 9L))
                .thenReturn(existing);

        DeliveryMaterialDO registered = service.registerBusinessResult("SOL", "solution", 42L,
                "IMPLEMENTATION_PLAN", "solution", "42", 9L, "重大方案（基线v9）", 9L);

        assertEquals(77L, registered.getId());
        verify(materialMapper, never()).insert(any(DeliveryMaterialDO.class));
    }

    @Test
    void businessObjectWithoutIdentityIsRejected() {
        assertThrows(BusinessContractException.class, () -> service.registerBusinessResult(
                "SOL", "solution", 42L, "IMPLEMENTATION_PLAN", null, "42", null, "t", 9L));
        verify(materialMapper, never()).insert(any(DeliveryMaterialDO.class));
    }

    @Test
    void missingEvidenceProviderRejectsRegistration() {
        assertThrows(BusinessContractException.class, () -> service.registerBusinessResult(
                "IMP", "satisfaction", 8L, "SATISFACTION_REPORT", "satisfaction", "8", null, "满意度报告", 9L));

        verify(materialMapper, never()).insert(any(DeliveryMaterialDO.class));
    }

    @Test
    void revalidationUsesProviderForBusinessResultRows() {
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setMaterialKind(DeliveryMaterialDO.KIND_BUSINESS_RESULT);
        row.setBusinessObjectType("solution");
        row.setBusinessObjectId("42");
        row.setBusinessRevisionNo(9L);

        service.revalidateActive(row);

        verify(solutionProvider).validateCurrent(eq(1L), eq((Long) null), eq("42"), eq(9L));
    }

    @Test
    void revalidationRejectsUnavailableFileEvidence() {
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setId(5L);
        row.setMaterialKind(DeliveryMaterialDO.KIND_FILE);
        row.setFileReferenceId(501L);
        row.setFileSha256("sha-1");
        when(fileEvidenceApi.inspectDocument(eq(1L), eq(501L))).thenReturn(null);

        assertThrows(BusinessContractException.class, () -> service.revalidateActive(row));
    }

    @Test
    void duplicateKeyConflictSurfacesWhenIdempotentRowMissing() {
        when(materialMapper.selectByBusinessObject("SOL", "solution", 42L, "solution", "42", 9L))
                .thenReturn(null);
        DuplicateKeyException conflict = new DuplicateKeyException("dup");
        when(materialMapper.insert(any(DeliveryMaterialDO.class))).thenThrow(conflict);

        assertThrows(DuplicateKeyException.class, () -> service.registerBusinessResult(
                "SOL", "solution", 42L, "IMPLEMENTATION_PLAN", "solution", "42", 9L, "t", 9L));
    }

    @Test
    void withdrawMarksRowAndPublishesEvent() {
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setId(5L);
        row.setStatus(DeliveryMaterialDO.STATUS_ACTIVE);
        row.setOwnerModule("SOL");
        row.setEntityType("solution");
        row.setEntityId(42L);
        row.setTypeCode("IMPLEMENTATION_PLAN");
        when(materialMapper.selectById(5L)).thenReturn(row);

        DeliveryMaterialDO withdrawn = service.withdraw(5L);

        assertEquals(DeliveryMaterialDO.STATUS_WITHDRAWN, withdrawn.getStatus());
        verify(eventPublisher).publishMaterial("SOL", "solution", 42L, "IMPLEMENTATION_PLAN",
                "MATERIAL_WITHDRAWN");
    }
}
