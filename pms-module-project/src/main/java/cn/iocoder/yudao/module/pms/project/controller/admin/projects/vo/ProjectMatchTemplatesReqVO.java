package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Root creation preview: editable facts only; organization codes and creation defaults are owner-resolved. */
@Data
public class ProjectMatchTemplatesReqVO {
    @NotBlank private String projectName;
    private String customerCode;
    @Positive private Long contractId;
    @Positive private Long salesOrderId;
    @jakarta.validation.constraints.Size(max = 64) private String sourceFingerprint;
    @jakarta.validation.constraints.AssertTrue(message = "合同、销售订单和来源摘要必须一起提交")
    public boolean isCommerceSelectionValid() {
        return contractId == null ? salesOrderId == null && sourceFingerprint == null
                : salesOrderId != null && sourceFingerprint != null && sourceFingerprint.matches("[0-9a-f]{64}");
    }
    private String implementationLocation;
    @NotBlank private String signingMethod;
    @NotBlank private String projectCategory;
    @NotBlank private String implementationMode;
    @NotNull @Positive private Long orderOfficeCompanyId;
    @NotNull @Positive private Long orderOfficeDepartmentId;
}
