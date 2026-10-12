package cn.iocoder.yudao.module.system.api.migration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.mysql.user.UserMigrationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SystemHistoricalUsernameIdentityTest {
    UserMigrationMapper users=mock(UserMigrationMapper.class);
    SystemUserMigrationApiImpl api=new SystemUserMigrationApiImpl(users,null,null,null,null,null);
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    @Test void emptyOrForeignTenantCannotBroadenLookup() {
        TenantContextHolder.setTenantId(1L);assertTrue(api.getHistoricalIdentitiesByUsername(1L,Set.of()).isEmpty());
        assertThrows(IllegalArgumentException.class,()->api.getHistoricalIdentitiesByUsername(2L,Set.of("c00001")));verifyNoInteractions(users);
    }
    @Test void collationCaseVariantsAreNotAcceptedAsHistoricalActors() {
        TenantContextHolder.setTenantId(1L);
        when(users.selectHistoricalIdentitiesByUsername(any())).thenReturn(List.of(new SystemUserMigrationApi.HistoricalIdentity(10L,"c00001"),new SystemUserMigrationApi.HistoricalIdentity(11L,"C00001")));
        assertEquals(List.of(new SystemUserMigrationApi.HistoricalIdentity(10L,"c00001")),api.getHistoricalIdentitiesByUsername(1L,Set.of("c00001")));
    }
    @Test void historicalMemberReturnsTheFormalEmployeeNumberWithoutInferringItFromLogin() {
        TenantContextHolder.setTenantId(1L);
        when(users.selectHistoricalMemberIdentities(any())).thenReturn(List.of(
            new SystemUserMigrationApi.MemberIdentity(10L,"c00001","EHR-OTHER"),
            new SystemUserMigrationApi.MemberIdentity(11L,"C00001","00001")));
        assertEquals(List.of(new SystemUserMigrationApi.MemberIdentity(10L,"c00001","EHR-OTHER")),
            api.getHistoricalMemberIdentities(1L,Set.of("c00001")));
    }
    @Test void memberEmptySetAndTenantMismatchNeverQueryAllUsers() {
        TenantContextHolder.setTenantId(1L);
        assertTrue(api.getHistoricalMemberIdentities(1L,Set.of()).isEmpty());
        assertThrows(IllegalArgumentException.class,()->api.getHistoricalMemberIdentities(2L,Set.of("c00001")));
        verifyNoInteractions(users);
    }
}
