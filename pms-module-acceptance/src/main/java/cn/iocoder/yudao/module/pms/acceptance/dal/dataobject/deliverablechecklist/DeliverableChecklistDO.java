package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.deliverablechecklist;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import jakarta.validation.constraints.*;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 交付件完整性检查 DO
 * <p>
 * 状态机：0草稿 → 1已提交 → 2已通过 / 3已驳回
 * 交付件类型：REQUIRED 必交 / OPTIONAL 选交 / CONDITIONAL 条件
 * 用途：FR-ACC-005 验收通过前的交付件完整性门禁数据源
 */
@TableName("acc_deliverable_checklist")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliverableChecklistDO extends BaseProjectBusinessEntity {

    /**
     * 主键编号
     */
    /**
     * 所属项目编号
     */
    /**
     * 交付件编码，项目内唯一
     */
    @BusinessModelField(name="清单编码", writable=false)
    @NotBlank
    @Size(max=128)
    private String code;
    /**
     * 交付件名称
     */
    @BusinessModelField(name="清单名称")
    @NotBlank
    @Size(max=128)
    private String name;
    /**
     * 关联验收编号
     */
    @BusinessModelField(name="验收记录")
    private Long acceptanceId;
    /**
     * 交付件类型 REQUIRED 必交 / OPTIONAL 选交 / CONDITIONAL 条件
     */
    @BusinessModelField(name="交付件类型")
    @Size(max=32)
    private String deliverableType;
    /**
     * 交付件附件地址
     */
    @BusinessModelField(name="交付件地址")
    @Size(max=500)
    private String deliverableUrl;
    /**
     * 检查人
     */
    @BusinessModelField(name="核对人", writable=false)
    private Long checkUserId;
    /**
     * 检查时间
     */
    @BusinessModelField(name="核对时间", writable=false)
    private LocalDateTime checkTime;
    /**
     * 检查结果
     */
    @BusinessModelField(name="核对结果")
    private String checkResult;
    /**
     * 状态 0草稿 1已提交 2已通过 3已驳回
     */
    @BusinessModelField(name="状态", writable=false)
    private Integer status;
    /**
     * 备注
     */
    @BusinessModelField(name="备注")
    @Size(max=500)
    private String remark;
    /**
     * 乐观锁版本号
     */

}
