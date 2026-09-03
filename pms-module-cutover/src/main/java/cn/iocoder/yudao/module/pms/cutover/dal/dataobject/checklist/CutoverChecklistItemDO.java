package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist;

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
    private Long itemDefinitionId;
    private Integer itemDefinitionVersion;
    private String itemTypeCode;
    private String itemName;
    private String itemDescription;
    private String interfaceFormatCode;
    private String interfaceSchemaSnapshot;
    private String displayConditionSnapshot;
    private String workModeCode;
    private Boolean requiredFlag;
    private String sourceCode;
    private Long deviceId;
    private Long commandTemplateId;
    private Long matchedRuleId;
    private Integer matchedRuleVersion;
    private Boolean applicableFlag;
    private Long customCreatorUserId;
    private Integer sortOrder;
    @Version
    private Integer version;
}
