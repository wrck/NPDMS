package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 方案来源追溯 DO
 */
@TableName("pms_eng_solution_source")
@Data
@EqualsAndHashCode(callSuper = true)
public class SolutionSourceDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 方案编号
     */
    private Long solutionId;
    /**
     * SURVEY 工勘 / REQUIREMENT 需求
     */
    private String sourceType;
    /**
     * 来源业务编号
     */
    private Long sourceId;
    /**
     * 来源业务编码
     */
    private String sourceCode;
    /**
     * 带入时的来源快照（JSON）
     */
    private String sourceSnapshot;

}
