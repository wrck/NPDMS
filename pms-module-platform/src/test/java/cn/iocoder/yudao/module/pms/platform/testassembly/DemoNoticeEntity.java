package cn.iocoder.yudao.module.pms.platform.testassembly;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

/**
 * 仅测试装配的统一业务实体（演示公告）：不进入生产菜单、字典或业务范围。
 * 用于真实浏览器验证默认列表/表单/操作栏与独立办理闭环。
 */
@TableName("pms_plat_demo_notice")
public class DemoNoticeEntity extends BaseBusinessEntity {

    private String title;
    private String content;
    private Integer level;
    private Boolean pinned;
    private LocalDate publishedAt;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Boolean getPinned() {
        return pinned;
    }

    public void setPinned(Boolean pinned) {
        this.pinned = pinned;
    }

    public LocalDate getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDate publishedAt) {
        this.publishedAt = publishedAt;
    }
}
