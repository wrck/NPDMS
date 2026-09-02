package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 统一映射COM-A既有订单合同关系表，并补充COM-B来源CAS身份。 */
@TableName("com_order_contract_relation")
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderContractRelationDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private Long contractId;
    private String relationRole;
    private String relationSource;
    private String sourceSystem;
    private String salesOrderSourceKey;
    private String contractSourceKey;
    private String sourceVersion;
    private String sourceEvidence;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;

    public Long getSalesOrderId() {
        return orderId;
    }

    public void setSalesOrderId(Long salesOrderId) {
        this.orderId = salesOrderId;
    }

    public String getRelationStatus() {
        return effectiveTo == null ? "ACTIVE" : "ENDED";
    }

    public void setRelationStatus(String relationStatus) {
        // 状态由生效区间确定；保留方法供统一接入服务表达同一语义。
    }
}
