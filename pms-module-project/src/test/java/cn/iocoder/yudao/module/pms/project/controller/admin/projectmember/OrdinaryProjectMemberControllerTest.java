package cn.iocoder.yudao.module.pms.project.controller.admin.projectmember;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.service.projectmember.OrdinaryProjectMemberService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrdinaryProjectMemberControllerTest {
    @Test
    void enrichesOnlyAuthorizedPageMembersWithCurrentDirectoryContacts() {
        TenantContextHolder.setTenantId(1L);
        try {
            var members = mock(OrdinaryProjectMemberService.class);
            var users = mock(AdminUserApi.class);
            var member = new ProjectMemberAssignmentDO(); member.setId(1L); member.setUserId(7L);
            when(members.page(eq(10L), any(), any())).thenReturn(new PageResult<>(List.of(member), 1L));
            var contact = new AdminUserRespDTO(); contact.setId(7L); contact.setMobile("13800000000");
            contact.setEmail("member@example.test");
            when(users.getUserMap(List.of(7L))).thenReturn(Map.of(7L, contact));
            var result = new OrdinaryProjectMemberController(members, users)
                    .page(10L, new OrdinaryProjectMemberController.MemberPageRequest()).getData();
            assertEquals("member@example.test", result.getList().getFirst().getEmail());
            assertEquals("13800000000", result.getList().getFirst().getMobile());
            verify(users).getUserMap(List.of(7L));
        } finally { TenantContextHolder.clear(); }
    }

    @Test
    void deniedProjectNeverReadsDirectoryContacts() {
        TenantContextHolder.setTenantId(1L);
        try {
            var members = mock(OrdinaryProjectMemberService.class);
            var users = mock(AdminUserApi.class);
            when(members.page(eq(10L), any(), any())).thenThrow(new IllegalStateException("scope denied"));
            assertThrows(IllegalStateException.class, () -> new OrdinaryProjectMemberController(members, users)
                    .page(10L, new OrdinaryProjectMemberController.MemberPageRequest()));
            verifyNoInteractions(users);
        } finally { TenantContextHolder.clear(); }
    }
}
