package cn.iocoder.yudao.module.pms.asset.api.producttype.dto;

import java.util.List;
import java.util.Objects;

public record ProductTypeCodesQuery(
        List<String> productTypeCodes) {

    public ProductTypeCodesQuery {
        productTypeCodes = productTypeCodes == null ? List.of() : productTypeCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .toList();
    }
}
