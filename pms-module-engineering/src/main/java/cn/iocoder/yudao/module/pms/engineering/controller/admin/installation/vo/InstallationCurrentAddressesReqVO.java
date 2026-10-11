package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.util.List;
@Data
public class InstallationCurrentAddressesReqVO {
    @NotNull @Positive private Long projectId;
    @NotEmpty @Size(max = 200) private List<@NotNull @Positive Long> equipmentIds;
}
