package cn.iocoder.yudao.module.pms.customer.dal.dataobject.classification;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cus_market_relation")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerMarketRelationDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String marketCode;
    private String marketName;
    private String systemCode;
    private String systemName;
    private String expendCode;
    private String expendName;
    private String industryCode;
    private String industryName;
    private String mappingStatus;
    private String sourceVersion;
    private LocalDateTime dataAsOf;
}
