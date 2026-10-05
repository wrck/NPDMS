package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 电子完工证明设备明细 DO
 * <p>
 * 随完工证明整存整取（保存时按证明编号重建），不单独维护状态。
 */
@TableName("acc_completion_certificate_device")
@Data
@EqualsAndHashCode(callSuper = true)
public class CompletionCertificateDeviceDO extends BaseBusinessEntity {

    /**
     * 完工证明编号
     */
    private Long certificateId;
    /**
     * 设备类型
     */
    private String deviceType;
    /**
     * 设备型号
     */
    private String deviceModel;
    /**
     * 数量
     */
    private Integer quantity;
    /**
     * 展示顺序
     */
    private Integer sort;

}
