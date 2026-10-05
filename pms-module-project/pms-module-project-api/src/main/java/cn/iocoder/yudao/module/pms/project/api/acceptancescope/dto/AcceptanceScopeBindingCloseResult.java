package cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto;

/**
 * 关闭结果：closedBindings 为本次落 effective_to 的锁定条数；
 * replayed=true 表示无活跃锁定可关（从未绑定或已关闭），目标状态本就成立。
 */
public record AcceptanceScopeBindingCloseResult(
        int closedBindings,
        boolean replayed) {
}
