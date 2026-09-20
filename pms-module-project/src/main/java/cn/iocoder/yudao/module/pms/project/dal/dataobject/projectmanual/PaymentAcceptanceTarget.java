package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PaymentAcceptanceTarget {
    private Long id;
    private Long projectId;
    private Long bindingId;
    private Integer version;
    private LocalDateTime acceptanceTime;
    private String sourceOwner;
    private String sourceKey;
}
