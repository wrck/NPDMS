package cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 项目组合动态规则 DO
 */
@TableName("pms_project_portfolio_rule")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPortfolioRuleDO extends TenantBaseDO {

    /**
     * 规则编号
     */
    @TableId
    private Long id;
    /**
     * 组合编号
     */
    private Long portfolioId;
    /**
     * 规则字段 CUSTOMER/REGION/TYPE/STATUS
     */
    private String ruleField;
    /**
     * 规则操作符 EQ/NE/IN/LIKE
     */
    private String ruleOperator;
    /**
     * 规则值（IN 用逗号分隔）
     */
    private String ruleValue;
    /**
     * 乐观锁版本
     */
    @Version
    private Integer version;

}
