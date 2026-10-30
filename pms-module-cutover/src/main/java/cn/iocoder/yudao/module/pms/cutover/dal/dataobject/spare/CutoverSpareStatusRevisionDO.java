package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.spare;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("cut_spare_status_revision")
@Data
public class CutoverSpareStatusRevisionDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Long applicationReferenceId;
    private Long statusVersion;
    private String externalStatusRaw;
    @TableField(exist = false)
    private String statusSnapshot;
    @TableField(value = "source_type_code")
    private String sourceType;
    @TableField(value = "observe_time")
    private LocalDateTime observedAt;
    @TableField(value = "external_occur_time")
    private LocalDateTime externalOccurredAt;
    private String eventId;
    private String correlationId;
    private Integer currentMarker;
    @TableField(value = "create_user_id")
    private Long createdBy;
    @TableField(value = "create_time")
    private LocalDateTime createdAt;


    /** 规范化接收载荷摘要，仅保持幂等冲突检测，不替代业务字段；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String sourcePayloadDigest;
}
