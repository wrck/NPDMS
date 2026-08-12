package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 工程交底书 DO（FR-ENG-006）。
 * <p>
 * 对应表 {@code pms_eng_briefing}。
 * 状态：0 草稿、1 已生成、2 已审核、3 已发布、4 已作废。
 */
@TableName("pms_eng_briefing")
@Data
@EqualsAndHashCode(callSuper = true)
public class BriefingDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 交底书编号（如 BR-2026-001），全局唯一
     */
    private String code;
    /**
     * 关联项目ID
     */
    private Long projectId;
    /**
     * 交底书名称
     */
    private String name;
    /**
     * 交底类型：STANDARD 标准 / EMERGENCY 紧急 / CUSTOM 自定义
     */
    private String briefingType;
    /**
     * 关联交底书模板ID
     */
    private Long templateId;
    /**
     * 模板快照JSON（模板版本固定到实例）
     */
    private String templateSnapshot;
    /**
     * 前序基线数据快照JSON（需求/方案/工勘聚合）
     */
    private String sourceSnapshot;
    /**
     * 交底内容富文本
     */
    private String content;
    /**
     * 生成的文件URL
     */
    private String fileUrl;
    /**
     * 文件名
     */
    private String fileName;
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    /**
     * 文件校验值
     */
    private String fileChecksum;
    /**
     * 状态：0 草稿 1 已生成 2 已审核 3 已发布 4 已作废
     */
    private Integer status;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
    /**
     * 生成时间
     */
    private LocalDateTime generateTime;
    /**
     * 发布时间
     */
    private LocalDateTime publishTime;
    /**
     * 审核人
     */
    private Long approverUserId;
    /**
     * 审核意见
     */
    private String approveOpinion;
    /**
     * 审核时间
     */
    private LocalDateTime approveTime;
    /**
     * 编制人
     */
    private Long creatorUserId;
    /**
     * 备注
     */
    private String remark;

}
