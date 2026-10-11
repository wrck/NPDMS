package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo;

import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.time.LocalDateTime;

/** 批量安装共用地点及安装信息；设备集合在服务端整体校验。 */
@Data
public class InstallationBatchCreateReqVO {
    @NotNull private Long projectId;
    @NotEmpty private List<@NotNull Long> equipmentIds;
    private String installLocation;
    private LocationMaintenanceCommand locationMaintenance;
    private LocalDateTime installTime;
    private Long installerUserId;
    private String environmentCheck;
    private String specCheck;
    private String photoUrl;
    private String result;
    private String remark;
}
