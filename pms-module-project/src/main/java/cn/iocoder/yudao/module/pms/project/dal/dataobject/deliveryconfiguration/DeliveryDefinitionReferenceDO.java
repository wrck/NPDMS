package cn.iocoder.yudao.module.pms.project.dal.dataobject.deliveryconfiguration;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** PM-03 / SDS09: same-tenant immutable published definitions. */
@Data @EqualsAndHashCode(callSuper = true)
@TableName("proj_delivery_definition_reference")
public class DeliveryDefinitionReferenceDO extends TenantBaseDO {
    @TableId private Long id;
    private Long ownerRevisionId;
    private String referenceKey;
    private Long targetRevisionId;
}
