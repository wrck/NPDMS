package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 规则维度允许值。需求：CUT-03/07。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_checklist_binding_condition")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CutoverChecklistBindingConditionDO extends TenantBaseDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 规则修订ID */
    private Long bindingRuleRevisionId;
    /** 维度定义ID */
    private Long configurationDimensionId;
    /** 同维度允许值次序 */
    private Integer valueNo;
    /** 动态标量实际类型；只用于允许配置的元数据/参数 */
    private String valueTypeCode;
    /** 字符串型标量实际值 */
    private String valueText;
    /** 数字型标量实际值 */
    private BigDecimal valueNumber;
    /** 布尔型标量实际值 */
    private Boolean valueBoolean;
    /** 日期时间型标量实际值 */
    private LocalDateTime valueDatetime;
}
