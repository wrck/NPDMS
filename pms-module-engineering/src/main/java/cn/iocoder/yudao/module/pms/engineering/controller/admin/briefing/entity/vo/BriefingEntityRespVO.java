package cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.entity.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 独立交底响应，保留旧响应的全部字段并增加只读来源标识。 */
@Data
public class BriefingEntityRespVO {
    /** 主键。 */
    private Long id;
    /** 交底书编号；租户内唯一，创建后不可变。 */
    private String code;
    /** 关联项目 ID。 */
    private Long projectId;
    /** 交底书名称。 */
    private String name;
    /** STANDARD 标准 / EMERGENCY 紧急 / CUSTOM 自定义。 */
    private String briefingType;
    /** 关联模板 ID。 */
    private Long templateId;
    /** 固定的模板快照 JSON。 */
    private String templateSnapshot;
    /** 前序基线数据快照 JSON。 */
    private String sourceSnapshot;
    /** 交底内容富文本。 */
    private String content;
    /** 文件 URL。 */
    private String fileUrl;
    /** 文件名。 */
    private String fileName;
    /** 文件大小（字节）。 */
    private Long fileSize;
    /** 文件校验值。 */
    private String fileChecksum;
    /** 0 草稿 / 1 已生成 / 2 已审核 / 3 已发布 / 4 已作废。 */
    private Integer status;
    /** 乐观锁版本号。 */
    private Integer version;
    /** 生成时间。 */
    private LocalDateTime generateTime;
    /** 发布时间。 */
    private LocalDateTime publishTime;
    /** 审核人。 */
    private Long approverUserId;
    /** 审核意见。 */
    private String approveOpinion;
    /** 审核时间。 */
    private LocalDateTime approveTime;
    /** 编制人。 */
    private Long creatorUserId;
    /** 备注。 */
    private String remark;
    private LocalDateTime createTime;
    private Long legacySourceId;
}
