package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

import java.time.LocalDateTime;

/**
 * PMS 配置调试记录 DO（FR-ENG-023）。
 * <p>
 * 对应表 {@code imp_eng_configuration}。
 * 状态：0 待调试、1 进行中、2 已完成、3 异常。
 */
@TableName("imp_eng_configuration")
@Data
@EqualsAndHashCode(callSuper = true)
public class ConfigurationDO extends BaseBusinessEntity {

    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 配置编码，项目内唯一
     */
    private String code;
    /**
     * 设备编号
     */
    private Long equipmentId;
    /**
     * 配置 Log 文件
     */
    private String configLogUrl;
    /**
     * 调试结果
     */
    private String debugResult;
    /**
     * 调试人
     */
    private Long debuggerUserId;
    /**
     * 调试时间
     */
    private LocalDateTime debugTime;
    /**
     * 配置档案快照
     */
    private String configSnapshot;
    /**
     * 状态：0 待调试 1 进行中 2 已完成 3 异常
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;
}
