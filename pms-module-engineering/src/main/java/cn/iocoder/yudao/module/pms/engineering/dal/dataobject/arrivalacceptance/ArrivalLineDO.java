package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("imp_arrival_line")
@Data
@EqualsAndHashCode(callSuper = true)
public class ArrivalLineDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long arrivalAcceptanceId;
    private Integer lineNo;
    private Integer lineRevision;
    private String scopeType;
    private Long deviceId;
    private Long deviceAssignmentVersion;
    private Long orderLineId;
    private String productCode;
    private String modelCode;
    private BigDecimal expectedQuantity;
    private BigDecimal acceptedQuantity;
    private String unit;
    private String status;
    private Integer currentMarker;
    @Version
    private Integer version;
}
