package cn.iocoder.yudao.module.infra.dal.dataobject.file;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("infra_file_version")
@Data
@EqualsAndHashCode(callSuper = true)
public class FileVersionDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long artifactId;
    private Long configId;
    private String contentSha256;
    private Long size;
    private String contentType;
    private String storageKey;
    private String url;
    private String scanStatus;
}