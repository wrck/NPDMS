package cn.iocoder.yudao.module.pms.project.dal.dataobject.associated;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
@TableName("proj_project_lease_product") @Data @EqualsAndHashCode(callSuper=true)
public class ProjectLeaseProductDO extends TenantBaseDO {
    @TableId private Long id;
    private Long projectId;
    private String projectCode;
    private String executionNo;
    private String productCategory;
    private String productType;
    private String productCode;
    private String productModel;
    private String productName;
    private BigDecimal quantity;
    private String memo;
    private BigDecimal leaseDuration;
    private String dataSource;
}
