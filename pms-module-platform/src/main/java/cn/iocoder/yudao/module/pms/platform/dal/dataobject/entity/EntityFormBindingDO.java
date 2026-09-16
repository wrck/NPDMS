package cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plt_entity_form_binding")
public class EntityFormBindingDO extends TenantBaseDO {
    @TableId private Long id;
    private String ownerModule;
    private String entityType;
    private Long entityId;
    private Long revisionId;
    private Long formRevisionId;
    private Long extensionDefinitionRevisionId;
    private String fieldBindingsJson;
    private Integer version;
}
