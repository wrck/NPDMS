package cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.business;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data @EqualsAndHashCode(callSuper=true) @TableName("cus_project_customer_contact_relation")
@ProjectBusinessModel(ownerModule="CUS",entityType="projectContact",stableCode="CUS_PROJECT_CONTACT",name="项目联系人",permissionPrefix="pms:project",nativeEntityType="PROJECT_CUSTOMER_CONTACT")
public class ProjectContactBusinessDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name="客户",writable=false,listVisible=false) private Long customerId;
    @BusinessModelField(name="来源联系人",writable=false,listVisible=false) private Long customerContactId;
    @BusinessModelField(name="姓名",displayOrder=10) private String name;
    @BusinessModelField(name="部门",displayOrder=20) private String department;
    @BusinessModelField(name="职务",displayOrder=30) private String title;
    @BusinessModelField(name="手机",displayOrder=40,searchable=false,sortable=false) private String mobile;
    @BusinessModelField(name="电话",displayOrder=50,searchable=false,sortable=false) private String phone;
    @BusinessModelField(name="邮箱",displayOrder=60,searchable=false,sortable=false) private String email;
    @BusinessModelField(name="主联系人",displayOrder=70) private Boolean primaryFlag;
    @BusinessModelField(name="状态",displayOrder=80) private Integer status;
    @BusinessModelField(name="备注",displayOrder=90) private String remark;
    private LocalDateTime primarySetTime;
    private Long deletedBy;
    private LocalDateTime deletedAt;
    @TableField(exist=false) private boolean contactValidated;
}
