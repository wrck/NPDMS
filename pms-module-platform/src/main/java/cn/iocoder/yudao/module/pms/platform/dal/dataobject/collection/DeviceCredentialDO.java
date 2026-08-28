package cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("plt_device_credential")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceCredentialDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String credentialCode;
    private String credentialType;
    private String username;
    private String encryptedSecret;
    private String kmsReference;
    private Long credentialVersion;
    private String status;
}
