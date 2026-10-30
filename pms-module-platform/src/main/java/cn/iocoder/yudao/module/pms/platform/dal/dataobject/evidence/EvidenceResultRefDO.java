package cn.iocoder.yudao.module.pms.platform.dal.dataobject.evidence;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 执行证据采纳结果引用：结果失效影响按此回查受影响证据。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_plat_evidence_result_ref")
public class EvidenceResultRefDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long evidenceId;
    private String resultId;
}
