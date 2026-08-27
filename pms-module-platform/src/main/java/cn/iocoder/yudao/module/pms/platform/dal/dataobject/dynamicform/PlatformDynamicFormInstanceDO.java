package cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("plt_dynamic_form_instance")
@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformDynamicFormInstanceDO extends TenantBaseDO {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String instanceCode;
    private String instanceName;
    private String ownerContext;
    private String objectType;
    private String objectId;
    private Long templateId;
    private Long templateRevisionId;
    private Integer templateRevisionNo;
    private String engineCode;
    private String designerVersion;
    private String rendererVersion;
    private String valueJson;
    private Long createdBy;
    private Integer version;

    @TableField(exist = false)
    private String templateCode;
    @TableField(exist = false)
    private String templateName;
}
