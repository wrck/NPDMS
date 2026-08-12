package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 到货签收 DO（FR-ENG-021）。
 * <p>
 * 对应表 {@code pms_eng_arrival}。
 * 状态：0 待签收、1 已签收、2 异常。
 */
@TableName("pms_eng_arrival")
@Data
@EqualsAndHashCode(callSuper = true)
public class ArrivalDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 签收编码，项目内唯一
     */
    private String code;
    /**
     * 到货时间
     */
    private LocalDateTime arrivalTime;
    /**
     * 签收人
     */
    private Long receiverUserId;
    /**
     * 关联设备编号
     */
    private Long equipmentId;
    /**
     * 到货数量
     */
    private Integer quantity;
    /**
     * 外观与清单检查结果
     */
    private String inspectionResult;
    /**
     * 异常记录
     */
    private String exceptionRecord;
    /**
     * 签收单附件
     */
    private String attachmentUrl;
    /**
     * 状态：0 待签收 1 已签收 2 异常
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
}
