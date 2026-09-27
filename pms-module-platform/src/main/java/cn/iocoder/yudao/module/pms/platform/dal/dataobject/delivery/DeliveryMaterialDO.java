package cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 交付实际材料记录：关联来源实体与真实文件版本；允许先于要求存在。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plt_delivery_material")
public class DeliveryMaterialDO extends TenantBaseDO {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    public static final String SOURCE_UPLOAD = "UPLOAD";
    public static final String SOURCE_GENERATED = "GENERATED";
    public static final String SOURCE_ASSOCIATED = "ASSOCIATED";

    @TableId
    private Long id;
    private String ownerModule;
    private String entityType;
    private Long entityId;
    private String typeCode;
    private Long fileReferenceId;
    private Long fileArtifactId;
    private Integer fileVersionNo;
    private String fileSha256;
    private String fileName;
    private String title;
    private String sourceKind;
    private String status;
}
