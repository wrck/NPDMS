package cn.iocoder.yudao.module.pms.platform.dal.dataobject.authorization;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("plt_authorization_grant")
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthorizationGrantDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String subjectTypeCode;
    private Long subjectId;
    private String resourceContextCode;
    private String resourceTypeCode;
    private Long resourceId;
    private String actionCode;
    private String scopeCode;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String statusCode;
    private String sourceContextCode;
    private String sourceObjectType;
    private String sourceObjectId;
    private Long grantedBy;
    private LocalDateTime grantedAt;
    private Long revokedBy;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private Integer version;
    private Integer currentMarker;
}
