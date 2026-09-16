package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import com.baomidou.mybatisplus.annotation.TableField;
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
public class ProjectStageInstanceDO extends ProjectExecutionNodeDO<ProjectStageInstanceDO> {

    /**
     * 自定义阶段编码，与标准生命周期绑定独立。
     */
    @TableField("stage_code")
    private String code;
    /**
     * 准入条件说明（快照）
     */
    private String entryCriteria;
    /**
     * 准出条件说明（快照）
     */
    private String exitCriteria;
    private String deviationReason;
    /** 保留字段，暂不参与界面、派工和权限业务。 */
    private String responsibleRole;
    /** 保留字段，暂不参与界面、派工和权限业务。 */
    private Long responsibleUserId;
    private Long definitionRevisionId;
    private Long graphVersion;
    private Boolean startNode;
    private Boolean terminalNode;
    @Override
    protected ProjectStageInstanceDO self() { return this; }
}
