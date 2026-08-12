package cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - PMS 客户联系人创建/修改 Request VO")
@Data
public class CustomerContactSaveReqVO {

    @Schema(description = "联系人编号", example = "1024")
    private Long id;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "客户编号不能为空")
    private Long customerId;

    @Schema(description = "姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过 64 个字符")
    private String name;

    @Schema(description = "部门", example = "采购部")
    @Size(max = 64, message = "部门长度不能超过 64 个字符")
    private String department;

    @Schema(description = "职务", example = "经理")
    @Size(max = 64, message = "职务长度不能超过 64 个字符")
    private String title;

    @Schema(description = "手机", example = "13800138000")
    @Size(max = 32, message = "手机长度不能超过 32 个字符")
    private String mobile;

    @Schema(description = "电话", example = "0571-12345678")
    @Size(max = 32, message = "电话长度不能超过 32 个字符")
    private String phone;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;

    @Schema(description = "是否主联系人", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    @NotNull(message = "是否主联系人不能为空")
    private Boolean primaryFlag;

    @Schema(description = "状态：0启用，1停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "备注", example = "重要联系人")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

}
