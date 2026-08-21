package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目阶段实例 DO（F-PM01 / V57 `proj_project_stage`）
 * <p>
 * 实例化时从模板冻结快照；`source_definition_id` 为定义行映射槽
 * （F-PM03 `getRevisionContent` 不含定义行 ID 时保持 NULL）。
 * `version` 列暂不接 @Version 拦截器：并发由 uk + 行锁保障。
 */
@TableName("proj_project_stage")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectStageInstanceDO extends TenantBaseDO {

    /**
     * 阶段实例ID
     */
    @TableId
    private Long id;
    /**
     * 项目ID（proj_project）
     */
    private Long projectId;
    /**
     * 阶段码（S0～S6，实例化时冻结）
     */
    private String stageCode;
    /**
     * 阶段名称（快照）
     */
    private String name;
    /**
     * 阶段顺序（快照）
     */
    private Integer sortOrder;
    /**
     * 准入条件说明（快照）
     */
    private String entryCriteria;
    /**
     * 准出条件说明（快照）
     */
    private String exitCriteria;
    /**
     * 冻结来源：模板阶段定义ID（proj_project_template_stage_definition；内容模型无定义行 ID 时为 NULL）
     */
    private Long sourceDefinitionId;
    /**
     * 阶段实例状态（字典 pms_project_stage_status：PENDING/ACTIVE/DONE，实例化时最小 sort_order 阶段置 ACTIVE）
     */
    private String status;
    /**
     * 乐观锁版本列：暂不接 @Version 拦截器，并发由 uk + 行锁保障
     */
    private Integer version;
}
