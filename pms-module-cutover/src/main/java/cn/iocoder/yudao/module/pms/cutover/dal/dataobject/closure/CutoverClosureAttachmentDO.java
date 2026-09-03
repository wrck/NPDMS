package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("cut_cutover_closure_attachment")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverClosureAttachmentDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long closureId;
    private String purposeCode;
    private String referenceKey;
    private Long artifactId;
    private Integer fileVersionNo;
    private String fileFactVersion;
    private Long fileScopeVersion;
    private String fileHash;
    @Version
    private Integer version;
}
