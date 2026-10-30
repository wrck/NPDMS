package cn.iocoder.yudao.module.pms.engineering.service.configuration;

import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberSelectionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationDebuggerMembershipTest {
    final ConfigurationServiceImpl service = new ConfigurationServiceImpl();
    final ConfigurationMapper records = mock(ConfigurationMapper.class);
    final ProjectMemberSelectionApi members = mock(ProjectMemberSelectionApi.class);
    final EngineeringRecordCodeGenerator codes = mock(EngineeringRecordCodeGenerator.class);
    final ConfigurationDO existing = new ConfigurationDO();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var actor = new LoginUser(); actor.setId(5L); actor.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(actor, new MockHttpServletRequest());
        ReflectionTestUtils.setField(service, "configurationMapper", records);
        ReflectionTestUtils.setField(service, "projectMembers", members);
        ReflectionTestUtils.setField(service, "recordCodeGenerator", codes);
        existing.setId(1L); existing.setProjectId(7L); existing.setDebuggerUserId(9L); existing.setStatus(0); existing.setVersion(3L);
        when(records.selectById(1L)).thenReturn(existing);
        when(records.updateById(any(ConfigurationDO.class))).thenReturn(1);
        when(records.insert(any(ConfigurationDO.class))).thenAnswer(inv -> { inv.<ConfigurationDO>getArgument(0).setId(2L); return 1; });
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void nonMemberDefaultCannotCreateOrWriteHistory() {
        doThrow(new IllegalArgumentException("not a member")).when(members).requireCurrentUser(any());
        assertThrows(IllegalArgumentException.class, () -> service.createConfiguration(request(null, 7L, 9L)));
        verify(records, never()).insert(any(ConfigurationDO.class));
        verifyNoInteractions(codes);
    }
    @Test void currentMemberCanCreate() {
        assertEquals(2L, service.createConfiguration(request(null, 7L, 9L)));
        verify(members).requireCurrentUser(argThat(q -> q.projectId().equals(7L) && q.userId().equals(9L)
                && q.tenantId().equals(1L) && q.actorId().equals(5L)));
    }
    @Test void unchangedHistoricalDebuggerIsRetainedWithoutNewQualification() {
        service.updateConfiguration(request(1L, 7L, 9L));
        verifyNoInteractions(members);
        verify(records).updateById(argThat((ConfigurationDO row) -> row.getDebuggerUserId().equals(9L)));
        assertEquals(9L, existing.getDebuggerUserId());
    }
    @Test void changingPersonOrProjectRevalidatesBeforeWrite() {
        doThrow(new IllegalArgumentException("not a member")).when(members).requireCurrentUser(any());
        assertThrows(IllegalArgumentException.class, () -> service.updateConfiguration(request(1L, 7L, 10L)));
        assertThrows(IllegalArgumentException.class, () -> service.updateConfiguration(request(1L, 8L, 9L)));
        assertThrows(IllegalArgumentException.class, () -> service.updateConfiguration(request(1L, 8L, null)));
        verify(records, never()).updateById(any(ConfigurationDO.class));
        assertEquals(7L, existing.getProjectId()); assertEquals(9L, existing.getDebuggerUserId());
    }
    private ConfigurationSaveReqVO request(Long id, Long project, Long user) {
        var row = new ConfigurationSaveReqVO(); row.setId(id); row.setProjectId(project); row.setDebuggerUserId(user); row.setVersion(3); return row;
    }
}
