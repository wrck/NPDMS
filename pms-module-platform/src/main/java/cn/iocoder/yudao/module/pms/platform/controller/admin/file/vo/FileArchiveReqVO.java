package cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FileArchiveReqVO extends FileContextReqVO {
    @NotBlank @Size(max = 128) private String archiveBatchId;
    @NotBlank @Size(max = 128) private String businessDecisionRef;
    @Size(max = 512) private String archiveNote;
}
