package cn.iocoder.yudao.module.pms.asset.dal.dataobject.version;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A source business fact revision, separate from the device's current network version. */
@TableName("ast_device_software_observation")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceSoftwareObservationDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long projectId;
    private String sourceProjectKey;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private LocalDateTime sourceReadAt;
    private String sourceChecksum;
    private Long sourceId;
    private Long sourceProjectId;
    private Long logId;
    private String contractNo;
    private String itemCode;
    private String barCode;
    private String conp;
    private String conpType;
    private String conpSeries;
    private String conpMark;
    private String conpBak;
    private Integer conpChange;
    private String cpld;
    private String cpldBak;
    private Integer cpldChange;
    private String boot;
    private String bootBak;
    private Integer bootChange;
    private String pcb;
    private String pcbBak;
    private Integer pcbChange;
    private LocalDate executeTime;
    private Integer datastate;
    private LocalDateTime sourceCreatedAt;
    private String sourceCreatedBy;
    private LocalDateTime sourceUpdatedAt;
    private String sourceUpdatedBy;
    private String customInfo;
}
