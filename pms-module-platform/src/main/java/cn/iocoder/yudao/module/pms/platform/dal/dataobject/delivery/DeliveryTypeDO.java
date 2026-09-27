package cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 交付统一类型目录：稳定编码 + 文件约束；必交/选交属于要求，不进入类型编码。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plt_delivery_type")
public class DeliveryTypeDO extends TenantBaseDO {

    public static final String COUNTING_MATERIAL = "MATERIAL";
    public static final String COUNTING_FILE_VERSION = "FILE_VERSION";
    public static final String COUNTING_SUBMISSION = "SUBMISSION";

    @TableId
    private Long id;
    private String typeCode;
    private String name;
    private String category;
    /** JSON 数组，如 ["application/pdf","image/png"]。 */
    private String allowedMediaJson;
    private Long maxSizeBytes;
    private Boolean enabled;
    private String remark;
    @Version
    private Integer version;
}
