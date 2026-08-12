package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.announcement;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * PMS 技术公告 DO（FR-ENG-009）。
 * <p>
 * 对应表 {@code pms_eng_announcement}。
 * 状态：0 草稿、1 已发布、2 已停用。
 */
@TableName("pms_eng_announcement")
@Data
@EqualsAndHashCode(callSuper = true)
public class AnnouncementDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 公告编号（如 TA-2026-001），全局唯一
     */
    private String code;
    /**
     * 公告标题
     */
    private String title;
    /**
     * 公告类型：TECH_NOTICE 技术公告 / EOS 停产 / EOM 停维
     */
    private String announcementType;
    /**
     * 适用设备型号
     */
    private String productModel;
    /**
     * 影响版本范围JSON数组
     */
    private String affectedVersions;
    /**
     * 发布日期
     */
    private LocalDate publishDate;
    /**
     * 生效日期
     */
    private LocalDate effectiveDate;
    /**
     * 失效日期
     */
    private LocalDate expireDate;
    /**
     * 严重等级：CRITICAL/HIGH/MEDIUM/LOW
     */
    private String severity;
    /**
     * 公告内容富文本
     */
    private String content;
    /**
     * 处置建议
     */
    private String handlingSuggestion;
    /**
     * 附件URL
     */
    private String fileUrl;
    /**
     * 附件名
     */
    private String fileName;
    /**
     * 附件大小（字节）
     */
    private Long fileSize;
    /**
     * 附件校验值
     */
    private String fileChecksum;
    /**
     * 状态：0 草稿 1 已发布 2 已停用
     */
    private Integer status;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
    /**
     * 创建人
     */
    private Long creatorUserId;
    /**
     * 备注
     */
    private String remark;

}
