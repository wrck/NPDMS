package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("cut_cutover_checklist_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverChecklistItemDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long checklistId;
    private String stableItemKey;
    @TableField(value = "item_definition_revision_id")
    private Long itemDefinitionId;
    private Integer itemDefinitionVersion;
    private String itemTypeCode;
    private String itemName;
    private String itemDescription;
    private String interfaceFormatCode;
    @TableField(exist = false)
    private String interfaceSchemaSnapshot;
    @TableField(exist = false)
    private String displayConditionSnapshot;
    private String workModeCode;
    private Boolean requiredFlag;
    private String sourceCode;
    private Long deviceId;
    private Long commandTemplateId;
    @TableField(value = "binding_rule_revision_id")
    private Long matchedRuleId;
    @TableField(value = "binding_rule_version")
    private Integer matchedRuleVersion;
    private Boolean applicableFlag;
    private Long customCreatorUserId;
    private Integer sortOrder;
    @Version
    private Integer version;
}
