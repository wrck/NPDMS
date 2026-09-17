package cn.iocoder.yudao.module.pms.acceptance.api.acceptance;

/** PROJ 项目闭环校验消费的 ACC 验收事实只读契约。 */
public interface ProjectAcceptanceFactApi {

    /** 项目内是否存在终验且状态为已通过或已归档的验收记录。 */
    boolean existsPassedFinalAcceptance(Long tenantId, Long projectId);
}
