package cn.iocoder.yudao.module.pms.platform.dal.dataobject.file;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("plt_file_upload_session")
@Data
public class FileUploadSessionDO {

    @TableId
    private Long id;
    private String modeCode;
    private String ownerContext;
    private String objectType;
    private String objectId;
    private String purposeCode;
    private String referenceKey;
    private String fileName;
    private String categoryCode;
    private Long declaredSizeBytes;
    private String declaredMediaType;
    private String storageOperationId;
    private String statusCode;
    private Long scopeVersion;
    private LocalDateTime expiresAt;
    private Integer version;
    private Long artifactId;
    private Long referenceId;
    private Integer expectedReferenceVersion;
    private String clientSha256;
    private String actualSha256;
    private Integer completedFileVersionNo;
    private Long registeredInfraFileId;
    private String failureCode;
    private String failureDetail;
    private LocalDateTime completedAt;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
    private Long tenantId;
}
