package cn.iocoder.yudao.module.pms.project.dal.dataobject.completioncertificate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
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
@TableName("pms_acc_completion_certificate")
@Data
@EqualsAndHashCode(callSuper = true)
public class CompletionCertificateDO extends TenantBaseDO {

    /**
     * 主键编号
     */
    @TableId
    private Long id;
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
     * 客户确认人
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
     * 状态 0草稿 1待客户确认 2客户已确认 3已归档 4已驳回
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

}
