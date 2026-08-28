package cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FileAvailabilityReqVO extends FileContextReqVO {
    @NotNull @Positive private Integer versionNo;
    @NotNull @PositiveOrZero private Integer expectedAvailabilityVersion;
    @NotBlank @Size(max = 32) private String targetStatus;
    @Size(max = 64) private String reasonCode;
    @Size(max = 512) private String reasonDetail;
}
