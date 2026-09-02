package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.spare;

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
    private Long cutoverTaskId;
    private Long applicationReferenceId;
    private Long fileArtifactId;
    private String fileReferenceKey;
    private Integer fileVersionNo;
    private String fileFactVersion;
    private Long fileScopeVersion;
    private String description;
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
