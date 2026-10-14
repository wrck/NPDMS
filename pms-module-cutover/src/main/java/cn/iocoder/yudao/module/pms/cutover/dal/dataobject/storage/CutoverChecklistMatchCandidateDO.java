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

/** 实际命中规则与选择。需求：CUT-03。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_checklist_match_candidate")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CutoverChecklistMatchCandidateDO extends TenantBaseDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 清单修订ID */
    private Long checklistId;
    /** 采集项稳定键 */
    private String stableItemKey;
    /** 精确定义修订ID */
    private Long itemDefinitionRevisionId;
    /** 精确规则修订ID */
    private Long bindingRuleRevisionId;
    /** 匹配时规则版本 */
    private Long bindingRuleVersion;
    /** 匹配时优先级 */
    private Integer priority;
    /** 本次规则要求的必选性 */
    private Boolean requiredResultFlag;
    /** 本次是否采用该候选 */
    private Boolean selectedFlag;
}
