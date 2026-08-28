package cn.iocoder.yudao.module.pms.platform.dal.dataobject.file;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("plt_file_archive_record")
@Data
public class FileArchiveRecordDO {

    @TableId
    private Long id;
    private Long artifactId;
    private Integer fileVersionNo;
    private String archiveBatchId;
    private String businessDecisionRef;
    private Long archivedBy;
    private LocalDateTime archivedAt;
    private String archiveNote;
    private LocalDateTime createdAt;
    private Long tenantId;
}
