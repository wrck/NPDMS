package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** SOL项目工期版本。 */
@TableName("sol_construction_plan_revision")
@Data
public class ConstructionPlanRevisionDO implements Serializable {

    public static final String BASIS_DATE_RANGE = "DATE_RANGE";
    public static final String BASIS_DURATION_FROM_START = "DURATION_FROM_START";

    @TableId
    private Long id;
    private Long planId;
    private Integer revisionNo;
    private String calculationBasisCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer durationDays;
    private Long sourceChangeId;
    private LocalDateTime frozenAt;
    private LocalDateTime effectiveAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Integer version;
    private Long tenantId;

}
