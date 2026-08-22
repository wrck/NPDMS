package cn.iocoder.yudao.module.pms.asset.api.location.dto;

import java.math.BigDecimal;

public record AddressRespDTO(
        Long id,
        String countryCode,
        String countryName,
        String provinceCode,
        String provinceName,
        String cityCode,
        String cityName,
        String districtCode,
        String districtName,
        String detailAddress,
        String fullAddress,
        BigDecimal longitude,
        BigDecimal latitude,
        Integer status,
        Integer version) {
}
