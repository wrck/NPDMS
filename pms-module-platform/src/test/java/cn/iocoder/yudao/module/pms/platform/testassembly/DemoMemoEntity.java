package cn.iocoder.yudao.module.pms.platform.testassembly;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

/**
 * 仅测试装配的统一业务实体（演示备忘）：无内容历史能力，验证默认页面不要求历史承载。
 * 不进入生产菜单、字典或业务范围。
 */
@TableName("pms_plat_demo_memo")
public class DemoMemoEntity extends BaseBusinessEntity {

    private String subject;
    private String detail;
    private LocalDate dueDate;
    private Boolean archived;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }
}
