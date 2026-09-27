package cn.iocoder.yudao.module.pms.platform.dal.dataobject.result;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 统一业务结果登记：形成依据真实可追溯，重复形成按唯一键幂等。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_plat_business_result")
public class PlatformBusinessResultDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String resultType;
    private String ownerModule;
    private String entityType;
    private Long entityId;
    private String resultId;
    private String semantics;
    private String formationBasis;
    private String formedByBackend;
    private LocalDateTime formedAt;
    private Boolean valid;
}
