package cn.iocoder.yudao.module.pms.asset.api.location.dto;

import java.math.BigDecimal;

public record AddressInput(
        Long id,
        Integer expectedVersion,
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
        String normalizedAddress,
        String addressFingerprint) {
}
