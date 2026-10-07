package cn.iocoder.yudao.module.pms.platform.dal.dataobject.businessconfiguration;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("plt_business_field_configuration")
public class BusinessFieldConfigurationDO extends TenantBaseDO {
    @TableId private Long id;
    private String ownerModule;
    private String entityType;
    private String fieldsJson;
    private Long version;
}
