package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.business;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.ProjectBusinessModel;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** Default business contract over the existing imp_eng_installation table. */
@TableName("imp_eng_installation")
@Data
@EqualsAndHashCode(callSuper = true)
@ProjectBusinessModel(ownerModule = "IMP", entityType = "installation", stableCode = "IMP_INSTALLATION",
        name = "硬件安装", permissionPrefix = "pms:imp-installation", nativeEntityType = "INSTALLATION")
public class InstallationBusinessDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name = "安装编码", displayOrder = 10, writable = false)
    private String code;
    @BusinessModelField(name = "关联设备", displayOrder = 20)
    @jakarta.validation.constraints.NotNull
    private Long equipmentId;
    @BusinessModelField(name = "安装位置", displayOrder = 30)
    private String installLocation;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long addressId;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long addressVersion;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long siteId;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long siteVersion;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long siteLocationId;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long siteLocationVersion;
    @BusinessModelField(name = "位置解析状态", displayOrder = 100, writable = false)
    private String locationResolutionStatus;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String addressSnapshot;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String locationSnapshot;
    @BusinessModelField(name = "生效时间", displayOrder = 130, writable = false)
    private LocalDateTime effectiveFrom;
    @BusinessModelField(name = "失效时间", displayOrder = 140, writable = false)
    private LocalDateTime effectiveTo;
    @BusinessModelField(name = "安装时间", displayOrder = 150)
    private LocalDateTime installTime;
    @BusinessModelField(name = "安装人", displayOrder = 160)
    private Long installerUserId;
    @BusinessModelField(name = "环境检查", displayOrder = 170)
    private String environmentCheck;
    @BusinessModelField(name = "安装规范检查", displayOrder = 180)
    private String specCheck;
    @BusinessModelField(name = "安装照片", displayOrder = 190)
    private String photoUrl;
    @BusinessModelField(name = "安装结果", displayOrder = 200)
    private String result;
    @BusinessModelField(name = "状态", displayOrder = 210, writable = false)
    private Integer status;
    @BusinessModelField(name = "备注", displayOrder = 220)
    private String remark;
}

