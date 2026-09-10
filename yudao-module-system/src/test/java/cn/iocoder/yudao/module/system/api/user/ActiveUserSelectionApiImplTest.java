package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.ActiveUserSelectionQuery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveUserSelectionApiImplTest {
    @Mock AdminUserMapper mapper;
    @InjectMocks ActiveUserSelectionApiImpl api;
    @BeforeEach void before() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void after() { TenantContextHolder.clear(); }

    @Test void carriesTrustedTenantAndOnlyReturnsSelectionFields() {
        AdminUserDO user = new AdminUserDO();
        user.setId(9L); user.setUsername("engineer"); user.setNickname("张工"); user.setDeptId(25L);
        user.setMobile("not-for-selection");
        when(mapper.selectActiveSelectionCount(any())).thenReturn(1L);
        when(mapper.selectActiveSelectionPage(any())).thenReturn(List.of(user));
        var result = api.page(query(2, 20, " 张工 ", null));
        var query = ArgumentCaptor.forClass(ActiveUserSelectionQuery.class);
        verify(mapper).selectActiveSelectionPage(query.capture());
        assertEquals(1L, query.getValue().tenantId());
        assertEquals(2, query.getValue().pageNo());
        assertEquals("张工", query.getValue().keyword());
        assertNull(query.getValue().userIds());
        assertEquals("PROJECT_MANAGER", query.getValue().roleCode());
        assertNull(query.getValue().companyId());
        assertEquals(new ActiveUserSelectionApi.User(9L, "engineer", "张工", 25L), result.getList().getFirst());
    }

    @Test void emptyExplicitSelectionDoesNotExpandToAllUsers() {
        assertTrue(api.page(query(1, 20, null, Set.of())).getList().isEmpty());
        verifyNoInteractions(mapper);
    }

    @Test void rejectsUnboundedOrInvalidRequests() {
        for (var query : List.of(query(0, 20, null, null),
                query(1, 101, null, null),
                query(1, 20, "x".repeat(65), null),
                query(1, 20, null, Set.of(-1L)))) {
            assertThrows(ServiceException.class, () -> api.page(query));
        }
        verifyNoInteractions(mapper);
    }

    @Test void rejectsMissingOrUnknownSystemRoleAndInvalidQualification() {
        assertThrows(ServiceException.class, () -> api.page(new ActiveUserSelectionApi.Query(1, 20, null, null, null, null)));
        assertThrows(ServiceException.class, () -> api.page(new ActiveUserSelectionApi.Query(1, 20, null, null, "ENGINEER", null)));
        assertThrows(ServiceException.class, () -> api.page(new ActiveUserSelectionApi.Query(1, 20, null, null, "SERVICE_MANAGER",
                new ActiveUserSelectionApi.Qualification(8L, 25L, null, null))));
        verifyNoInteractions(mapper);
    }

    @Test void preservesCompanyAndDepartmentFromSameQualification() {
        when(mapper.selectActiveSelectionCount(any())).thenReturn(0L);
        api.page(new ActiveUserSelectionApi.Query(1, 20, null, null, "SERVICE_MANAGER",
                new ActiveUserSelectionApi.Qualification(8L, 25L, "OFFICE", null)));
        var captured = ArgumentCaptor.forClass(ActiveUserSelectionQuery.class);
        verify(mapper).selectActiveSelectionCount(captured.capture());
        assertEquals(8L, captured.getValue().companyId());
        assertEquals(25L, captured.getValue().departmentId());
        assertEquals("OFFICE", captured.getValue().departmentCode());
        verify(mapper, never()).selectActiveSelectionPage(any());
    }

    private static ActiveUserSelectionApi.Query query(int page, int size, String keyword, Set<Long> ids) {
        return new ActiveUserSelectionApi.Query(page, size, keyword, ids, "PROJECT_MANAGER", null);
    }
}
