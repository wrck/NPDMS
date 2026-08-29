package cn.iocoder.yudao.module.pms.commerce.api.authority;

import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.AuthorityWriteResult;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityWriteCommand;

/** COM受控权威副本写入边界，不包含第三方连接器。 */
public interface CommerceAuthorityWriteApi {

    AuthorityWriteResult apply(CommerceAuthorityWriteCommand command);
}
