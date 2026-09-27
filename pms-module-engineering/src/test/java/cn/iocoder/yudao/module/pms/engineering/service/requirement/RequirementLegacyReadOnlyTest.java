package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.vo.RequirementSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_LEGACY_BUSINESS_READ_ONLY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementLegacyReadOnlyTest {
    private final RequirementMapper mapper = mock(RequirementMapper.class);
    private final EngineeringRecordCodeGenerator codes = mock(EngineeringRecordCodeGenerator.class);
    private final RequirementServiceImpl service = new RequirementServiceImpl();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "requirementMapper", mapper);
        ReflectionTestUtils.setField(service, "recordCodeGenerator", codes);
    }

    @Test
    void businessCreationFailsBeforeAnyWrite() {
        RequirementSaveReqVO request = new RequirementSaveReqVO();
        request.setRequirementType("BUSINESS");
        readonly(() -> service.createRequirement(request));
        request.setRequirementType(null);
        readonly(() -> service.createRequirement(request));
        verifyNoInteractions(mapper, codes);
    }

    @Test
    void historicalBusinessRemainsReadableButEveryMutationFails() {
        RequirementDO record = record("BUSINESS");
        when(mapper.selectById(1L)).thenReturn(record);
        assertSame(record, service.getRequirement(1L));
        RequirementSaveReqVO request = new RequirementSaveReqVO();
        request.setId(1L);
        request.setRequirementType("INTERFACE");
        readonly(() -> service.updateRequirement(request));
        readonly(() -> service.deleteRequirement(1L));
        readonly(() -> service.submitRequirement(1L));
        readonly(() -> service.markEffective(1L));
        readonly(() -> service.archiveRequirement(1L));
        verify(mapper, times(6)).selectById(1L);
        verifyNoMoreInteractions(mapper);
        assertEquals(0, record.getStatus());
    }

    @Test
    void interfaceCannotBecomeBusinessAndRetainsLifecycle() {
        RequirementDO record = record("INTERFACE");
        when(mapper.selectById(1L)).thenReturn(record);
        RequirementSaveReqVO request = new RequirementSaveReqVO();
        request.setId(1L);
        request.setRequirementType("BUSINESS");
        readonly(() -> service.updateRequirement(request));
        verify(mapper).selectById(1L);
        verifyNoMoreInteractions(mapper);
        request.setRequirementType(null);
        service.updateRequirement(request);
        service.submitRequirement(1L);
        assertEquals(1, record.getStatus());
        service.markEffective(1L);
        assertEquals(2, record.getStatus());
        service.archiveRequirement(1L);
        assertEquals(3, record.getStatus());
        service.deleteRequirement(1L);
        verify(mapper).deleteById(1L);
    }

    private static RequirementDO record(String type) {
        RequirementDO record = new RequirementDO();
        record.setId(1L);
        record.setRequirementType(type);
        record.setStatus(0);
        record.setVersion(0L);
        return record;
    }

    private static void readonly(Runnable action) {
        ServiceException error = assertThrows(ServiceException.class, action::run);
        assertEquals(REQUIREMENT_LEGACY_BUSINESS_READ_ONLY.getCode(), error.getCode());
    }
}
