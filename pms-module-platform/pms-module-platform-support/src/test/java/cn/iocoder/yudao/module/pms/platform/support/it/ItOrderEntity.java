package cn.iocoder.yudao.module.pms.platform.support.it;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 集成测试聚合根样例：结构 A（单表订单），与结构 B（清单）字段完全不同。
 */
@TableName("pms_plat_it_order")
public class ItOrderEntity extends BaseBusinessEntity {

    private String orderNo;

    private BigDecimal amount;

    @TableField("order_status")
    private String status;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
