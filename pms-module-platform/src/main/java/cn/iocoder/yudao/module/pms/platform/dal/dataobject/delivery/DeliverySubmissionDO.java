package cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
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

    /** 提交来源语义（对齐 DeliveryMaterialSource 与模板自动投影）。 */
    public static final String SOURCE_UPLOAD = PlatformDeliveryRequirementApi.SOURCE_UPLOAD;
    public static final String SOURCE_BUSINESS_RESULT = PlatformDeliveryRequirementApi.SOURCE_BUSINESS_RESULT;
    public static final String SOURCE_BUSINESS_DOCUMENT = PlatformDeliveryRequirementApi.SOURCE_BUSINESS_DOCUMENT;
    public static final String SOURCE_AUTO_PROJECTION = PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION;

    @TableId
    private Long id;
    private Long requirementId;
    private String requestKey;
    /** JSON 数组：本次提交的材料ID清单。 */
    private String materialIdsJson;
    private String sourceType;
    private Long projectId;
    /** 请求快照：幂等重放时与原请求比对，同一键不同载荷拒绝。 */
    private String requestPayloadJson;
    private String status;
    /** JSON：提交时冻结的文件证据（材料ID → artifact/version/sha256/available）。 */
    private String submitEvidenceJson;
    /** 判定证据快照：提交/归集后冻结的满足判定（满足/原因/证据）。 */
    private String decisionEvidenceJson;
    /** Archive obligation belongs to this immutable source submission, not the shared material. */
    private String archiveStatus;
    private String archiveFailureCode;
    private Integer archiveRetryCount;

}
