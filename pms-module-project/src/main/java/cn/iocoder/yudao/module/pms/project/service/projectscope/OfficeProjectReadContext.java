package cn.iocoder.yudao.module.pms.project.service.projectscope;

/** 仅由HTTP边界逐请求校验后设置；不保存授权、不跨请求复用。 */
public final class OfficeProjectReadContext {
    private OfficeProjectReadContext() { }
    public record Scope(Long tenantId, Long userId, Long projectId) { }
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    public static Scope current() { return CURRENT.get(); }
    static void set(Scope scope) { CURRENT.set(scope); }
    static void clear() { CURRENT.remove(); }
}
