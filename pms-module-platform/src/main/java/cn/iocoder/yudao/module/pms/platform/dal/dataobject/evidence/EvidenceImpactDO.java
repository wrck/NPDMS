package cn.iocoder.yudao.module.pms.platform.dal.dataobject.evidence;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 执行证据历史影响：原完成证据失效时追加记录，不覆盖原结论。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_plat_evidence_impact")
public class EvidenceImpactDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long evidenceId;
    private String resultId;
    private String impact;
    private String detail;
    private LocalDateTime recordedAt;
}
