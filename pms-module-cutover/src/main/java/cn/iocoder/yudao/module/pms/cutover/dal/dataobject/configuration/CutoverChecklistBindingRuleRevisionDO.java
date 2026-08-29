package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("cut_cutover_checklist_binding_rule_revision")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverChecklistBindingRuleRevisionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long configurationRevisionId;
    private String stableRuleKey;
    private Long itemDefinitionId;
    private Integer itemDefinitionVersion;
    private String dimensionConditionSnapshot;
    private Integer priority;
    private Boolean requiredResult;
    private String statusCode;
    @Version
    private Integer version;
}
