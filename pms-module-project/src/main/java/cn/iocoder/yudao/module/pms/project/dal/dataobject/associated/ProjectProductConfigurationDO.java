package cn.iocoder.yudao.module.pms.project.dal.dataobject.associated;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
@TableName("proj_project_product_configuration") @Data @EqualsAndHashCode(callSuper=true)
public class ProjectProductConfigurationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long projectId;
    private String projectCode;
    private String executionNo;
    private Integer itemGroup;
    private String productCode;
    private String parentCode;
    private BigDecimal quantity;
    private String bomPath;
    private String productModel;
    private String productDescription;
    private Integer level;
    private String dataSource;
}
