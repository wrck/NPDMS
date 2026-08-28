package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目门禁实例引用行 DO（F-PM01 / V57 `proj_project_gate_reference`）
 * <p>
 * (gate_id, ref_type, ref_code) 结构化三元组，实例化时从模板门禁引用冻结。
 * `gate_id` 由服务层在门禁实例落库后回填。
 * `version` 列暂不接 @Version 拦截器：并发由 uk + 行锁保障。
 */
@TableName("proj_project_gate_reference")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectGateReferenceInstanceDO extends TenantBaseDO {

    /**
     * 门禁引用ID
     */
    @TableId
    private Long id;
    /**
     * 门禁实例ID（proj_project_gate）
     */
    private Long gateId;
    /**
     * 引用类型：TASK/DELIVERABLE/STATE/PROCESS（冻结）
     */
    private String refType;
    /**
     * 引用编码（冻结）
     */
    private String refCode;
    /**
     * 引用版本（流程引用时使用，冻结）
     */
    private String refVersion;
    /**
     * 乐观锁版本列：暂不接 @Version 拦截器，并发由 uk + 行锁保障
     */
    private Integer version;
}
