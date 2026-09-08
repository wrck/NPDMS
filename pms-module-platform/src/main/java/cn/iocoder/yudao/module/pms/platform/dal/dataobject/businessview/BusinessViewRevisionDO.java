package cn.iocoder.yudao.module.pms.platform.dal.dataobject.businessview;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** PM-03: one physical revision; version is row CAS, not the domain aggregate version. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plt_business_view_revision")
public class BusinessViewRevisionDO extends TenantBaseDO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String entityType;
    private String viewKey;
    private Long revisionNo;
    private String ownerContext;
    private String viewSource;
    private Long dynamicFormRevisionId;
    private String componentKey;
    private String componentVersion;
    private String contextSchema;
    private String supportedActions;
    private String queryProviderKey;
    private String commandProviderKey;
    private String permissionProviderKey;
    private LocalDateTime publishedAt;
    private LocalDateTime disabledAt;
    private Integer version;
}
