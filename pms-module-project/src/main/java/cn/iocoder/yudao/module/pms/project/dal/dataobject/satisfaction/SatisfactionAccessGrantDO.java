package cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("acc_satisfaction_access_grant")
@Data
public class SatisfactionAccessGrantDO {
    @TableId private Long id;
    private Long tenantId;
    private Long questionnaireId;
    private Integer grantVersion;
    private String tokenDigest;
    private LocalDateTime effectiveFrom;
    private LocalDateTime expiresAt;
    private String grantStatus;
    private LocalDateTime consumedAt;
    private Integer version;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
}
