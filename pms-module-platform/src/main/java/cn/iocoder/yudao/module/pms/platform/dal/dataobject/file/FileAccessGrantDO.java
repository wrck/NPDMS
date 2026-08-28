package cn.iocoder.yudao.module.pms.platform.dal.dataobject.file;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("plt_file_access_grant")
@Data
public class FileAccessGrantDO {

    @TableId
    private Long id;
    private Long artifactId;
    private Integer fileVersionNo;
    private Long subjectUserId;
    private String operationCode;
    private String businessScopeHash;
    private String tokenDigest;
    private String statusCode;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime consumedAt;
    private LocalDateTime revokedAt;
    private Long revokedBy;
    private String revokeReason;
    private Long tenantId;
}
