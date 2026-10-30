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

/** 章节适用集合。需求：CUT-04/07。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_configuration_plan_section_scope")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CutoverConfigurationPlanSectionScopeDO extends TenantBaseDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 章节ID */
    private Long planSectionId;
    /** CUTOVER_TYPE或CUTOVER_LEVEL */
    private String scopeAxisCode;
    /** 该轴允许的权威编码 */
    private String scopeValue;
    /** 原集合顺序 */
    private Integer sortOrder;
}
