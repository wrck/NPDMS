package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.IdempotencyRecordDO;

/**
 * API 命令幂等记录 Service（F-PM01，薄存取）
 * <p>
 * 幂等拦截（同键同摘要重放/同键异摘要 409）在 T4 Controller 层做，这里只供记录存取。
 */
public interface IdempotencyRecordService {

    /**
     * 按作用域键查询幂等记录（tenant+command+actor+idempotency_key；不存在返回 null）。
     */
    IdempotencyRecordDO findByKey(Long tenantId, String command, Long actorId, String idempotencyKey);

    /**
     * 保存幂等记录（首次成功响应载荷由调用方组装）。
     */
    void save(IdempotencyRecordDO record);
}
