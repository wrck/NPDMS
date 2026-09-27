package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

import java.time.LocalDateTime;

/**
 * PMS 硬件安装记录 DO（FR-ENG-022）。
 * <p>
 * 对应表 {@code imp_eng_installation}。
 * 状态：0 待安装、1 进行中、2 已完成、3 异常。
 */
@TableName("imp_eng_installation")
@Data
@EqualsAndHashCode(callSuper = true)
public class InstallationDO extends BaseBusinessEntity {

    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 安装编码，项目内唯一
     */
    private String code;
    /**
     * 设备编号
     */
    private Long equipmentId;
    /**
     * 安装位置
     */
    private String installLocation;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long addressId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long addressVersion;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long siteId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long siteVersion;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long siteLocationId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long siteLocationVersion;
    private String locationResolutionStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String addressSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String locationSnapshot;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    /**
     * 安装时间
     */
    private LocalDateTime installTime;
    /**
     * 安装人
     */
    private Long installerUserId;
    /**
     * 环境检查
     */
    private String environmentCheck;
    /**
     * 安装规范检查
     */
    private String specCheck;
    /**
     * 安装照片
     */
    private String photoUrl;
    /**
     * 安装结果
     */
    private String result;
    /**
     * 状态：0 待安装 1 进行中 2 已完成 3 异常
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;
}
