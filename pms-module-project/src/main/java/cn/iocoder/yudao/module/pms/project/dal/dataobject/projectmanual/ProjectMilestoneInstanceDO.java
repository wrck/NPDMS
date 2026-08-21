package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目里程碑实例 DO（F-PM01 / V57 `proj_project_milestone`）
 * <p>
 * 实例化时从模板冻结快照，初始状态 PENDING。
 * `source_definition_id` 为定义行映射槽（内容模型无定义行 ID 时为 NULL）。
 * `version` 列暂不接 @Version 拦截器：并发由 uk + 行锁保障。
 */
@TableName("proj_project_milestone")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectMilestoneInstanceDO extends TenantBaseDO {

    /**
     * 里程碑实例ID
     */
    @TableId
    private Long id;
    /**
     * 项目ID
     */
    private Long projectId;
    /**
     * 里程碑码（实例化时冻结，项目内唯一）
     */
    private String milestoneCode;
    /**
     * 里程碑名称（快照）
     */
    private String name;
    /**
     * 所属阶段码
     */
    private String stageCode;
    /**
     * 时点说明（快照）
     */
    private String timing;
    /**
     * 达成标准（快照）
     */
    private String criteria;
    /**
     * 冻结来源：模板里程碑定义ID（内容模型无定义行 ID 时为 NULL）
     */
    private Long sourceDefinitionId;
    /**
     * 里程碑状态（字典 pms_project_milestone_status：PENDING/ACHIEVED，初始 PENDING）
     */
    private String status;
    /**
     * 乐观锁版本列：暂不接 @Version 拦截器，并发由 uk + 行锁保障
     */
    private Integer version;
}
