package cn.iocoder.yudao.module.pms.engineering.service.configuration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationLocalLifecycleTest {
    private final ConfigurationMapper mapper = mock(ConfigurationMapper.class);
    private final ConfigurationServiceImpl service = new ConfigurationServiceImpl();
    private ConfigurationDO row;
    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(service, "configurationMapper", mapper);
        row = new ConfigurationDO(); row.setId(1L); row.setProjectId(7L); row.setCode("CFG-TEST"); row.setStatus(0); row.setVersion(6);
        when(mapper.selectById(1L)).thenReturn(row);
    }

    @Test void startAndCompleteLeaveVersionIncrementToMybatis() {
        int[] storedVersion = {6};
        when(mapper.updateById(any(ConfigurationDO.class))).thenAnswer(call -> {
            ConfigurationDO update = call.getArgument(0);
            assertEquals(storedVersion[0], update.getVersion());
            update.setVersion(++storedVersion[0]);
            return 1;
        });
        service.startConfiguration(1L);
        assertEquals(1, row.getStatus());
        assertNotNull(row.getDebugTime());
        service.completeConfiguration(1L);
        assertEquals(2, row.getStatus());
        assertEquals(8, row.getVersion());
    }

    @Test void completedRecordCannotBeEditedOrDeleted() {
        row.setStatus(2);
        ConfigurationSaveReqVO request = new ConfigurationSaveReqVO(); request.setId(1L);
        assertEquals(CONFIGURATION_STATUS_INVALID.getCode(), assertThrows(ServiceException.class, () -> service.updateConfiguration(request)).getCode());
        assertEquals(CONFIGURATION_STATUS_INVALID.getCode(), assertThrows(ServiceException.class, () -> service.deleteConfiguration(1L)).getCode());
        verify(mapper, never()).updateById(any(ConfigurationDO.class));
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test void failedUpdateCannotBeReportedAsSuccess() {
        when(mapper.updateById(any(ConfigurationDO.class))).thenReturn(0);
        assertEquals(CONFIGURATION_VERSION_NOT_MATCH.getCode(), assertThrows(ServiceException.class, () -> service.startConfiguration(1L)).getCode());
    }

    @Test void normalEditingCannotSetACompletedStateFromTheRequest() {
        row.setStatus(1);
        ConfigurationSaveReqVO request = new ConfigurationSaveReqVO();
        request.setId(1L); request.setProjectId(7L); request.setCode("CFG-TEST"); request.setStatus(2); request.setVersion(6);
        when(mapper.updateById(any(ConfigurationDO.class))).thenAnswer(call -> {
            ConfigurationDO update = call.getArgument(0);
            assertEquals(1, update.getStatus());
            assertEquals(6, update.getVersion());
            return 1;
        });
        service.updateConfiguration(request);
    }

    @Test void newConfigurationAlwaysStartsPending() {
        ConfigurationSaveReqVO request = new ConfigurationSaveReqVO();
        request.setProjectId(7L); request.setCode("CFG-NEW"); request.setStatus(2);
        when(mapper.insert(any(ConfigurationDO.class))).thenAnswer(call -> {
            ConfigurationDO inserted = call.getArgument(0);
            assertEquals(0, inserted.getStatus());
            inserted.setId(2L);
            return 1;
        });
        assertEquals(2L, service.createConfiguration(request));
    }
}
