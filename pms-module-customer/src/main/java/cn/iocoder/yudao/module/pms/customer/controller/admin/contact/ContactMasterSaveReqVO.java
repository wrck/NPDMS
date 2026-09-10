package cn.iocoder.yudao.module.pms.customer.controller.admin.contact;

import cn.iocoder.yudao.module.pms.customer.service.contact.ContactMasterWrite;
import cn.iocoder.yudao.module.pms.customer.service.contact.ContactValues;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ContactMasterSaveReqVO {
    @NotNull @Positive private Long customerId;
    private Integer version;
    @NotBlank @Size(max=64) private String name;
    @Size(max=64) private String department;
    @Size(max=64) private String title;
    @Size(max=32) private String mobile;
    @Size(max=32) private String phone;
    @Size(max=128) private String email;
    @Size(max=500) private String remark;
    @NotNull private Boolean primaryFlag;
    @NotNull @Min(0) @Max(1) private Integer status;

    public ContactMasterWrite command(Long contactId) {
        return new ContactMasterWrite(customerId, contactId, version,
                new ContactValues(name, department, title, mobile, phone, email, null, remark),
                Boolean.TRUE.equals(primaryFlag), status, false);
    }
}
