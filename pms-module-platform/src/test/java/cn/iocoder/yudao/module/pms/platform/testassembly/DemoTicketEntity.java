package cn.iocoder.yudao.module.pms.platform.testassembly;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 仅测试装配的统一业务实体（演示工单）：P05 之前从未出现过的实体，
 * 验证仅添加本域声明与配置即可被统一目录、默认执行与两种执行后端承载。
 * 不进入生产菜单、字典或业务范围。
 */
@TableName("pms_plat_demo_ticket")
public class DemoTicketEntity extends BaseBusinessEntity {

    private String summary;
    private String detail;
    private Integer priority;
    private Boolean handled;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getHandled() {
        return handled;
    }

    public void setHandled(Boolean handled) {
        this.handled = handled;
    }
}
