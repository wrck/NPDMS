package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.archivedocument;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import jakarta.validation.constraints.*;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 交付资料归档 DO
 * <p>
 * 状态机：0草稿 → 1待归档 → 2已归档
 * 归档后版本不可覆盖：已归档（status=2）的文档不允许更新
 */
@TableName("acc_archive_document")
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchiveDocumentDO extends BaseProjectBusinessEntity {

    /**
     * 主键编号
     */
    /**
     * 所属项目编号
     */
    /**
     * 归档文档编码，项目内唯一
     */
    @BusinessModelField(name="文档编码", writable=false)
    @NotBlank
    @Size(max=128)
    private String code;
    /**
     * 归档文档名称
     */
    @BusinessModelField(name="文档名称")
    @NotBlank
    @Size(max=128)
    private String name;
    /**
     * 文档类型 ACCEPTANCE 验收 / BUSINESS 业务 / TECHNICAL 技术 / FINANCE 财务 / OTHER 其他
     */
    @BusinessModelField(name="文档类型")
    @Size(max=32)
    private String documentType;
    /**
     * 文档附件地址
     */
    @BusinessModelField(name="文档地址")
    @Size(max=500)
    private String documentUrl;
    /**
     * 文档版本号
     */
    @BusinessModelField(name="版本")
    @Size(max=32)
    private String versionNo;
    /**
     * 归档人
     */
    @BusinessModelField(name="归档人", writable=false)
    private Long archiveUserId;
    /**
     * 归档时间
     */
    @BusinessModelField(name="归档时间", writable=false)
    private LocalDateTime archiveTime;
    /**
     * 状态 0草稿 1待归档 2已归档
     */
    @BusinessModelField(name="状态", writable=false)
    private Integer status;
    /**
     * 备注
     */
    @BusinessModelField(name="备注")
    @Size(max=500)
    private String remark;
    /**
     * 乐观锁版本号
     */

}
