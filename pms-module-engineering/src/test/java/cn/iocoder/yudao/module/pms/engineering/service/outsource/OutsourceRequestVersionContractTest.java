package cn.iocoder.yudao.module.pms.engineering.service.outsource;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo.OutsourceRequestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.outsource.OutsourceRequestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.outsource.OutsourceRequestMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.OUTSOURCE_VERSION_NOT_MATCH;

/** Preserve the original intended numeric version contract and reject unsuccessful native mutations. */
class OutsourceRequestVersionContractTest {
    @org.junit.jupiter.api.AfterEach void clearNativeTenant() { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }

    OutsourceRequestMapper mapper;
    OutsourceRequestServiceImpl service;
    @BeforeEach void setup() {
        mapper=mock(OutsourceRequestMapper.class);service=new OutsourceRequestServiceImpl();
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(7L);
        ReflectionTestUtils.setField(service,"attachments",mock(cn.iocoder.yudao.module.pms.engineering.service.attachment.NativeAttachmentRegistration.class));
        ReflectionTestUtils.setField(service,"outsourceRequestMapper",mapper);
        var row=new OutsourceRequestDO();row.setId(41L);row.setProjectId(7L);row.setCode("OS-41");row.setStatus(0);row.setVersion(5L);
        when(mapper.selectAttachmentOwnerForUpdate(org.mockito.ArgumentMatchers.any())).thenReturn(row);
        when(mapper.updateById(any(OutsourceRequestDO.class))).thenReturn(1);
        when(mapper.deleteById(41L)).thenReturn(1);
    }
    OutsourceRequestSaveReqVO update(Integer version) {
        var input=new OutsourceRequestSaveReqVO();input.setId(41L);input.setProjectId(7L);input.setCode("OS-41");
        input.setName("original native draft");input.setWorkContent("native work");input.setVersion(version);return input;
    }
    void versionFailure(Runnable command) {
        var error=assertThrows(ServiceException.class,command::run);
        assertEquals(OUTSOURCE_VERSION_NOT_MATCH.getCode(),error.getCode());
    }
    @Test void numericEqualIntegerInputAndLongEntityVersionAreTheSameVersion() {
        assertDoesNotThrow(()->service.updateOutsourceRequest(update(5)));
        verify(mapper).updateById(any(OutsourceRequestDO.class));
    }
    @Test void staleVersionIsRejectedBeforeWriting() {
        versionFailure(()->service.updateOutsourceRequest(update(4)));
        verify(mapper,never()).updateById(any(OutsourceRequestDO.class));
    }
    @Test void failedDraftUpdateCannotReturnSuccess() {
        when(mapper.updateById(any(OutsourceRequestDO.class))).thenReturn(0);
        versionFailure(()->service.updateOutsourceRequest(update(null)));
    }
    @Test void failedStateTransitionCannotReturnSuccess() {
        when(mapper.updateById(any(OutsourceRequestDO.class))).thenReturn(0);
        versionFailure(()->service.submitOutsourceRequest(41L));
    }
    @Test void failedDeletionCannotReturnSuccess() {
        when(mapper.deleteById(41L)).thenReturn(0);
        versionFailure(()->service.deleteOutsourceRequest(41L,null));
    }
    @Test void failedInsertCannotProduceAnObjectIdentity() {
        when(mapper.insert(any(OutsourceRequestDO.class))).thenReturn(0);
        assertThrows(IllegalStateException.class,()->service.createOutsourceRequest(update(null)));
    }
}
