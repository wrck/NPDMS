package cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("acc_satisfaction_response_file")
@Data
public class SatisfactionResponseFileDO {
    @TableId private Long id;
    private Long tenantId;
    private Long responseId;
    private String fileRole;
    private Integer fileSequence;
    private Long artifactId;
    private Integer versionNo;
    private String referenceKey;
    private Integer artifactVersion;
    private Integer referenceVersion;
    private Integer availabilityVersion;
    private Long scopeVersion;
    private String fileHash;
    private String creator;
    private LocalDateTime createTime;
}
