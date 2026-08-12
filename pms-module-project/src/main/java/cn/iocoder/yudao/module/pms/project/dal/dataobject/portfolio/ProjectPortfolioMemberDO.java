package cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 项目组合成员 DO
 */
@TableName("pms_project_portfolio_member")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPortfolioMemberDO extends TenantBaseDO {

    /**
     * 成员编号
     */
    @TableId
    private Long id;
    /**
     * 组合编号
     */
    private Long portfolioId;
    /**
     * 项目编号
     */
    private Long projectId;
    /**
     * 纳入类型 STATIC 静态 / DYNAMIC 动态
     */
    private String inclusionType;
    /**
     * 纳入原因
     */
    private String inclusionReason;
    /**
     * 排除原因
     */
    private String exclusionReason;
    /**
     * 状态：1纳入 2排除
     */
    private Integer status;
    /**
     * 乐观锁版本
     */
    @Version
    private Integer version;

}
