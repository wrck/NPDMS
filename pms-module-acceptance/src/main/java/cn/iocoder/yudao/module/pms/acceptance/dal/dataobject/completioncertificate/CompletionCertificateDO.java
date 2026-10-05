package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 电子完工证明 DO
 * <p>
 * 状态机：0草稿 → 1待客户确认 → 2客户已确认 → 3已归档 / 4已驳回
 * 【待确认：法律效力口径】电子完工证明的法律效力以公司法务口径为准，本实现仅承载流程数据。
 */
@TableName("acc_completion_certificate")
@Data
@EqualsAndHashCode(callSuper = true)
public class CompletionCertificateDO extends BaseBusinessEntity {

    /**
     * 主键编号
     */
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 完工证明编码，项目内唯一
     */
    private String code;
    /**
     * 完工证明名称
     */
    private String name;
    /**
     * 证明编号（业务编号）
     */
    private String certificateNo;
    /**
     * 客户编号
     */
    private Long customerId;
    /**
     * 完工日期
     */
    private LocalDate completionDate;
    /**
     * 客户确认人。客户确认是客户单位的外部人员（非系统用户），不落系统 user_id，保持 NULL。
     */
    private Long customerConfirmUserId;
    /**
     * 客户确认时间
     */
    private LocalDateTime customerConfirmTime;
    /**
     * 归档时间
     */
    private LocalDateTime archiveTime;
    /**
     * 驳回原因
     */
    private String rejectReason;
    /**
     * 完工证明内容
     */
    private String content;
    /**
     * 附件地址
     */
    private String attachmentUrl;
    /**
     * 工程服务类型
     */
    private String serviceType;
    /**
     * 迪普工程师用户编号
     */
    private Long engineerUserId;
    /**
     * 迪普工程师姓名（快照）
     */
    private String engineerName;
    /**
     * 工程师联系方式
     */
    private String engineerContact;
    /**
     * 客户单位
     */
    private String customerUnit;
    /**
     * 合同号
     */
    private String contractNo;
    /**
     * 完成到货验收：是/否/不涉及
     */
    private String itemArrival;
    /**
     * 完成安装调试：是/否/不涉及
     */
    private String itemInstall;
    /**
     * 完成割接：是/否/不涉及
     */
    private String itemCutover;
    /**
     * 完成培训：是/否/不涉及
     */
    private String itemTraining;
    /**
     * 完成文档交付：是/否/不涉及
     */
    private String itemDocs;
    /**
     * 甲方签章图片地址
     */
    private String customerSignUrl;
    /**
     * 甲方签章日期
     */
    private LocalDate customerSignDate;
    /**
     * 服务方签章图片地址
     */
    private String vendorSignUrl;
    /**
     * 服务方签章日期
     */
    private LocalDate vendorSignDate;
    /**
     * 状态 0草稿 1待客户确认 2客户已确认 3已归档 4已驳回
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;

}
