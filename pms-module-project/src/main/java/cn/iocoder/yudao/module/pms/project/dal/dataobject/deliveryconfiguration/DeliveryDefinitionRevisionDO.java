package cn.iocoder.yudao.module.pms.project.dal.dataobject.deliveryconfiguration;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** PM-03 / SDS09: same-tenant immutable published definitions. */
@Data @EqualsAndHashCode(callSuper = true)
@TableName("proj_delivery_definition_revision")
public class DeliveryDefinitionRevisionDO extends TenantBaseDO {
    @TableId private Long id;
    private String definitionKind;
    private String definitionCode;
    private Long revisionNo;
    private String revisionState;
    private Integer schemaVersion;
    private String payload;
    private java.time.LocalDateTime publishedAt;
    private java.time.LocalDateTime disabledAt;
    private Integer version;
}
