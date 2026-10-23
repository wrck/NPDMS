package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 方案逐项风险应对。需求：CUT-04/05。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_plan_risk_mitigation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CutoverPlanRiskMitigationDO extends TenantBaseDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 既有方案修订ID */
    private Long planRevisionId;
    /** 当时采用的统一结果ID */
    private Long checklistResultId;
    /** 对应采集项 */
    private Long checklistItemId;
    /** 当时结果版本号 */
    private Integer checklistResultVersion;
    /** 填写值：本项应对措施；草稿尚未填写时为空，风险来源引用仍保留 */
    private String mitigation;
    /** 顺序 */
    private Integer sortOrder;
}
