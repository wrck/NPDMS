package cn.iocoder.yudao.module.pms.platform.dal.dataobject.file;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("plt_file_reference")
@Data
public class FileReferenceDO {

    @TableId
    private Long id;
    private String ownerContext;
    private String objectType;
    private String objectId;
    private String purposeCode;
    private String referenceKey;
    private Long artifactId;
    private Integer fileVersionNo;
    private String sensitivityCode;
    private String statusCode;
    private Long scopeVersion;
    private Integer version;
    private LocalDateTime detachedAt;
    private Long detachedBy;
    private String detachedReason;
    private LocalDateTime archivedAt;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
    private Long tenantId;
}
