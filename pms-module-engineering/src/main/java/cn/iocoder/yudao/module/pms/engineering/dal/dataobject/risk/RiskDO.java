package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.risk;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

import java.time.LocalDateTime;

/**
 * PMS 单机风险 DO（FR-ENG-008）。
 * <p>
 * 对应表 {@code imp_eng_risk}。
 * 状态：0 草稿、1 已识别、2 已确认、3 已同步CRM、4 已关闭。
 */
@TableName("imp_eng_risk")
@Data
@EqualsAndHashCode(callSuper = true)
public class RiskDO extends BaseBusinessEntity {

    /**
     * 风险编号（如 RK-2026-001），全局唯一
     */
    private String code;
    /**
     * 关联项目ID
     */
    private Long projectId;
    /**
     * 风险名称
     */
    private String name;
    /**
     * 风险类型：SINGLE_DEVICE 单机 / SCENARIO 场景
     */
    private String riskType;
    /**
     * 关联设备ID
     */
    private Long deviceId;
    /**
     * 设备序列号
     */
    private String deviceSerial;
    /**
     * 设备型号
     */
    private String deviceModel;
    /**
     * 风险场景描述
     */
    private String scenario;
    /**
     * 风险等级：HIGH 高 / MEDIUM 中 / LOW 低
     */
    private String riskLevel;
    /**
     * 状态：0 草稿 1 已识别 2 已确认 3 已同步CRM 4 已关闭
     */
    private Integer status;
    /**
     * 是否已同步CRM
     */
    private Boolean crmSynced;
    /**
     * CRM同步时间
     */
    private LocalDateTime crmSyncTime;
    /**
     * 处理人
     */
    private Long handlerUserId;
    /**
     * 处理意见
     */
    private String handleOpinion;
    /**
     * 处理时间
     */
    private LocalDateTime handleTime;
    /**
     * 创建人
     */
    private Long creatorUserId;
    /**
     * 备注
     */
    private String remark;

}
