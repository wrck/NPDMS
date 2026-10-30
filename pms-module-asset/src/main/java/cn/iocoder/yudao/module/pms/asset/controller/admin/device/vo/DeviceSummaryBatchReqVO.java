package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.util.Set;

@Data
public class DeviceSummaryBatchReqVO {
    @NotNull
    private Set<@NotNull @Positive Long> deviceIds;
    @Positive
    private Long projectId;
}
