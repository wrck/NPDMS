package cn.iocoder.yudao.module.system.service.oauth2;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2RefreshTokenMapper;
import cn.iocoder.yudao.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2TokenServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private OAuth2TokenServiceImpl service;
    @Mock
    private OAuth2AccessTokenMapper accessTokenMapper;
    @Mock
    private OAuth2RefreshTokenMapper refreshTokenMapper;
    @Mock
    private OAuth2AccessTokenRedisDAO accessTokenRedisDAO;
    @Mock
    private OAuth2ClientService clientService;
    @Mock
    private AdminUserService adminUserService;

    @Test
    void createAccessTokenUsesAdminUserTenantWhenTenantContextIsDisabled() {
        OAuth2ClientDO client = new OAuth2ClientDO().setClientId("default")
                .setAccessTokenValiditySeconds(1800).setRefreshTokenValiditySeconds(2592000);
        when(clientService.validOAuthClientFromCache("default")).thenReturn(client);
        AdminUserDO adminUser = new AdminUserDO().setId(1L).setNickname("管理员");
        adminUser.setTenantId(1L);
        when(adminUserService.getUser(1L)).thenReturn(adminUser);

        OAuth2AccessTokenDO result = service.createAccessToken(
                1L, UserTypeEnum.ADMIN.getValue(), "default", List.of("user.read"));

        ArgumentCaptor<OAuth2RefreshTokenDO> refreshTokenCaptor =
                ArgumentCaptor.forClass(OAuth2RefreshTokenDO.class);
        verify(refreshTokenMapper).insert(refreshTokenCaptor.capture());
        assertEquals(1L, refreshTokenCaptor.getValue().getTenantId());
        assertEquals(1L, result.getTenantId());
    }
}
