package cn.iocoder.yudao.module.pms.platform.dal.dataobject.file;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("plt_file_artifact")
@Data
@EqualsAndHashCode(callSuper = true)
public class FileArtifactDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String name;
    private String categoryCode;
    private String ownerContext;
    private String lifecycleStatusCode;
    private String invalidReasonCode;
    private String invalidReasonDetail;
    private LocalDateTime invalidatedAt;
    private Long invalidatedBy;
    private Integer version;
}
