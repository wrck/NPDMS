package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

/**
 * P3 清单 HTTP 受信上下文。
 *
 * <p>F-CUT-002 生产依赖接通前只允许测试显式提供，不注册生产实现。</p>
 */
public interface CutoverChecklistRequestContext {

    TrustedContext current();

    record TrustedContext(Long tenantId, Long actorId, String correlationId) {
        public TrustedContext {
            if (tenantId == null || tenantId < 0 || actorId == null || actorId <= 0
                    || correlationId == null || correlationId.isBlank()
                    || !correlationId.equals(correlationId.trim()) || correlationId.length() > 128) {
                throw new IllegalArgumentException("invalid trusted cutover checklist context");
            }
        }
    }
}
