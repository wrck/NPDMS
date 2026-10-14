package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.spare;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("cut_spare_manual_evidence")
@Data
public class CutoverSpareManualEvidenceDO {
    @TableId
    private Long id;
    private Long tenantId;
    @TableField(value = "task_id")
    private Long cutoverTaskId;
    private Long applicationReferenceId;
    private Long fileArtifactId;
    private String fileReferenceKey;
    private Integer fileVersionNo;
    @TableField(exist = false)
    private String fileFactVersion;
    private Long fileScopeVersion;
    private String description;
    @TableField(value = "upload_user_id")
    private Long uploadedBy;
    @TableField(value = "create_time")
    private LocalDateTime createdAt;


    /** 文件工件事实水位；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long fileArtifactFactVersion;
    /** 文件引用事实水位；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long fileReferenceFactVersion;
    /** 文件可用性事实水位；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long fileAvailabilityFactVersion;
}
