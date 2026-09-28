package cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 交付实际材料记录：关联来源实体与真实文件版本；允许先于要求存在。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plt_delivery_material")
public class DeliveryMaterialDO extends TenantBaseDO {

    public static final String STATUS_ACTIVE = PlatformDeliveryRequirementApi.MATERIAL_STATUS_ACTIVE;
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    public static final String SOURCE_UPLOAD = PlatformDeliveryRequirementApi.MATERIAL_SOURCE_UPLOAD;
    public static final String SOURCE_GENERATED = PlatformDeliveryRequirementApi.MATERIAL_SOURCE_GENERATED;
    public static final String SOURCE_ASSOCIATED = PlatformDeliveryRequirementApi.MATERIAL_SOURCE_ASSOCIATED;

    /** 归档补偿状态（acc source_version archive_* 语义随迁到材料行）。 */
    public static final String ARCHIVE_NOT_REQUIRED = PlatformDeliveryRequirementApi.ARCHIVE_NOT_REQUIRED;
    public static final String ARCHIVE_PENDING_COMPENSATION = PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION;
    public static final String ARCHIVE_ARCHIVED = PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED;
    public static final String ARCHIVE_INVALID = PlatformDeliveryRequirementApi.ARCHIVE_INVALID;

    /** 材料证据锚：FILE=文件版本锚定；BUSINESS_RESULT=业务成果锚定（文件列为空）。 */
    public static final String KIND_FILE = PlatformDeliveryRequirementApi.MATERIAL_KIND_FILE;
    public static final String KIND_BUSINESS_RESULT = PlatformDeliveryRequirementApi.MATERIAL_KIND_BUSINESS_RESULT;

    @TableId
    private Long id;
    private String ownerModule;
    private String entityType;
    private Long entityId;
    private String typeCode;
    private String materialKind;
    /** 绑定的模板冻结要求（TEMPLATE_FROZEN 要求的材料直连）；CATALOG 场景为空。 */
    private Long requirementId;
    /** 项目上下文：供项目级汇总与门禁定位，不改变 owner 三元组语义。 */
    private Long projectId;
    private Long fileReferenceId;
    private Long fileArtifactId;
    private Integer fileVersionNo;
    private String fileSha256;
    private String fileName;
    /** 业务成果对象类型（统一目录 entityType）；仅 BUSINESS_RESULT 行有效。 */
    private String businessObjectType;
    private String businessObjectId;
    /** 业务成果修订锚（如方案基线版本）；无修订对象为空。 */
    private Long businessRevisionNo;
    private String title;
    private String sourceKind;
    private String status;
    /** 归档补偿事实：NOT_REQUIRED/PENDING_COMPENSATION/ARCHIVED/INVALID。 */
    private String archiveStatus;
    private String archiveFailureCode;
    private Integer archiveRetryCount;
    private java.time.LocalDateTime archiveTime;
}
