package cn.iocoder.yudao.module.system.api.permission;

/**
 * System 显式角色—菜单授权查询，不使用超级管理员快捷放行。
 *
 * <p>调用方必须已校验业务对象范围和候选人资格，并传入与当前租户上下文一致的租户。
 * 本接口只证明功能权限，不授予项目、待办、用户列表或材料正文访问权限。
 */
public interface ExplicitPermissionApi {

    /**
     * 查询启用、未删除且同租户的用户及真实角色—菜单授权链。
     * 权限码区分大小写、精确匹配；菜单隐藏不影响授权。
     * 超级管理员也必须具有真实显式授权。缺失/非法参数或租户上下文不一致返回 false，
     * 数据库异常向调用方传播，不得降级放行。普通查询不防止检查后的并发撤权。
     */
    boolean hasExplicitPermission(Long tenantId, Long userId, String permission);

    /**
     * 必须在调用方现有 Spring 事务内调用；当前读并按 user、role、user_role、role_menu、
     * menu 顺序锁定真实授权链（各表按主键升序），锁持续到外层事务结束。
     * 调用方必须在同一事务内完成受保护动作，不能将结果缓存后用于其他事务。
     * 授权链不存在或失效返回 false；无事务调用拒绝执行。
     */
    boolean lockAndCheck(Long tenantId, Long userId, String permission);
}
