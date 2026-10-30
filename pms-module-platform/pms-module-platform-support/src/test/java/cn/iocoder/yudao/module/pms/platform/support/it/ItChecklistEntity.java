package cn.iocoder.yudao.module.pms.platform.support.it;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

/**
 * 集成测试聚合根样例：结构 B（清单），作为订单关系 checklists 的目标实体。
 */
@TableName("pms_plat_it_checklist")
public class ItChecklistEntity extends BaseBusinessEntity {

    private String title;

    private Boolean done;

    private Long orderId;

    private LocalDate dueDate;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getDone() {
        return done;
    }

    public void setDone(Boolean done) {
        this.done = done;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
}
