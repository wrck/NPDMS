package cn.iocoder.yudao.module.pms.asset.api.location.dto;

import java.time.LocalDateTime;

public record AreaDepartmentMappingRespDTO(
        Long id,
        String areaCode,
        String areaLevel,
        String mappingType,
        String departmentCode,
        String departmentName,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Integer version) {
}
