package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * API 命令幂等记录 DO（F-PM01 / V57 `proj_idempotency_record`）
 * <p>
 * 作用域 tenant+command+actor+idempotency_key；同键同摘要重放返回原资源，同键异摘要 409。
 * 幂等拦截在 T4 Controller 层做，本 DO 供存取。
 * `version` 列暂不接 @Version 拦截器：并发由 uk + 行锁保障。
 */
@TableName("proj_idempotency_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class IdempotencyRecordDO extends TenantBaseDO {

    /**
     * 幂等记录ID
     */
    @TableId
    private Long id;
    /**
     * 命令标识（如 ProjectCreate）
     */
    private String command;
    /**
     * 操作者用户ID
     */
    private Long actorId;
    /**
     * 幂等键（Header Idempotency-Key）
     */
    private String idempotencyKey;
    /**
     * 请求体 SHA-256 摘要（同键异摘要拒绝）
     */
    private String requestDigest;
    /**
     * 首次成功响应载荷（重放原样返回）
     */
    private String responsePayload;
    /**
     * 记录状态：COMPLETED/FAILED
     */
    private String status;
    /**
     * 乐观锁版本列：暂不接 @Version 拦截器，并发由 uk + 行锁保障
     */
    private Integer version;
}
