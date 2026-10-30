package cn.iocoder.yudao.module.pms.platform.testassembly;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 仅测试装配的统一业务实体（演示申领单）：P13 之前从未出现过的实体，
 * 在框架公共实现稳定后仅添加本域声明、持久化与配置即被统一目录、
 * 默认页面、内容历史/扩展字段/交付/审批公共能力与两种执行后端承载。
 * 不进入生产菜单、字典或业务范围。
 */
@TableName("pms_plat_demo_requisition")
public class DemoRequisitionEntity extends BaseBusinessEntity {

    private String title;
    private Integer quantity;
    private String reason;
    private Boolean urgent;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean getUrgent() {
        return urgent;
    }

    public void setUrgent(Boolean urgent) {
        this.urgent = urgent;
    }
}
