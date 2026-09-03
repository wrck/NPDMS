package cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("plt_credential_grant")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class CredentialGrantDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long credentialId;
    private String granteeType;
    private String granteeId;
    private String projectId;
    private String deviceId;
    private String protocol;
    private String commandTemplateId;
    private LocalDateTime expiresAt;
    private String status;
}
