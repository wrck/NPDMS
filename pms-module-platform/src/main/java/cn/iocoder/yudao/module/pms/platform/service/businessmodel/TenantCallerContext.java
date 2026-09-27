package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.support.service.AbstractBusinessApplicationService;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import org.springframework.stereotype.Component;

/**
 * 可信调用上下文平台实现：租户与操作者取自服务端会话上下文。
 * 缺少登录身份时显式失败；后台系统命令契约由入口侧另行授权后构造。
 */
@Component
public class TenantCallerContext implements BusinessCallerContext {

    @Override
    public AbstractBusinessApplicationService.ResolvedCaller require() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException(
                    "CALLER_UNRESOLVED", "业务操作缺少可信操作者身份，后台执行须走系统命令契约");
        }
        return new AbstractBusinessApplicationService.ResolvedCaller(tenantId, userId, null);
    }
}
