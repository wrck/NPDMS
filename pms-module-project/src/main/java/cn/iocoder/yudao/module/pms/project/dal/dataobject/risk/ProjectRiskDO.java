package cn.iocoder.yudao.module.pms.project.dal.dataobject.risk;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 项目风险 DO（FR-PROJ-026 / T-V1-PROJ-009）。
 * <p>
 * 对应表 {@code pms_project_risk}，承载项目风险登记册。
 * 状态：0 已识别、1 处理中、2 已关闭、3 已发生；状态迁移由 {@code RiskStatusRules} 校验。
 */
@TableName("pms_project_risk")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectRiskDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 风险标题
     */
    private String title;
    /**
     * 风险等级：HIGH / MEDIUM / LOW
     */
    private String riskLevel;
    /**
     * 风险类型
     */
    private String riskType;
    /**
     * 风险原因
     */
    private String cause;
    /**
     * 风险影响
     */
    private String impact;
    /**
     * 缓解措施
     */
    private String mitigation;
    /**
     * 应急措施
     */
    private String contingency;
    /**
     * 风险负责人用户编号
     */
    private Long ownerUserId;
    /**
     * 状态：0 已识别 1 处理中 2 已关闭 3 已发生
     */
    private Integer status;
    /**
     * 预警阈值
     */
    private String warningThreshold;
    /**
     * 复核备注
     */
    private String reviewNotes;
    /**
     * 识别时间
     */
    private LocalDateTime identifiedAt;
    /**
     * 关闭时间
     */
    private LocalDateTime closedAt;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
}
