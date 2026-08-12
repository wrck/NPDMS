package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.announcementcheck;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 技术公告预检查记录 DO（FR-ENG-009）。
 * <p>
 * 对应表 {@code pms_eng_announcement_check}。
 * 状态：0 待检查、1 已检查、2 已处置、3 已忽略。
 */
@TableName("pms_eng_announcement_check")
@Data
@EqualsAndHashCode(callSuper = true)
public class AnnouncementCheckDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 检查编号（如 PCH-2026-001），全局唯一
     */
    private String code;
    /**
     * 关联项目ID
     */
    private Long projectId;
    /**
     * 关联技术公告ID
     */
    private Long announcementId;
    /**
     * 关联设备ID
     */
    private Long deviceId;
    /**
     * 设备序列号
     */
    private String deviceSerial;
    /**
     * 设备型号
     */
    private String deviceModel;
    /**
     * 设备版本
     */
    private String deviceVersion;
    /**
     * 匹配结果：HIT 命中 / MISS 未命中 / UNKNOWN 未知
     */
    private String matchResult;
    /**
     * EOS/EOM 状态：EOS / EOM / NONE
     */
    private String eomStatus;
    /**
     * 处置建议
     */
    private String handlingSuggestion;
    /**
     * 状态：0 待检查 1 已检查 2 已处置 3 已忽略
     */
    private Integer status;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
    /**
     * 检查人
     */
    private Long checkerUserId;
    /**
     * 检查时间
     */
    private LocalDateTime checkTime;
    /**
     * 处理意见
     */
    private String handleOpinion;
    /**
     * 处理时间
     */
    private LocalDateTime handleTime;
    /**
     * 创建人
     */
    private Long creatorUserId;
    /**
     * 备注
     */
    private String remark;

}
