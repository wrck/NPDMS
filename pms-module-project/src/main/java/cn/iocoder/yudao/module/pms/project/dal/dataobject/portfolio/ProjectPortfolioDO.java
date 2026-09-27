package cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

/**
 * PMS 项目组合 DO
 */
@TableName("proj_project_portfolio")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPortfolioDO extends BaseBusinessEntity {

    /**
     * 组合编号
     */
    /**
     * 组合编码，全局唯一
     */
    private String code;
    /**
     * 组合名称
     */
    private String name;
    /**
     * 组合用途（战略/客户/区域/计划/专项）
     */
    private String purpose;
    /**
     * 负责人用户编号
     */
    private Long ownerUserId;
    /**
     * 有效期开始
     */
    private LocalDate validFrom;
    /**
     * 有效期结束
     */
    private LocalDate validTo;
    /**
     * 状态：0草稿 1已发布 2已归档
     */
    private Integer status;
    /**
     * 统计目标（JSON 文本）
     */
    private String targetMetrics;
    /**
     * 成员类型 STATIC 静态 / DYNAMIC 动态
     */
    private String memberType;
    /**
     * 乐观锁版本
     */
}
