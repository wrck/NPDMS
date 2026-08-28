package cn.iocoder.yudao.module.pms.platform.dal.dataobject.file;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("plt_file_version")
@Data
public class FileVersionDO {

    @TableId
    private Long id;
    private Long artifactId;
    private Integer versionNo;
    private Long infraFileId;
    private Integer availabilityVersion;
    private String sha256;
    private Long sizeBytes;
    private String declaredMediaType;
    private String detectedMediaType;
    private String scanStatusCode;
    private String scanProviderCode;
    private String scanProviderVersion;
    private String availabilityStatusCode;
    private String unavailableReasonCode;
    private LocalDateTime unavailableAt;
    private String versionNote;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long tenantId;
}
