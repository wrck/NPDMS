package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目交付件实例 DO（F-PM01 / V57 `proj_project_deliverable`）
 * <p>
 * 实例化时从模板冻结快照（含 required 必需标志），初始状态 PENDING。
 * `source_definition_id` 为定义行映射槽（内容模型无定义行 ID 时为 NULL）。
 * `version` 列暂不接 @Version 拦截器：并发由 uk + 行锁保障。
 */
@TableName("proj_project_deliverable")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectDeliverableInstanceDO extends TenantBaseDO {

    /**
     * 交付件实例ID
     */
    @TableId
    private Long id;
    /**
     * 项目ID
     */
    private Long projectId;
    /**
     * 交付件码（实例化时冻结，项目内唯一）
     */
    private String deliverableCode;
    /**
     * 交付件名称（快照）
     */
    private String name;
    /**
     * 所属阶段码
     */
    private String stageCode;
    /**
     * 关联任务码（NULL=阶段级）
     */
    private String taskCode;
    /**
     * 必需标志（快照）
     */
    private Boolean required;
    /**
     * 冻结来源：模板交付件定义ID（内容模型无定义行 ID 时为 NULL）
     */
    private Long sourceDefinitionId;
    /**
     * 交付件状态（字典 pms_project_deliverable_status：PENDING/SUBMITTED/ACCEPTED，初始 PENDING）
     */
    private String status;
    /**
     * 乐观锁版本列：暂不接 @Version 拦截器，并发由 uk + 行锁保障
     */
    private Integer version;
}
