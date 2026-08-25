package cn.iocoder.yudao.module.pms.platform.api.authorization;

import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageResult;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantQuery;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantRevokeCommand;

import java.util.List;

/** 平台通用授权事实公开契约，不承载资源内部的层级或路径语义。 */
public interface AuthorizationGrantApi {

    AuthorizationGrantDTO create(AuthorizationGrantCreateCommand command);

    AuthorizationGrantDTO revoke(AuthorizationGrantRevokeCommand command);

    AuthorizationGrantDTO get(Long tenantId, Long grantId);

    List<AuthorizationGrantDTO> listEffective(AuthorizationGrantQuery query);

    AuthorizationGrantPageResult page(AuthorizationGrantPageQuery query);
}
