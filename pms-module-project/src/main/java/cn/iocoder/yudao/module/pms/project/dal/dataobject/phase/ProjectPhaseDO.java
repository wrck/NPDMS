package cn.iocoder.yudao.module.pms.project.dal.dataobject.phase;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 项目阶段 DO（FR-PROJ-017 / T-V1-PROJ-007、FR-PROJ-016 / T-V1-PROJ-008）。
 * <p>
 * 对应表 {@code pms_project_phase}，承载项目实际阶段（从模板实例化或手工创建）。
 * 唯一索引 {@code (project_id, code)} 保证项目内阶段编码唯一；阶段顺序通过 {@link #sort} 控制。
 */
@TableName("pms_project_phase")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPhaseDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 来源阶段模板编号
     */
    private Long templateId;
    /**
     * 阶段名称
     */
    private String name;
    /**
     * 阶段编码，项目内唯一
     */
    private String code;
    /**
     * 排序号
     */
    private Integer sort;
    /**
     * 状态：0 未开始 1 进行中 2 已完成 3 已跳过
     */
    private Integer status;
    /**
     * 建议开始时间
     */
    private LocalDateTime suggestedStartTime;
    /**
     * 建议结束时间
     */
    private LocalDateTime suggestedEndTime;
    /**
     * 计划开始时间
     */
    private LocalDateTime planStartTime;
    /**
     * 计划结束时间
     */
    private LocalDateTime planEndTime;
    /**
     * 实际开始时间
     */
    private LocalDateTime actualStartTime;
    /**
     * 实际结束时间
     */
    private LocalDateTime actualEndTime;
    /**
     * 偏差原因
     */
    private String deviationReason;
    /**
     * 准入条件
     */
    private String entryCriteria;
    /**
     * 退出条件
     */
    private String exitCriteria;
    /**
     * 负责角色编码
     */
    private String responsibleRole;
    /**
     * 负责用户编号
     */
    private Long responsibleUserId;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
}
