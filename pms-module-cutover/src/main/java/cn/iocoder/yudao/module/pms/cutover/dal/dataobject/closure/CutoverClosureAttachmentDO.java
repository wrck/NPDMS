package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("cut_cutover_closure_attachment")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverClosureAttachmentDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long closureId;
    private String purposeCode;
    @TableField(value = "file_reference_key")
    private String referenceKey;
    @TableField(value = "file_artifact_id")
    private Long artifactId;
    private Integer fileVersionNo;
    @TableField(exist = false)
    private String fileFactVersion;
    private Long fileScopeVersion;
    @TableField(value = "file_sha256")
    private String fileHash;
    @Version
    private Integer version;


    /** 文件工件事实水位；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long fileArtifactFactVersion;
    /** 文件引用事实水位；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long fileReferenceFactVersion;
    /** 文件可用性事实水位；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long fileAvailabilityFactVersion;
}
