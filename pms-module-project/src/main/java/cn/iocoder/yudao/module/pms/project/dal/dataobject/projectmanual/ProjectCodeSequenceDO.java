package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目平台编码序列 DO（F-PM01 / V57 `proj_project_code_sequence`）
 * <p>
 * `SELECT ... FOR UPDATE` 行锁原子递增；V1 命名空间 PLATFORM_ROOT（租户级平台流水），
 * PM-02 预留 ROOT:&lt;code_root_id&gt;（子项目命名空间）。
 * `version` 列暂不接 @Version 拦截器：并发由 uk + 行锁保障。
 */
@TableName("proj_project_code_sequence")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectCodeSequenceDO extends TenantBaseDO {

    /**
     * 序列ID
     */
    @TableId
    private Long id;
    /**
     * 编码命名空间：V1=PLATFORM_ROOT（租户级平台流水）；PM-02 预留 ROOT:<code_root_id>
     */
    private String codeNamespace;
    /**
     * 下一个可分配流水号
     */
    private Long nextValue;
    /**
     * 乐观锁版本列：暂不接 @Version 拦截器，并发由 uk + 行锁保障
     */
    private Integer version;
}
