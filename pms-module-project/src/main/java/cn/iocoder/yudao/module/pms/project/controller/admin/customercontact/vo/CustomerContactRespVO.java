package cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - PMS 客户联系人 Response VO")
@Data
public class CustomerContactRespVO {

    @Schema(description = "联系人编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long customerId;

    @Schema(description = "姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    private String name;

    @Schema(description = "部门", example = "采购部")
    private String department;

    @Schema(description = "职务", example = "经理")
    private String title;

    @Schema(description = "手机", example = "13800138000")
    private String mobile;

    @Schema(description = "电话", example = "0571-12345678")
    private String phone;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "是否主联系人", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean primaryFlag;

    @Schema(description = "状态：0启用，1停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "备注", example = "重要联系人")
    private String remark;

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
