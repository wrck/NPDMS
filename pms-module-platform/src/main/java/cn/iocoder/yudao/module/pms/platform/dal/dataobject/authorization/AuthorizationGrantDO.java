package cn.iocoder.yudao.module.pms.platform.dal.dataobject.authorization;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("plt_authorization_grant")
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthorizationGrantDO extends BaseBusinessEntity {

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
    private Integer currentMarker;
}
