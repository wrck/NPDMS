package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("cut_cutover_checklist_item_definition_revision")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverChecklistItemDefinitionRevisionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long configurationRevisionId;
    private String stableItemKey;
    private Integer itemDefinitionVersion;
    private String itemTypeCode;
    private String itemName;
    private String itemDescription;
    private String interfaceFormatCode;
    private String interfaceSchema;
    private String feedbackFormatCode;
    private Boolean requiredFlag;
    private String workModeCode;
    private String externalSourceConfig;
    private String subtableCode;
    private String statusCode;
    private Integer sortOrder;
    @Version
    private Integer version;
}
