package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@TableName("proj_project_real_product")
@Data
@EqualsAndHashCode(callSuper=true)
public class DirectRealProductRow extends TenantBaseDO {
    @TableId private Long id;
    private Long projectId;
    private String contractNo;
    private String projectCode;
    private String executionNo;
    private String productCategoryName;
    private String productName;
    private String productSubCode;
    private String productSubModel;
    private String productSubName;
    private java.math.BigDecimal quantity;
    private String memo;
    private String dataSource;
}
