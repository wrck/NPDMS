package cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 交付提交台账：request_key 幂等；同一要求同时最多一个 CURRENT 提交，新提交取代旧提交。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plt_delivery_submission")
public class DeliverySubmissionDO extends TenantBaseDO {

    public static final String STATUS_CURRENT = "CURRENT";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    @TableId
    private Long id;
    private Long requirementId;
    private String requestKey;
    /** JSON 数组：本次提交的材料ID清单。 */
    private String materialIdsJson;
    private String status;
    /** JSON：提交时冻结的文件证据（材料ID → artifact/version/sha256/available）。 */
    private String submitEvidenceJson;
}
