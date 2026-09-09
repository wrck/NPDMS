package cn.iocoder.yudao.module.pms.customer.controller.admin.contact;

import cn.iocoder.yudao.module.pms.customer.service.contact.ContactValues;
import cn.iocoder.yudao.module.pms.customer.service.contact.ProjectContactWrite;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ProjectContactSaveReqVO {
    private Long sourceContactId;
    @NotNull @Min(0) private Integer expectedProjectVersion;
    private Integer version;
    @Size(max=64) private String name;
    @Size(max=64) private String department;
    @Size(max=64) private String title;
    @Size(max=32) private String mobile;
    @Size(max=32) private String phone;
    @Size(max=128) private String email;
    @Size(max=64) private String roleCode;
    @Size(max=500) private String remark;
    @NotNull private Boolean primaryFlag;
    @NotNull @Min(0) @Max(1) private Integer status;
    private boolean confirmNoPrimary;

    public ProjectContactWrite command(Long projectId, Long id) {
        ContactValues values = name == null && sourceContactId != null ? null
                : new ContactValues(name, department, title, mobile, phone, email, roleCode, remark);
        return new ProjectContactWrite(projectId, id, sourceContactId, expectedProjectVersion, version,
                values, Boolean.TRUE.equals(primaryFlag), status, confirmNoPrimary);
    }
}
