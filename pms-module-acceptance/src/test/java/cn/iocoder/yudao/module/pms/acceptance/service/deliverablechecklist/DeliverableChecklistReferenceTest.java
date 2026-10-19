package cn.iocoder.yudao.module.pms.acceptance.service.deliverablechecklist;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.deliverablechecklist.vo.DeliverableChecklistSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AcceptanceDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AcceptanceMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.AcceptanceRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptance.NativeAcceptanceProjectAccess;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliverableChecklistReferenceTest {
    @Mock AcceptanceMapper acceptanceMapper;
    @Mock DeliverableChecklistMapper deliverableChecklistMapper;
    @Mock AcceptanceRecordCodeGenerator recordCodeGenerator;
    @Mock NativeAcceptanceProjectAccess projectAccess;
    @InjectMocks DeliverableChecklistServiceImpl service;

    @BeforeEach void setup() { TenantContextHolder.setTenantId(7L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void crossProjectAcceptanceCannotAffectAnotherProjectsGate() {
        var target = new AcceptanceDO(); target.setId(8L); target.setTenantId(7L); target.setProjectId(10L);
        when(acceptanceMapper.selectOwnerForUpdate(any())).thenReturn(target);
        assertThrows(ServiceException.class, () -> service.createDeliverableChecklist(request()));
        verify(deliverableChecklistMapper, never()).insert(any(DeliverableChecklistDO.class));
    }

    @Test void missingAcceptanceCannotBecomeAnOrphanReference() {
        assertThrows(ServiceException.class, () -> service.createDeliverableChecklist(request()));
        verify(deliverableChecklistMapper, never()).insert(any(DeliverableChecklistDO.class));
    }

    @Test void sameProjectAcceptanceCanBeLinked() {
        var target = new AcceptanceDO(); target.setId(8L); target.setTenantId(7L); target.setProjectId(9L);
        when(acceptanceMapper.selectOwnerForUpdate(any())).thenReturn(target);
        service.createDeliverableChecklist(request());
        verify(deliverableChecklistMapper).insert(argThat((DeliverableChecklistDO row) -> row.getAcceptanceId().equals(8L)));
    }

    @Test void crossTenantReferenceIsRejectedEvenIfAProviderReturnsIt() {
        var target = new AcceptanceDO(); target.setId(8L); target.setTenantId(8L); target.setProjectId(9L);
        when(acceptanceMapper.selectOwnerForUpdate(any())).thenReturn(target);
        assertThrows(ServiceException.class, () -> service.createDeliverableChecklist(request()));
        verify(deliverableChecklistMapper, never()).insert(any(DeliverableChecklistDO.class));
    }

    private static DeliverableChecklistSaveReqVO request() {
        var request = new DeliverableChecklistSaveReqVO(); request.setProjectId(9L);
        request.setAcceptanceId(8L); request.setName("关联完整性回归"); return request;
    }
}
