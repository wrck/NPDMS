package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
@TableName("imp_configuration_collection")
@Data @EqualsAndHashCode(callSuper = true)
public class ConfigurationCollectionDO extends TenantBaseDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.ASSIGN_ID) private Long id;
    private Long configurationId;
    private Long equipmentId;
    private Long actorId;
    private String requestKey;
    private String requestDigest;
    private String platformTaskId;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String commandText;
    private Long consumedResultVersion;
}
