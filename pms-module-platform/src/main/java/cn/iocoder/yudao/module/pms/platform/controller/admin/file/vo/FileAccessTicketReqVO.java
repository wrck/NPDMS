package cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FileAccessTicketReqVO {

    @NotNull @Positive
    private Integer versionNo;
    @NotBlank @Size(max = 32)
    private String operationCode;
    @NotBlank @Size(max = 32)
    private String ownerContext;
    @NotBlank @Size(max = 64)
    private String objectType;
    @NotBlank @Size(max = 128)
    private String objectId;
    @NotBlank @Size(max = 64)
    private String purposeCode;
    @NotBlank @Size(max = 128)
    private String referenceKey;
}
