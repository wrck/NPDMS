package cn.iocoder.yudao.module.pms.platform.dal.dataobject.evidence;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 统一模型执行证据：中性节点/轮次标识与采纳引用，原生执行标识不进入。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_plat_execution_evidence")
public class PlatformExecutionEvidenceDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String projectStableRef;
    private String planVersion;
    private String nodeKey;
    private Long roundNo;
    private String ruleVersion;
    private String decision;
    private Long subscriptionId;
    private String adoptedFactRefs;
}
