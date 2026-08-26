package cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo;

import cn.iocoder.yudao.module.pms.asset.api.customer.CustomerDeviceSummarySlice;
import cn.iocoder.yudao.module.pms.project.api.customer.CustomerProjectSummarySlice;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerDetailRespVO extends CustomerRespVO {

    private List<Location> locations;
    private CustomerProjectSummarySlice projects;
    private CustomerDeviceSummarySlice devices;
    private List<History> history;

    @Data
    public static class Location {
        private String locationType;
        private Long locationId;
        private Integer sourceVersion;
        private LocalDateTime effectiveFrom;
    }

    @Data
    public static class History {
        private String fieldName;
        private String fieldOwner;
        private String beforeValueDigest;
        private String afterValueDigest;
        private String sourceType;
        private String operationId;
        private Long operatorId;
        private LocalDateTime occurredAt;
    }
}
