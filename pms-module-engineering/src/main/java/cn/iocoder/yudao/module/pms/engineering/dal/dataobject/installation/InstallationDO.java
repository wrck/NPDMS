package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 硬件安装记录 DO（FR-ENG-022）。
 * <p>
 * 对应表 {@code pms_eng_installation}。
 * 状态：0 待安装、1 进行中、2 已完成、3 异常。
 */
@TableName("pms_eng_installation")
@Data
@EqualsAndHashCode(callSuper = true)
public class InstallationDO extends TenantBaseDO {

    @TableId
    private Long id;
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
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
}
