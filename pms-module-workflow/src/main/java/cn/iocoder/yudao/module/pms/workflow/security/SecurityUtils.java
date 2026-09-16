package cn.iocoder.yudao.module.pms.workflow.security;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.security.core.Authentication;

/**
 * 安全工具类（迁移自源工程 pms-common com.dp.plat.common.util.SecurityUtils，
 * 兼容层，委托 yudao {@link SecurityFrameworkUtils}）。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Authentication getAuthentication() {
        return SecurityFrameworkUtils.getAuthentication();
    }

    /**
     * 获取当前登录用户 ID。
     */
    public static Long getCurrentUserId() {
        return SecurityFrameworkUtils.getLoginUserId();
    }

    /**
     * 获取当前用户名（兼容旧 API）。
     * <p>yudao 体系下优先返回用户昵称，未认证时返回 "system"。</p>
     */
    public static String getCurrentUsername() {
        String nickname = SecurityFrameworkUtils.getLoginUserNickname();
        return nickname != null ? nickname : "system";
    }

    /**
     * 获取当前登录用户对象。
     */
    public static LoginUser getLoginUser() {
        return SecurityFrameworkUtils.getLoginUser();
    }

    /**
     * 判断是否已认证。
     */
    public static boolean isAuthenticated() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser != null;
    }
}