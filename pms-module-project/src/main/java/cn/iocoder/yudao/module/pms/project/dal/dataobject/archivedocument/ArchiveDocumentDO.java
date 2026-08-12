package cn.iocoder.yudao.module.pms.project.dal.dataobject.archivedocument;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 交付资料归档 DO
 * <p>
 * 状态机：0草稿 → 1待归档 → 2已归档
 * 归档后版本不可覆盖：已归档（status=2）的文档不允许更新
 */
@TableName("pms_acc_archive_document")
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchiveDocumentDO extends TenantBaseDO {

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
     * 归档文档编码，项目内唯一
     */
    private String code;
    /**
     * 归档文档名称
     */
    private String name;
    /**
     * 文档类型 ACCEPTANCE 验收 / BUSINESS 业务 / TECHNICAL 技术 / FINANCE 财务 / OTHER 其他
     */
    private String documentType;
    /**
     * 文档附件地址
     */
    private String documentUrl;
    /**
     * 文档版本号
     */
    private String versionNo;
    /**
     * 归档人
     */
    private Long archiveUserId;
    /**
     * 归档时间
     */
    private LocalDateTime archiveTime;
    /**
     * 状态 0草稿 1待归档 2已归档
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
