package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectschedule;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

/**
 * 3.1 工期建议计划时间规则（Demo 页面9 / Excel 3.1）。
 * <p>
 * 建议最迟完成 = 参照时间 - 偏移；参照时间按来源取自身计划验收时间（PMS_IMPORTED）、
 * 工期要求（DURATION_REQUIRE，项目结束日期）或参照阶段建议（STAGE_PLAN，须晚于自身阶段）。
 * signing_method 为空表示全部签约方式，精确变体行优先于空行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proj_stage_suggestion_rule")
public class StageSuggestionRuleDO extends BaseBusinessEntity {

    /** 参与计划的阶段编码。 */
    private String stageCode;
    /** 签约方式（字典 pms_signing_method）；空=全部签约方式。 */
    private String signingMethod;
    /** 建议来源：PMS_IMPORTED / DURATION_REQUIRE / STAGE_PLAN。 */
    private String sourceType;
    /** 参照阶段编码（STAGE_PLAN 时必填）。 */
    private String referenceStageCode;
    /** 参照时间偏移月数（负=提前）。 */
    private Integer offsetMonths;
    /** 参照时间偏移天数（负=提前，如 -14=2周）。 */
    private Integer offsetDays;
    private String remark;
    /** 停用行不参与推算。 */
    private Boolean enabled;
}
